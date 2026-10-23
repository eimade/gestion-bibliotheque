/** Interfaces miroir des DTO d'ARCH-adherents §3 (champs `null` omis par le serveur : optionnels ici). */
import { AccountStatus, MemberStatus } from '../../../shared/models/statuses';

export type EligibilityReason =
  | 'MEMBERSHIP_EXPIRED'
  | 'MEMBER_SUSPENDED'
  | 'MEMBER_ARCHIVED'
  | 'OVERDUE_LOAN'
  | 'UNPAID_FINES_ABOVE_THRESHOLD'
  | 'LOAN_LIMIT_REACHED';

export type ArchiveBlockingReason = 'ACTIVE_LOANS' | 'ACTIVE_RESERVATIONS' | 'UNPAID_FINES';

export interface Eligibility {
  readonly eligible: boolean;
  readonly reasons: readonly EligibilityReason[];
  readonly activeLoans: number;
  readonly loanLimit: number;
  readonly remainingLoans: number;
  readonly unpaidTotal: number;
  readonly fineThreshold: number;
}

export interface GuardianView {
  readonly lastName: string;
  readonly firstName: string;
  readonly phone: string;
}

export interface MemberSummary {
  readonly memberNumber: string;
  readonly lastName?: string | null;
  readonly firstName?: string | null;
  readonly birthDate?: string | null;
  readonly status: MemberStatus;
  readonly expiryDate: string;
}

export interface RenewalInfo {
  readonly allowed: boolean;
  readonly newExpiryDate?: string | null;
  readonly renewableFrom?: string | null;
  readonly blockedBy?: 'RENEWAL_TOO_EARLY' | 'MEMBER_SUSPENDED' | 'MEMBER_ARCHIVED' | null;
}

export interface MemberDetail {
  readonly memberNumber: string;
  readonly lastName?: string | null;
  readonly firstName?: string | null;
  readonly birthDate?: string | null;
  readonly email?: string | null;
  readonly phone?: string | null;
  readonly street?: string | null;
  readonly postalCode: string;
  readonly city?: string | null;
  readonly guardian?: GuardianView | null;
  readonly registrationDate: string;
  readonly expiryDate: string;
  readonly status: MemberStatus;
  readonly suspensionReason?: string | null;
  readonly accountId?: number | null;
  readonly accountStatus?: AccountStatus | null;
  readonly version: number;
  readonly eligibility: Eligibility;
  readonly renewal: RenewalInfo;
  readonly expiresSoon: boolean;
  readonly activeReservations: number;
}

export interface MemberRequest {
  readonly lastName: string;
  readonly firstName: string;
  readonly birthDate: string;
  readonly guardian: GuardianView | null;
  readonly email: string;
  readonly phone: string | null;
  readonly street: string;
  readonly postalCode: string;
  readonly city: string;
}

export interface MemberCreateRequest extends MemberRequest {
  readonly password: string;
  readonly confirmDuplicate: boolean;
}

export interface MemberUpdateRequest extends MemberRequest {
  readonly version: number;
}

export interface ExistingMember {
  readonly memberNumber: string;
  readonly lastName: string;
  readonly firstName: string;
  readonly status: MemberStatus;
}

export interface ArchiveCheck {
  readonly archivable: boolean;
  readonly reasons: readonly ArchiveBlockingReason[];
  readonly activeLoans: number;
  readonly activeReservations: number;
  readonly unpaidTotal: number;
}

export interface MemberToArchive extends ArchiveCheck {
  readonly memberNumber: string;
  readonly lastName: string;
  readonly firstName: string;
  readonly expiryDate: string;
}

export interface MyMember {
  readonly memberNumber: string;
  readonly lastName: string;
  readonly firstName: string;
  readonly birthDate: string;
  readonly email: string;
  readonly phone?: string | null;
  readonly street: string;
  readonly postalCode: string;
  readonly city: string;
  readonly status: MemberStatus;
  readonly expiryDate: string;
  readonly version: number;
}

export interface ContactRequest {
  readonly phone: string | null;
  readonly street: string;
  readonly postalCode: string;
  readonly city: string;
  readonly version: number;
}

export type MemberSort = 'lastName,asc' | 'memberNumber,asc' | 'expiryDate,asc';

export interface MemberSearchQuery {
  readonly q: string;
  readonly status: MemberStatus | null;
  readonly sort: MemberSort;
  readonly page: number;
  readonly size: number;
}

/** RG-13 : format exact d'un numéro d'adhérent. */
export const MEMBER_NUMBER_PATTERN = /^A\d{6}$/;

/** « MARTIN Jean » ; « Données effacées » pour un adhérent archivé. */
export function memberName(member: { readonly lastName?: string | null; readonly firstName?: string | null }): string {
  return member.lastName ? `${member.lastName.toUpperCase()} ${member.firstName ?? ''}`.trim() : 'Données effacées';
}
