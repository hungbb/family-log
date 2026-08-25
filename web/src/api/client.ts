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
 * TODO(Phase 1): `Authorization: Bearer` and the single-flight refresh described in doc 07 §4.2.
 * There is no session to attach yet, so `skipAuth` is accepted and currently has nothing to skip.
 */
import { ApiError, toApiError } from './errors';

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
  /** TODO(Phase 1): omit the bearer token. Set on the refresh call to stop it recursing. */
  skipAuth?: boolean;
  /**
   * Overrides the {@link API_BASE_PATH} prefix.
   *
   * Only one thing needs this: the actuator health endpoint, which is served at the server root
   * rather than under the versioned API. Business endpoints must never set it.
   */
  basePath?: string;
}

/** Where the API lives. Configured per environment; see `web/.env.example`. */
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
 * Performs one request and returns its parsed body.
 *
 * @throws {ApiError} on any non-2xx response, and on a transport failure.
 */
export async function request<T>(path: string, options?: RequestOptions): Promise<T> {
  const url = buildUrl(path, options);
  const method = options?.method ?? 'GET';
  const body = options?.body;

  const headers: Record<string, string> = {
    Accept: 'application/json',
    ...options?.headers,
  };

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
