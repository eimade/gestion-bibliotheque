/** Interfaces miroir des DTO d'ARCH-circulation §3 (réservations). */
import { MemberStatus, ReservationStatus } from '../../../shared/models/statuses';
import { EligibilityReason } from '../../adherents/data-access/member.models';
import { CopyRelease, Destination, Hold, MemberBrief, MemberContactBrief } from '../../emprunts/data-access/loan.models';

export type ReservationRefusal =
  | 'COPY_AVAILABLE_ON_SHELF'
  | 'NO_COPY_IN_CIRCULATION'
  | 'RESERVATION_ALREADY_EXISTS'
  | 'BOOK_ALREADY_BORROWED'
  | 'RESERVATION_LIMIT_REACHED'
  | 'MEMBER_NOT_ELIGIBLE';

export interface ReservationPreview {
  readonly reservable: boolean;
  readonly reasons: readonly ReservationRefusal[];
  readonly memberReasons: readonly EligibilityReason[];
  readonly position: number;
  readonly activeReservations: number;
  readonly limit: number;
  readonly pickupDays: number;
  readonly member?: (MemberBrief & { readonly status: MemberStatus }) | null;
}

export interface CurrentReservation {
  readonly id: number;
  readonly status: ReservationStatus;
  readonly position?: number | null;
  readonly queueLength: number;
  readonly pickupDeadline?: string | null;
}

export interface MyBookReservation {
  readonly current?: CurrentReservation | null;
  readonly preview: ReservationPreview;
}

export interface ReservationCreated {
  readonly id: number;
  readonly status: ReservationStatus;
  readonly position: number;
  readonly queueLength: number;
}

export interface ReservationCancelled {
  readonly id: number;
  readonly status: ReservationStatus;
  readonly release?: CopyRelease | null;
}

export interface MemberReservation {
  readonly id: number;
  readonly bookId: number;
  readonly title: string;
  readonly status: ReservationStatus;
  readonly position?: number | null;
  readonly queueLength: number;
  readonly reservedAt: string;
  readonly pickupDeadline?: string | null;
  readonly copyNumber?: string | null;
}

export interface QueueEntry {
  readonly id: number;
  readonly status: ReservationStatus;
  readonly position?: number | null;
  readonly member?: MemberBrief | null;
  readonly reservedAt: string;
  readonly pickupDeadline?: string | null;
  readonly copyNumber?: string | null;
}

export interface ReadyReservation {
  readonly id: number;
  readonly title: string;
  readonly copyNumber: string;
  readonly shelfMark: string;
  readonly member?: MemberContactBrief | null;
  readonly pickupDeadline: string;
  readonly lastDay: boolean;
  readonly notificationFailed: boolean;
}

export interface ReleasedCopy {
  readonly reservationId: number;
  readonly title: string;
  readonly copyNumber: string;
  readonly shelfMark: string;
  readonly previousMember?: MemberBrief | null;
  readonly closedOn: string;
  readonly closedStatus: ReservationStatus;
  readonly destination: Destination;
  readonly hold?: Hold | null;
}
