import { Signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Observable, catchError, map, of, startWith, switchMap } from 'rxjs';

/** États d'une vue de données (UX-socle §5.7) ; `idle` = rien à charger (ex. recherche vide). */
export type ViewState<T> =
  | { readonly kind: 'idle' }
  | { readonly kind: 'loading' }
  | { readonly kind: 'error'; readonly error: unknown }
  | { readonly kind: 'success'; readonly data: T };

/**
 * Charge `load(params)` à chaque changement de `params` (requête précédente annulée) ; `null` → `idle`.
 * À appeler dans un contexte d'injection (initialiseur de champ d'un composant).
 */
export function viewState<P, T>(params: Signal<P | null>, load: (params: P) => Observable<T>): Signal<ViewState<T>> {
  return toSignal(
    toObservable(params).pipe(
      switchMap((value) =>
        value === null
          ? of<ViewState<T>>({ kind: 'idle' })
          : load(value).pipe(
              map((data): ViewState<T> => ({ kind: 'success', data })),
              catchError((error: unknown) => of<ViewState<T>>({ kind: 'error', error })),
              startWith<ViewState<T>>({ kind: 'loading' }),
            ),
      ),
    ),
    { initialValue: { kind: 'loading' } as ViewState<T> },
  );
}

export function dataOf<T>(state: ViewState<T>): T | null {
  return state.kind === 'success' ? state.data : null;
}
