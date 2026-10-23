/** Interfaces miroir des DTO de l'API d'authentification (ARCH-authentification §3). */
import { AccountStatus, Role } from '../../../shared/models/statuses';

export interface SessionUser {
  readonly accountId: number;
  readonly email: string;
  readonly role: Role;
  readonly lastName: string;
  readonly firstName: string;
  readonly passwordTemporary: boolean;
  readonly memberNumber?: string | null;
}

export interface LoginRequest {
  readonly email: string;
  readonly password: string;
}

export interface ChangePasswordRequest {
  readonly currentPassword: string;
  readonly newPassword: string;
}

export interface StaffAccount {
  readonly id: number;
  readonly lastName: string;
  readonly firstName: string;
  readonly email: string;
  readonly role: Role;
  readonly status: AccountStatus;
  readonly me: boolean;
  readonly version: number;
}

export type StaffRole = Exclude<Role, 'MEMBER'>;

export interface CreateStaffAccountRequest {
  readonly lastName: string;
  readonly firstName: string;
  readonly email: string;
  readonly role: StaffRole;
  readonly initialPassword: string;
}

export type StaffAccountSortField = 'lastName' | 'role' | 'status';
export type SortDirection = 'asc' | 'desc';

export interface StaffAccountQuery {
  readonly q: string;
  readonly role: StaffRole | null;
  readonly status: AccountStatus | null;
  readonly page: number;
  readonly size: number;
  readonly sortField: StaffAccountSortField;
  readonly sortDirection: SortDirection;
}
