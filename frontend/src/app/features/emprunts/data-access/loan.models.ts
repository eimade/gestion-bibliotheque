/** Interfaces miroir des DTO d'ARCH-circulation §3 (emprunts). */
import { LoanStatus } from '../../../shared/models/statuses';
import { Eligibility, EligibilityReason } from '../../adherents/data-access/member.models';

export type RenewalBlock = 'RENEWAL_LIMIT_REACHED' | 'RENEWAL_BLOCKED_BY_RESERVATION' | 'LOAN_OVERDUE' | 'MEMBER_NOT_ELIGIBLE';

export interface LoanRenewal {
  readonly allowed: boolean;
  readonly blockedBy?: RenewalBlock | null;
  readonly memberReasons: readonly EligibilityReason[];
  readonly newDueDate: string;
}

export interface MemberLoan {
  readonly id: number;
  readonly copyNumber: string;
  readonly bookId: number;
  readonly title: string;
  readonly loanDate: string;
  readonly dueDate: string;
  readonly status: LoanStatus;
  readonly renewalCount: number;
  readonly overdueDays: number;
  readonly provisionalFine: number;
  readonly renewal: LoanRenewal;
}

export interface HistoryLoan {
  readonly id: number;
  readonly copyNumber: string;
  readonly title: string;
  readonly loanDate: string;
  readonly dueDate: string;
  readonly returnDate: string;
}

export interface MemberBrief {
  readonly memberNumber: string;
  readonly lastName?: string | null;
  readonly firstName?: string | null;
}

export interface MemberContactBrief extends MemberBrief {
  readonly phone?: string | null;
  readonly email?: string | null;
}

export interface Hold extends MemberBrief {
  readonly pickupDeadline: string;
}

export type Destination = 'SHELF' | 'HOLD';

export interface CopyRelease {
  readonly copyNumber: string;
  readonly shelfMark: string;
  readonly destination: Destination;
  readonly hold?: Hold | null;
}

export interface LoanResult {
  readonly loanId: number;
  readonly copyNumber: string;
  readonly title: string;
  readonly dueDate: string;
  readonly reservationFulfilled: boolean;
  readonly eligibility: Eligibility;
}

export interface ReturnResult {
  readonly copyNumber: string;
  readonly title: string;
  readonly shelfMark: string;
  readonly member?: MemberBrief | null;
  readonly lateDays: number;
  readonly fine?: { readonly id: number; readonly amount: number } | null;
  readonly destination: Destination;
  readonly hold?: Hold | null;
}

export interface OverdueLoan {
  readonly loanId: number;
  readonly copyNumber: string;
  readonly title: string;
  readonly member?: MemberContactBrief | null;
  readonly dueDate: string;
  readonly overdueDays: number;
  readonly provisionalFine: number;
}

export interface LossAmounts {
  readonly lateDays: number;
  readonly lateFine: number;
  readonly lostItemFine: number;
  readonly total: number;
}

export interface CopyLoanHistoryItem {
  readonly loanDate: string;
  readonly dueDate: string;
  readonly returnDate?: string | null;
  readonly status: LoanStatus;
  readonly borrower?: MemberBrief | null;
}

export type OverdueSort = 'overdueDays' | 'dueDate' | 'memberName';

/** RG-09 : 8 chiffres. */
export const COPY_NUMBER_PATTERN = /^\d{8}$/;
