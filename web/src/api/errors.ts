/**
 * The client side of the error contract in `.docs/05-api-specification.md` §2.
 *
 * The backend answers every failure with RFC 7807 Problem Details plus three extensions — `code`,
 * `correlationId` and `errors` — and `GlobalExceptionHandlerTest` on the API side pins that shape.
 * Everything here is written against it.
 */

/**
 * The `code` values the API can return. Kept in step with `ErrorCode` on the backend.
 *
 * `ApiError.code` is typed as a plain `string`, not this union: a deployed client will meet codes
 * added after it shipped, and pretending otherwise would make the exhaustiveness a lie. Use
 * {@link hasCode} to compare against a known code with autocomplete and a spell-check.
 */
export type ApiErrorCode =
  | 'MALFORMED_REQUEST'
  | 'INVALID_CLAN_CODE'
  | 'AUTH_REQUIRED'
  | 'TOKEN_EXPIRED'
  | 'TOKEN_INVALID'
  | 'PERMISSION_DENIED'
  | 'OUT_OF_SCOPE'
  | 'PERMISSION_ESCALATION_BLOCKED'
  | 'LAST_ADMIN_PROTECTED'
  | 'CLAN_NOT_FOUND'
  | 'PERSON_NOT_FOUND'
  | 'BRANCH_NOT_FOUND'
  | 'REQUEST_NOT_FOUND'
  | 'RESOURCE_NOT_FOUND'
  | 'VERSION_CONFLICT'
  | 'EMAIL_ALREADY_REGISTERED'
  | 'DUPLICATE_PENDING_REQUEST'
  | 'NODE_ALREADY_LINKED'
  | 'USER_ALREADY_LINKED_IN_CLAN'
  | 'MEMBERSHIP_EXISTS'
  | 'GRANT_ALREADY_EXISTS'
  | 'VALIDATION_FAILED'
  | 'CYCLE_DETECTED'
  | 'INVALID_RELATIONSHIP'
  | 'DATE_RANGE_INVALID'
  | 'SUBTREE_SCOPE_UNAVAILABLE'
  | 'RATE_LIMITED'
  | 'INTERNAL_ERROR'
  /** Not from the server: the request never completed (offline, DNS, CORS, abort). */
  | 'NETWORK_ERROR';

/** One field-level rejection from the `errors` extension. */
export interface FieldViolation {
  field: string;
  message: string;
}

/** The wire shape. Every member is optional because a proxy or a gateway may answer instead. */
export interface ProblemDetails {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  code?: string;
  correlationId?: string;
  errors?: FieldViolation[];
}

/**
 * Every failure the client raises, successful HTTP or not.
 *
 * One error type rather than several is deliberate: a caller should not have to distinguish "the
 * server said no" from "the request never arrived" before it can render a message. `code` tells it
 * apart when it matters.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly detail: string | undefined;
  readonly errors: FieldViolation[];
  readonly correlationId: string | undefined;
  readonly problem: ProblemDetails | undefined;

  constructor(init: {
    status: number;
    code: string;
    message: string;
    detail?: string | undefined;
    errors?: FieldViolation[] | undefined;
    correlationId?: string | undefined;
    problem?: ProblemDetails | undefined;
    cause?: unknown;
  }) {
    super(init.message, init.cause === undefined ? undefined : { cause: init.cause });
    this.name = 'ApiError';
    this.status = init.status;
    this.code = init.code;
    this.detail = init.detail;
    this.errors = init.errors ?? [];
    this.correlationId = init.correlationId;
    this.problem = init.problem;
  }

  /** Whether retrying the exact same request could plausibly succeed. */
  get isRetryable(): boolean {
    return this.code === 'NETWORK_ERROR' || this.status === 429 || this.status >= 500;
  }

  /** Field violations keyed by field, for driving an Ant Design form's error state. */
  get fieldErrors(): Record<string, string[]> {
    return this.errors.reduce<Record<string, string[]>>((accumulator, violation) => {
      const existing = accumulator[violation.field] ?? [];
      existing.push(violation.message);
      accumulator[violation.field] = existing;
      return accumulator;
    }, {});
  }
}

/** Narrows an unknown caught value — `catch` gives `unknown`, and rightly so. */
export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

/** Whether an error is a specific failure, e.g. `hasCode(error, 'VERSION_CONFLICT')`. */
export function hasCode(error: unknown, ...codes: ApiErrorCode[]): boolean {
  return isApiError(error) && (codes as string[]).includes(error.code);
}

/**
 * Builds an {@link ApiError} from a response body that may or may not be Problem Details.
 *
 * A 502 from a load balancer arrives as HTML; a dropped connection arrives as nothing at all. The
 * fallbacks below are what keep those from surfacing as "undefined" in the UI.
 */
export function toApiError(
  status: number,
  body: unknown,
  fallbackMessage: string,
  correlationIdHeader?: string | null,
): ApiError {
  const problem = isProblemDetails(body) ? body : undefined;
  const code = problem?.code ?? defaultCodeForStatus(status);
  const detail = problem?.detail;
  const correlationId = problem?.correlationId ?? correlationIdHeader ?? undefined;

  return new ApiError({
    status,
    code,
    message: detail ?? problem?.title ?? fallbackMessage,
    detail,
    errors: problem?.errors,
    correlationId,
    problem,
  });
}

function isProblemDetails(body: unknown): body is ProblemDetails {
  return typeof body === 'object' && body !== null && !Array.isArray(body);
}

function defaultCodeForStatus(status: number): ApiErrorCode {
  if (status === 401) return 'AUTH_REQUIRED';
  if (status === 403) return 'PERMISSION_DENIED';
  if (status === 404) return 'RESOURCE_NOT_FOUND';
  if (status === 409) return 'VERSION_CONFLICT';
  if (status === 422) return 'VALIDATION_FAILED';
  if (status === 429) return 'RATE_LIMITED';
  if (status >= 500) return 'INTERNAL_ERROR';
  return 'MALFORMED_REQUEST';
}
