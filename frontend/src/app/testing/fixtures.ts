import { Eligibility, MemberDetail } from '../features/adherents/data-access/member.models';
import { SessionUser, StaffAccount } from '../features/authentification/data-access/account.models';
import { MemberLoan } from '../features/emprunts/data-access/loan.models';

/** Données de test partagées par les specs. */
export const ADMIN: SessionUser = {
  accountId: 1,
  email: 'admin@exemple.fr',
  role: 'ADMIN',
  lastName: 'LEROY',
  firstName: 'Anne',
  passwordTemporary: false,
  memberNumber: null,
};

export const LIBRARIAN: SessionUser = { ...ADMIN, accountId: 2, email: 'marie@exemple.fr', role: 'LIBRARIAN' };

export const MEMBER: SessionUser = {
  ...ADMIN,
  accountId: 9,
  email: 'jean.martin@exemple.fr',
  role: 'MEMBER',
  lastName: 'MARTIN',
  firstName: 'Jean',
  memberNumber: 'A000123',
};

export function staffAccount(overrides: Partial<StaffAccount> = {}): StaffAccount {
  return {
    id: 5,
    lastName: 'Dupont',
    firstName: 'Marie',
    email: 'marie.dupont@exemple.fr',
    role: 'LIBRARIAN',
    status: 'ACTIVE',
    me: false,
    version: 0,
    ...overrides,
  };
}

export function problem(status: number, code: string, extra: Record<string, unknown> = {}) {
  return { status, statusText: 'Erreur', body: { status, code, detail: `détail ${code}`, ...extra } };
}

export function eligibility(overrides: Partial<Eligibility> = {}): Eligibility {
  return { eligible: true, reasons: [], activeLoans: 2, loanLimit: 5, remainingLoans: 3, unpaidTotal: 0, fineThreshold: 5, ...overrides };
}

export function memberDetail(overrides: Partial<MemberDetail> = {}): MemberDetail {
  return {
    memberNumber: 'A000123',
    lastName: 'Martin',
    firstName: 'Jean',
    birthDate: '1990-03-12',
    email: 'jean.martin@exemple.fr',
    phone: '0612345678',
    street: '12 rue des Lilas',
    postalCode: '75011',
    city: 'Paris',
    registrationDate: '2025-10-17',
    expiryDate: '2027-10-17',
    status: 'ACTIVE',
    accountId: 9,
    accountStatus: 'ACTIVE',
    version: 0,
    eligibility: eligibility(),
    renewal: { allowed: false, newExpiryDate: '2028-10-17', renewableFrom: '2027-09-17', blockedBy: 'RENEWAL_TOO_EARLY' },
    expiresSoon: false,
    activeReservations: 0,
    ...overrides,
  };
}

export function memberLoan(overrides: Partial<MemberLoan> = {}): MemberLoan {
  return {
    id: 1,
    copyNumber: '00001234',
    bookId: 3,
    title: 'L’Étranger',
    loanDate: '2026-10-10',
    dueDate: '2026-10-31',
    status: 'ACTIVE',
    renewalCount: 0,
    overdueDays: 0,
    provisionalFine: 0,
    renewal: { allowed: true, memberReasons: [], newDueDate: '2026-11-21' },
    ...overrides,
  };
}
