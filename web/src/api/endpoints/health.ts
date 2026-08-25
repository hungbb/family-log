import { api } from '../client';

/**
 * Spring Boot Actuator's health endpoint.
 *
 * The only endpoint served outside `/api/v1` — Actuator owns its own path — so it is the one place
 * that passes `basePath`. Everything added in later phases is a plain versioned endpoint.
 */

export type HealthStatus = 'UP' | 'DOWN' | 'OUT_OF_SERVICE' | 'UNKNOWN';

export interface HealthComponent {
  status: HealthStatus;
  details?: Record<string, unknown>;
}

export interface HealthResponse {
  status: HealthStatus;
  /** Present only when the profile is configured to show details — `dev` does, `prod` does not. */
  components?: Record<string, HealthComponent>;
}

export function getHealth(signal?: AbortSignal): Promise<HealthResponse> {
  return api.get<HealthResponse>('/health', {
    basePath: '/actuator',
    ...(signal ? { signal } : {}),
  });
}
