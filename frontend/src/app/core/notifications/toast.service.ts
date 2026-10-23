import { Injectable, signal } from '@angular/core';

export type ToastKind = 'success' | 'warning' | 'error';

export interface Toast {
  readonly id: number;
  readonly kind: ToastKind;
  readonly message: string;
}

/** Durée d'affichage d'un message de succès ; avertissements et erreurs se ferment à la main (UX-socle §5.3). */
export const TOAST_DURATION_MS = 6000;

@Injectable({ providedIn: 'root' })
export class ToastService {
  private nextId = 1;
  private readonly items = signal<readonly Toast[]>([]);
  readonly toasts = this.items.asReadonly();

  success(message: string): void {
    const id = this.push('success', message);
    setTimeout(() => this.dismiss(id), TOAST_DURATION_MS);
  }

  warning(message: string): void {
    this.push('warning', message);
  }

  error(message: string): void {
    this.push('error', message);
  }

  dismiss(id: number): void {
    this.items.update((toasts) => toasts.filter((toast) => toast.id !== id));
  }

  private push(kind: ToastKind, message: string): number {
    const id = this.nextId++;
    this.items.update((toasts) => [...toasts, { id, kind, message }]);
    return id;
  }
}
