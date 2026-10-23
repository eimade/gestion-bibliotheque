/** Interfaces miroir des DTO d'ARCH-pilotage §3. */

export interface Dashboard {
  readonly date: string;
  readonly loansToday: number;
  readonly returnsToday: number;
  readonly overdue: number;
  readonly readyReservations: number;
  readonly readyLastDay: number;
  readonly toShelve: number;
  readonly computedAt: string;
}

export interface TopBook {
  readonly bookId: number;
  readonly title: string;
  readonly authors: string;
  readonly loans: number;
}

export interface Rotation {
  readonly category: string;
  readonly loans: number;
  readonly copies: number;
  readonly rate: number;
}

export interface MonthlyStatistics {
  readonly month: string;
  readonly loans: number;
  readonly returns: number;
  readonly renewals: number;
  readonly reservations: number;
  readonly newMembers: number;
  readonly activeBorrowers: number;
  readonly topBooks: readonly TopBook[];
  readonly rotation: readonly Rotation[];
  readonly collections: { readonly cash: number; readonly card: number; readonly check: number; readonly waived: number };
}

export type SettingGroup = 'MEMBERSHIP' | 'LOANS' | 'RESERVATIONS' | 'FINES';

export interface SettingView {
  readonly code: string;
  readonly group: SettingGroup;
  readonly valueType: 'INTEGER' | 'MONEY';
  readonly unit: 'MONTHS' | 'DAYS' | 'COUNT' | 'EUR';
  readonly value: number;
  readonly min: number;
  readonly max: number;
  readonly updatedAt?: string | null;
  readonly updatedBy?: string | null;
  readonly version: number;
}

export type AuditAction = 'CREATE' | 'UPDATE' | 'STATE_CHANGE' | 'DELETE' | 'LOGIN_SUCCESS' | 'LOGIN_FAILURE' | 'LOGOUT';

export interface AuditEntry {
  readonly id: number;
  readonly occurredAt: string;
  readonly actorLabel: string;
  readonly action: AuditAction;
  readonly objectType: string;
  readonly objectRef: string;
  readonly details?: Record<string, unknown> | null;
}

export interface AuditQuery {
  readonly object: string;
  readonly actor: string;
  readonly action: AuditAction | null;
  readonly from: string;
  readonly to: string;
  readonly page: number;
  readonly size: number;
}

/** Libellés des paramètres (UX-pilotage ECR-39). */
export const SETTING_LABELS: Readonly<Record<string, string>> = {
  MEMBERSHIP_DURATION_MONTHS: 'Durée de l’adhésion',
  MEMBERSHIP_EARLY_RENEWAL_DAYS: 'Renouvellement anticipé',
  LOAN_DURATION_DAYS: 'Durée d’emprunt',
  LOAN_MAX_ACTIVE: 'Emprunts simultanés max',
  LOAN_MAX_RENEWALS: 'Prolongations max',
  LOAN_RENEWAL_DAYS: 'Durée d’une prolongation',
  RESERVATION_MAX_ACTIVE: 'Réservations actives max',
  RESERVATION_PICKUP_DAYS: 'Délai de retrait',
  FINE_BLOCKING_THRESHOLD: 'Seuil de blocage',
  FINE_DAILY_RATE: 'Tarif journalier de retard',
  FINE_LATE_CAP: 'Plafond par emprunt',
  FINE_LOST_ITEM_AMOUNT: 'Forfait de perte',
};

export const SETTING_GROUP_LABELS: Readonly<Record<SettingGroup, string>> = {
  MEMBERSHIP: 'Adhésion',
  LOANS: 'Emprunts',
  RESERVATIONS: 'Réservations',
  FINES: 'Pénalités',
};

export const UNIT_LABELS: Readonly<Record<SettingView['unit'], string>> = {
  MONTHS: 'mois',
  DAYS: 'jours',
  COUNT: '',
  EUR: '€',
};

export const AUDIT_ACTION_LABELS: Readonly<Record<AuditAction, string>> = {
  CREATE: 'Création',
  UPDATE: 'Modification',
  STATE_CHANGE: 'Changement d’état',
  DELETE: 'Suppression',
  LOGIN_SUCCESS: 'Connexion',
  LOGIN_FAILURE: 'Échec de connexion',
  LOGOUT: 'Déconnexion',
};

export const AUDIT_OBJECT_LABELS: Readonly<Record<string, string>> = {
  ACCOUNT: 'Compte',
  MEMBER: 'Adhérent',
  BOOK: 'Notice',
  AUTHOR: 'Auteur',
  CATEGORY: 'Catégorie',
  COPY: 'Exemplaire',
  LOAN: 'Emprunt',
  RESERVATION: 'Réservation',
  FINE: 'Pénalité',
  PAYMENT: 'Paiement',
  SETTING: 'Paramètre',
};
