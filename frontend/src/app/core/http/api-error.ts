import { HttpErrorResponse } from '@angular/common/http';

import { GENERIC_MESSAGES } from './messages';

export interface FieldError {
  readonly field: string;
  readonly code: string;
  readonly message: string;
}

/** Corps d'erreur du serveur (ADR-003 §4). */
export interface ProblemDetail {
  readonly status?: number;
  readonly title?: string;
  readonly detail?: string;
  readonly code?: string;
  readonly reasons?: readonly string[];
  readonly fieldErrors?: readonly FieldError[];
  readonly [property: string]: unknown;
}

export const NETWORK_ERROR = 'NETWORK_ERROR';
export const UNKNOWN_ERROR = 'UNKNOWN_ERROR';

/** Erreur d'API normalisée : toute erreur HTTP est convertie en `ApiError` par l'intercepteur. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly detail: string,
    readonly problem: ProblemDetail,
  ) {
    super(detail);
    this.name = 'ApiError';
  }

  static fromResponse(response: HttpErrorResponse): ApiError {
    if (response.status === 0) {
      return new ApiError(0, NETWORK_ERROR, GENERIC_MESSAGES.network, {});
    }
    const problem: ProblemDetail = isProblemDetail(response.error) ? response.error : {};
    return new ApiError(
      response.status,
      typeof problem.code === 'string' ? problem.code : UNKNOWN_ERROR,
      typeof problem.detail === 'string' ? problem.detail : GENERIC_MESSAGES.unexpected,
      problem,
    );
  }

  /** Propriété contextuelle du ProblemDetail (ex. `lockedUntil`). */
  property(name: string): unknown {
    return this.problem[name];
  }
}

function isProblemDetail(value: unknown): value is ProblemDetail {
  return typeof value === 'object' && value !== null;
}
