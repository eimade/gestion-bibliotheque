/** Interfaces miroir des DTO d'ARCH-circulation §3 (pénalités). */
import { FineReason, FineStatus, PaymentMethod } from '../../../shared/models/statuses';
import { Page } from '../../../shared/models/page.model';

export interface FineView {
  readonly id: number;
  readonly reason: FineReason;
  readonly lateDays?: number | null;
  readonly title?: string | null;
  readonly copyNumber?: string | null;
  readonly createdOn: string;
  readonly amount: number;
  readonly version: number;
}

export interface MemberFines {
  readonly fines: readonly FineView[];
  readonly total: number;
}

export interface PaymentRequest {
  readonly memberNumber: string;
  readonly fineIds: readonly number[];
  readonly method: PaymentMethod;
  readonly amount: number;
}

export interface PaymentResult {
  readonly paymentId: number;
  readonly amount: number;
  readonly method: PaymentMethod;
  readonly paidFineIds: readonly number[];
  readonly unpaidTotal: number;
  readonly eligible: boolean;
}

export interface WaiverResult {
  readonly fineId: number;
  readonly status: FineStatus;
  readonly unpaidTotal: number;
  readonly eligible: boolean;
}

export interface Debtor {
  readonly memberNumber: string;
  readonly lastName?: string | null;
  readonly firstName?: string | null;
  readonly phone?: string | null;
  readonly email?: string | null;
  readonly fineCount: number;
  readonly total: number;
  readonly blocked: boolean;
}

export interface DebtorList {
  readonly memberCount: number;
  readonly totalAmount: number;
  readonly threshold: number;
  readonly page: Page<Debtor>;
}

export type DebtorSort = 'total' | 'count' | 'name';

/** Montant en centimes entiers, pour comparer sans arrondi (NFR-10). */
export function toCents(amount: number): number {
  return Math.round(amount * 100);
}
