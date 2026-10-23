/** Interfaces miroir des schémas du catalogue (ARCH-catalogue §3, openapi.yaml). */
import { CopyStatus, PhysicalCondition, WithdrawalReason } from '../../../shared/models/statuses';

export interface AuthorView {
  readonly id: number;
  readonly lastName: string;
  readonly firstName?: string | null;
}

export interface CategoryOption {
  readonly id: number;
  readonly label: string;
}

export interface LanguageOption {
  readonly code: string;
  readonly label: string;
}

export interface BookSearchItem {
  readonly id: number;
  readonly title: string;
  readonly subtitle?: string | null;
  readonly authors: readonly AuthorView[];
  readonly publicationYear?: number | null;
  readonly categories: readonly CategoryOption[];
  readonly availableCount: number;
  readonly totalCount: number;
}

export interface PublicCopy {
  readonly shelfMark: string;
  readonly status: CopyStatus;
  readonly dueDate?: string | null;
}

interface BookDetailBase {
  readonly id: number;
  readonly isbn?: string | null;
  readonly title: string;
  readonly subtitle?: string | null;
  readonly authors: readonly AuthorView[];
  readonly categories: readonly CategoryOption[];
  readonly publisher?: string | null;
  readonly publicationYear?: number | null;
  readonly language: LanguageOption;
  readonly pageCount?: number | null;
  readonly summary?: string | null;
  readonly availableCount: number;
  readonly totalCount: number;
}

export interface BookPublicDetail extends BookDetailBase {
  readonly copies: readonly PublicCopy[];
}

export interface CopyView {
  readonly id: number;
  readonly copyNumber: string;
  readonly shelfMark: string;
  readonly physicalCondition: PhysicalCondition;
  readonly acquisitionDate: string;
  readonly status: CopyStatus;
  readonly withdrawalReason?: WithdrawalReason | null;
  readonly dueDate?: string | null;
  readonly version: number;
}

export interface BookAdminDetail extends BookDetailBase {
  readonly copies: readonly CopyView[];
  readonly version: number;
}

export interface BookAdminItem {
  readonly id: number;
  readonly title: string;
  readonly authors: readonly AuthorView[];
  readonly isbn?: string | null;
  readonly publicationYear?: number | null;
  readonly availableCount: number;
  readonly totalCount: number;
  readonly copyCount: number;
  readonly withdrawn: boolean;
}

export interface BookRequest {
  readonly isbn: string | null;
  readonly title: string;
  readonly subtitle: string | null;
  readonly authorIds: readonly number[];
  readonly categoryIds: readonly number[];
  readonly publisher: string | null;
  readonly publicationYear: number | null;
  readonly languageCode: string;
  readonly pageCount: number | null;
  readonly summary: string | null;
  readonly version: number | null;
}

export interface AddCopiesRequest {
  readonly quantity: number;
  readonly shelfMark: string;
  readonly physicalCondition: PhysicalCondition;
  readonly acquisitionDate: string;
}

export interface CategoryResponse {
  readonly id: number;
  readonly label: string;
  readonly bookCount: number;
  readonly version: number;
}

export type PublicSort = 'relevance' | 'title,asc' | 'publicationYear,desc';

export interface PublicSearchQuery {
  readonly q: string;
  readonly categoryId: number | null;
  readonly language: string | null;
  readonly available: boolean;
  readonly sort: PublicSort;
  readonly page: number;
  readonly size: number;
}

export interface AdminSearchQuery {
  readonly q: string;
  readonly categoryId: number | null;
  readonly includeWithdrawn: boolean;
  readonly sort: 'title,asc' | 'title,desc' | 'publicationYear,asc' | 'publicationYear,desc';
  readonly page: number;
  readonly size: number;
}

/** « Prénom Nom » ; un auteur sans prénom garde son seul nom. */
export function authorName(author: AuthorView): string {
  return author.firstName ? `${author.firstName} ${author.lastName}` : author.lastName;
}

export function authorNames(authors: readonly AuthorView[]): string {
  return authors.map(authorName).join(', ');
}
