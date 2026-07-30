import { inject } from '@angular/core';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { ToastService } from '../services/toast.service';

const STATUS_MESSAGES: Record<number, string> = {
  400: 'Invalid request. Please check your input and try again.',
  404: 'The requested resource was not found.',
  409: 'This action conflicts with the current state. Please refresh and try again.',
  422: 'The submitted data is invalid.',
  500: 'Something went wrong on our end. Please try again later.',
  502: 'Service temporarily unavailable. Please try again shortly.',
  503: 'Service temporarily unavailable. Please try again shortly.',
  504: 'The request timed out. Please try again.',
};

const DEFAULT_MESSAGE = 'An unexpected error occurred. Please try again.';

// 401/403 are excluded here; MFE-level auth-failure handling is a tracked follow-up — shell session handling only covers the shell's own client.
const SKIPPED_STATUSES = [401, 403];

function extractMessage(error: HttpErrorResponse): string {
  const body: unknown = error.error;

  if (typeof body === 'string' && body.trim()) {
    return body;
  }
  if (body && typeof body === 'object') {
    const { message, error: bodyError } = body as { message?: unknown; error?: unknown };
    if (typeof message === 'string' && message.trim()) {
      return message;
    }
    if (typeof bodyError === 'string' && bodyError.trim()) {
      return bodyError;
    }
  }
  return STATUS_MESSAGES[error.status] ?? DEFAULT_MESSAGE;
}

export const httpErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(ToastService);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && !SKIPPED_STATUSES.includes(error.status)) {
        toast.error(extractMessage(error));
      }
      return throwError(() => error);
    })
  );
};
