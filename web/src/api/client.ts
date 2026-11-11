/**
 * The HTTP client. **The only place in the application that calls `fetch`.**
 *
 * There is no Axios and no `ky` here, and there never will be — the draft forbids both, ESLint
 * enforces it (`no-restricted-imports`), and `eslint-fixtures/` proves the enforcement works.
 * `fetch` covers everything the product needs; a wrapper is cheaper than a dependency.
 *
 * Components never call into this module. They call an endpoint function in `api/endpoints/*`,
 * usually through a TanStack Query hook. That indirection is what keeps request shapes typed in
 * one place instead of scattered across components.
 *
 * It also owns the session, because that is the only place it can be owned once: the bearer header
 * on every request, and the single-flight refresh of doc 07 §4.2 when one comes back expired.
 */
import { ApiError, hasCode, toApiError } from './errors';

/** Header the API echoes on every response; quoted in bug reports and sent back on retries. */
const CORRELATION_ID_HEADER = 'X-Correlation-Id';

/** The versioned API prefix from `.docs/05-api-specification.md` §1. */
export const API_BASE_PATH = '/api/v1';

export type QueryValue = string | number | boolean | undefined | null;

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  query?: Record<string, QueryValue | QueryValue[]>;
  signal?: AbortSignal;
  headers?: Record<string, string>;
  /**
   * Send no bearer token, and never attempt a refresh on a 401.
   *
   * Set on everything under `/auth` — those endpoints are how a session is obtained, so attaching a
   * token they do not want is pointless, and refreshing in response to their 401 is how you get an
   * infinite loop.
   */
  skipAuth?: boolean;
  /**
   * Overrides the {@link API_BASE_PATH} prefix.
   *
   * Only one thing needs this: the actuator health endpoint, which is served at the server root
   * rather than under the versioned API. Business endpoints must never set it.
   */
  basePath?: string;
}

/**
 * Where the API lives.
 *
 * Configured per environment. In development the default comes from the committed
 * `web/.env.development` (a fresh clone must reach a local API with no setup); `.env.local`
 * overrides it, as `web/.env.example` describes. Unset — a production build behind a reverse proxy
 * that serves both — means "same origin", which is why the fallback is the empty string rather than
 * a guess at a host.
 */
function apiOrigin(): string {
  const configured = import.meta.env.VITE_API_BASE_URL;
  return (configured ?? '').replace(/\/+$/, '');
}

function buildUrl(path: string, options: RequestOptions | undefined): string {
  const basePath = options?.basePath ?? API_BASE_PATH;
  const normalisedPath = path.startsWith('/') ? path : `/${path}`;
  const url = `${apiOrigin()}${basePath}${normalisedPath}`;
  const query = serializeQuery(options?.query);
  return query ? `${url}?${query}` : url;
}

/**
 * Serializes query parameters, dropping `undefined` and `null`.
 *
 * Dropping rather than sending them matters: `?branchId=undefined` is a string the server has to
 * reject, while an absent parameter means what the caller meant. Arrays repeat the key, which is
 * what Spring binds to a `List<T>`.
 */
function serializeQuery(query: RequestOptions['query']): string {
  if (!query) return '';
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    const values = Array.isArray(value) ? value : [value];
    for (const item of values) {
      if (item !== undefined && item !== null) {
        params.append(key, String(item));
      }
    }
  }
  return params.toString();
}

function isFormData(body: unknown): body is FormData {
  return typeof FormData !== 'undefined' && body instanceof FormData;
}

async function readBody(response: Response): Promise<unknown> {
  // 204, and 205, carry no body at all; asking for one throws in some browsers.
  if (response.status === 204 || response.status === 205) return undefined;

  const contentType = response.headers.get('Content-Type') ?? '';
  if (contentType.includes('json')) {
    try {
      return (await response.json()) as unknown;
    } catch {
      // A truncated or empty body with a JSON content type. Nothing to recover; fall through.
      return undefined;
    }
  }
  const text = await response.text();
  return text.length > 0 ? text : undefined;
}

/**
 * How the client reaches the session, without importing it.
 *
 * The session store needs the client (to call `/auth/refresh`) and the client needs the session
 * (for the bearer token). Injecting the second direction keeps the module graph acyclic — which
 * `import/no-cycle` enforces — and means this module can be reasoned about without knowing that
 * Zustand exists.
 */
export interface AuthBridge {
  /** The current access token, or null when nobody is signed in. */
  getAccessToken: () => string | null;
  /**
   * Exchanges the stored refresh token for a new pair.
   *
   * @returns the new access token, or null when the session could not be renewed
   */
  refresh: () => Promise<string | null>;
  /** Called once when a refresh fails: the session is over and the UI should say so. */
  onSessionEnded: () => void;
}

let authBridge: AuthBridge | null = null;

/** Wires the session into the client. Called once, at module load, by the session store. */
export function configureAuth(bridge: AuthBridge): void {
  authBridge = bridge;
}

/**
 * The in-flight refresh, if there is one.
 *
 * Doc 07 §4.2: when five queries fail together because the token expired, they must produce one
 * refresh call, not five. Five would rotate the refresh token five times, and four of those would
 * present an already-rotated token — which the server correctly treats as theft and answers by
 * revoking the whole chain. The single-flight promise is what stops the client from mugging itself.
 */
let refreshInFlight: Promise<string | null> | null = null;

function refreshOnce(bridge: AuthBridge): Promise<string | null> {
  refreshInFlight ??= bridge.refresh().finally(() => {
    refreshInFlight = null;
  });
  return refreshInFlight;
}

/**
 * Performs one request and returns its parsed body.
 *
 * Retries once, and only once, after renewing an expired access token.
 *
 * @throws {ApiError} on any non-2xx response, and on a transport failure.
 */
export async function request<T>(path: string, options?: RequestOptions): Promise<T> {
  try {
    return await send<T>(path, options);
  } catch (error) {
    const bridge = authBridge;
    if (!bridge || options?.skipAuth || !isExpiredSession(error)) {
      throw error;
    }

    const renewed = await refreshOnce(bridge);
    if (renewed === null) {
      bridge.onSessionEnded();
      throw error;
    }
    // One retry. If the fresh token is refused too, something is wrong that retrying cannot fix.
    return send<T>(path, options);
  }
}

/** Whether this failure is the one a refresh can fix. */
function isExpiredSession(error: unknown): boolean {
  return hasCode(error, 'TOKEN_EXPIRED', 'TOKEN_INVALID');
}

async function send<T>(path: string, options?: RequestOptions): Promise<T> {
  const url = buildUrl(path, options);
  const method = options?.method ?? 'GET';
  const body = options?.body;

  const headers: Record<string, string> = {
    Accept: 'application/json',
    ...options?.headers,
  };

  if (!options?.skipAuth) {
    const accessToken = authBridge?.getAccessToken() ?? null;
    if (accessToken !== null) {
      headers['Authorization'] = `Bearer ${accessToken}`;
    }
  }

  let payload: BodyInit | undefined;
  if (body !== undefined) {
    if (isFormData(body)) {
      // Deliberately no Content-Type: the browser must set it, because only it knows the
      // multipart boundary it generated.
      payload = body;
    } else {
      headers['Content-Type'] = 'application/json';
      payload = JSON.stringify(body);
    }
  }

  let response: Response;
  try {
    response = await fetch(url, {
      method,
      headers,
      ...(payload === undefined ? {} : { body: payload }),
      ...(options?.signal ? { signal: options.signal } : {}),
    });
  } catch (cause) {
    // An aborted request is not a failure — the caller (usually TanStack Query) navigated away or
    // superseded it. Rethrow untouched so it is not reported as an error.
    if (cause instanceof DOMException && cause.name === 'AbortError') {
      throw cause;
    }
    throw new ApiError({
      status: 0,
      code: 'NETWORK_ERROR',
      message: `Could not reach the API at ${url}.`,
      cause,
    });
  }

  const parsed = await readBody(response);

  if (!response.ok) {
    throw toApiError(
      response.status,
      parsed,
      `${method} ${path} failed with ${response.status}.`,
      response.headers.get(CORRELATION_ID_HEADER),
    );
  }

  return parsed as T;
}

type BodylessOptions = Omit<RequestOptions, 'method' | 'body'>;
type BodyOptions = Omit<RequestOptions, 'method' | 'body'>;

/** The verb-shaped surface everything else uses. */
export const api = {
  get: <T>(path: string, options?: BodylessOptions) =>
    request<T>(path, { ...options, method: 'GET' }),

  post: <T>(path: string, body?: unknown, options?: BodyOptions) =>
    request<T>(path, { ...options, method: 'POST', body }),

  put: <T>(path: string, body?: unknown, options?: BodyOptions) =>
    request<T>(path, { ...options, method: 'PUT', body }),

  patch: <T>(path: string, body?: unknown, options?: BodyOptions) =>
    request<T>(path, { ...options, method: 'PATCH', body }),

  del: <T>(path: string, options?: BodylessOptions) =>
    request<T>(path, { ...options, method: 'DELETE' }),

  upload: <T>(path: string, file: File, field = 'file', options?: BodylessOptions) => {
    const form = new FormData();
    form.append(field, file);
    return request<T>(path, { ...options, method: 'POST', body: form });
  },
} as const;
