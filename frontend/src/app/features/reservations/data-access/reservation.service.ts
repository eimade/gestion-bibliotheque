import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE } from '../../../core/http/api';
import { Page } from '../../../shared/models/page.model';
import {
  MemberReservation,
  MyBookReservation,
  QueueEntry,
  ReadyReservation,
  ReleasedCopy,
  ReservationCancelled,
  ReservationCreated,
  ReservationPreview,
} from './reservation.models';

/** Réservations (US-032 → US-035). */
@Injectable({ providedIn: 'root' })
export class ReservationService {
  private readonly http = inject(HttpClient);

  /** ECR-09 : situation de l'adhérent connecté sur une notice. */
  myBookReservation(bookId: number): Observable<MyBookReservation> {
    return this.http.get<MyBookReservation>(`${API_BASE}/me/books/${bookId}/reservation`);
  }

  reserveForMe(bookId: number): Observable<ReservationCreated> {
    return this.http.post<ReservationCreated>(`${API_BASE}/me/reservations`, { bookId });
  }

  previewForMember(bookId: number, memberNumber: string): Observable<ReservationPreview> {
    return this.http.get<ReservationPreview>(`${API_BASE}/reservations/preview`, {
      params: new HttpParams().set('bookId', bookId).set('memberNumber', memberNumber),
    });
  }

  reserveForMember(bookId: number, memberNumber: string): Observable<ReservationCreated> {
    return this.http.post<ReservationCreated>(`${API_BASE}/reservations`, { bookId, memberNumber });
  }

  /** Annulation par l'adhérent connecté (`asMember`) ou par le personnel. */
  cancel(reservationId: number, asMember: boolean): Observable<ReservationCancelled> {
    const url = asMember
      ? `${API_BASE}/me/reservations/${reservationId}/cancellation`
      : `${API_BASE}/reservations/${reservationId}/cancellation`;
    return this.http.post<ReservationCancelled>(url, {});
  }

  memberReservations(memberNumber: string | null): Observable<readonly MemberReservation[]> {
    return this.http.get<readonly MemberReservation[]>(
      memberNumber ? `${API_BASE}/members/${memberNumber}/reservations` : `${API_BASE}/me/reservations`);
  }

  queue(bookId: number): Observable<readonly QueueEntry[]> {
    return this.http.get<readonly QueueEntry[]>(`${API_BASE}/books/${bookId}/reservations`);
  }

  ready(page: number, size: number): Observable<Page<ReadyReservation>> {
    return this.http.get<Page<ReadyReservation>>(`${API_BASE}/reservations/ready`, {
      params: new HttpParams().set('page', page).set('size', size),
    });
  }

  released(page: number, size: number): Observable<Page<ReleasedCopy>> {
    return this.http.get<Page<ReleasedCopy>>(`${API_BASE}/reservations/released`, {
      params: new HttpParams().set('page', page).set('size', size),
    });
  }
}
