/** Énumérations partagées et leur affichage (UX-socle §5.2) : libellé français + ton sémantique. */

export type Role = 'MEMBER' | 'LIBRARIAN' | 'ADMIN';
export type AccountStatus = 'ACTIVE' | 'LOCKED' | 'DISABLED';

export type Tone = 'success' | 'warning' | 'danger' | 'info' | 'muted';

export interface StatusDisplay {
  readonly label: string;
  readonly tone: Tone;
}

export const ROLE_LABELS: Readonly<Record<Role, string>> = {
  MEMBER: 'Adhérent',
  LIBRARIAN: 'Bibliothécaire',
  ADMIN: 'Administrateur',
};

export const ACCOUNT_STATUS_DISPLAY: Readonly<Record<AccountStatus, StatusDisplay>> = {
  ACTIVE: { label: 'Actif', tone: 'success' },
  LOCKED: { label: 'Verrouillé', tone: 'warning' },
  DISABLED: { label: 'Désactivé', tone: 'muted' },
};

export type CopyStatus = 'AVAILABLE' | 'ON_LOAN' | 'RESERVED' | 'LOST' | 'WITHDRAWN';
export type PhysicalCondition = 'GOOD' | 'WORN' | 'DAMAGED';
export type WithdrawalReason = 'DAMAGED' | 'LOST' | 'WEEDED' | 'OTHER';

export const COPY_STATUS_DISPLAY: Readonly<Record<CopyStatus, StatusDisplay>> = {
  AVAILABLE: { label: 'Disponible', tone: 'success' },
  ON_LOAN: { label: 'Emprunté', tone: 'info' },
  RESERVED: { label: 'Mis de côté', tone: 'warning' },
  LOST: { label: 'Perdu', tone: 'danger' },
  WITHDRAWN: { label: 'Retiré', tone: 'muted' },
};

export const PHYSICAL_CONDITION_LABELS: Readonly<Record<PhysicalCondition, string>> = {
  GOOD: 'Bon',
  WORN: 'Usé',
  DAMAGED: 'Abîmé',
};

export const WITHDRAWAL_REASON_LABELS: Readonly<Record<WithdrawalReason, string>> = {
  DAMAGED: 'Abîmé',
  LOST: 'Perdu',
  WEEDED: 'Désherbage',
  OTHER: 'Autre',
};

export type MemberStatus = 'ACTIVE' | 'EXPIRED' | 'SUSPENDED' | 'ARCHIVED';
export type LoanStatus = 'ACTIVE' | 'OVERDUE' | 'RETURNED' | 'LOST';
export type ReservationStatus = 'PENDING' | 'READY' | 'FULFILLED' | 'EXPIRED' | 'CANCELLED';
export type FineReason = 'LATE_RETURN' | 'LOST_ITEM';
export type FineStatus = 'UNPAID' | 'PAID' | 'WAIVED';
export type PaymentMethod = 'CASH' | 'CARD' | 'CHECK';

export const MEMBER_STATUS_DISPLAY: Readonly<Record<MemberStatus, StatusDisplay>> = {
  ACTIVE: { label: 'Actif', tone: 'success' },
  EXPIRED: { label: 'Expiré', tone: 'warning' },
  SUSPENDED: { label: 'Suspendu', tone: 'danger' },
  ARCHIVED: { label: 'Archivé', tone: 'muted' },
};

export const LOAN_STATUS_DISPLAY: Readonly<Record<LoanStatus, StatusDisplay>> = {
  ACTIVE: { label: 'En cours', tone: 'info' },
  OVERDUE: { label: 'En retard', tone: 'danger' },
  RETURNED: { label: 'Rendu', tone: 'muted' },
  LOST: { label: 'Perdu', tone: 'danger' },
};

export const RESERVATION_STATUS_DISPLAY: Readonly<Record<ReservationStatus, StatusDisplay>> = {
  PENDING: { label: 'En attente', tone: 'info' },
  READY: { label: 'Prête', tone: 'success' },
  FULFILLED: { label: 'Retirée', tone: 'muted' },
  EXPIRED: { label: 'Expirée', tone: 'warning' },
  CANCELLED: { label: 'Annulée', tone: 'muted' },
};

export const FINE_STATUS_DISPLAY: Readonly<Record<FineStatus, StatusDisplay>> = {
  UNPAID: { label: 'Impayée', tone: 'danger' },
  PAID: { label: 'Payée', tone: 'success' },
  WAIVED: { label: 'Remise accordée', tone: 'muted' },
};

export const FINE_REASON_LABELS: Readonly<Record<FineReason, string>> = {
  LATE_RETURN: 'Retard',
  LOST_ITEM: 'Perte',
};

export const PAYMENT_METHOD_LABELS: Readonly<Record<PaymentMethod, string>> = {
  CASH: 'Espèces',
  CARD: 'Carte bancaire',
  CHECK: 'Chèque',
};
