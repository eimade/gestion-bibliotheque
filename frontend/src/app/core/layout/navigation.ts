import { Role } from '../../shared/models/statuses';
import { IconName } from '../../shared/ui/icon.component';

export interface NavItem {
  readonly label: string;
  readonly route: string;
  readonly roles: readonly Role[];
  readonly testId: string;
  /** Groupe « Administration » du menu latéral (UX-socle §2.2). */
  readonly admin?: boolean;
  /** Touche du raccourci `Alt+<touche>` (Q-20). */
  readonly shortcut?: string;
  /** Icône décorative de l'entrée. */
  readonly icon?: IconName;
}

/** Menu latéral du personnel (UX-socle §2.2), avec les raccourcis clavier du guichet (Q-20). */
export const STAFF_NAVIGATION: readonly NavItem[] = [
  { label: 'Tableau de bord', route: '/gestion', roles: ['LIBRARIAN', 'ADMIN'], testId: 'nav-dashboard', icon: 'dashboard' },
  { label: 'Guichet — Emprunt', route: '/gestion/guichet/emprunt', roles: ['LIBRARIAN', 'ADMIN'], testId: 'nav-loan-desk', shortcut: 'e', icon: 'arrow-up-right' },
  { label: 'Guichet — Retour', route: '/gestion/guichet/retour', roles: ['LIBRARIAN', 'ADMIN'], testId: 'nav-return-desk', shortcut: 'r', icon: 'arrow-down-left' },
  { label: 'Adhérents', route: '/gestion/adherents', roles: ['LIBRARIAN', 'ADMIN'], testId: 'nav-members', shortcut: 'a', icon: 'users' },
  { label: 'Catalogue', route: '/gestion/catalogue', roles: ['LIBRARIAN', 'ADMIN'], testId: 'nav-catalog', shortcut: 'c', icon: 'books' },
  { label: 'Retards', route: '/gestion/retards', roles: ['LIBRARIAN', 'ADMIN'], testId: 'nav-overdue', icon: 'clock' },
  { label: 'Réservations à traiter', route: '/gestion/reservations', roles: ['LIBRARIAN', 'ADMIN'], testId: 'nav-reservations', icon: 'bookmark' },
  { label: 'Pénalités impayées', route: '/gestion/penalites', roles: ['LIBRARIAN', 'ADMIN'], testId: 'nav-fines', icon: 'coins' },
  { label: 'Catégories', route: '/gestion/categories', roles: ['LIBRARIAN', 'ADMIN'], testId: 'nav-categories', icon: 'tag' },
  { label: 'Comptes du personnel', route: '/gestion/comptes', roles: ['ADMIN'], testId: 'nav-staff-accounts', admin: true, icon: 'shield' },
  { label: 'Statistiques', route: '/gestion/statistiques', roles: ['ADMIN'], testId: 'nav-statistics', admin: true, icon: 'chart' },
  { label: 'Paramètres', route: '/gestion/parametres', roles: ['ADMIN'], testId: 'nav-settings', admin: true, icon: 'settings' },
  { label: 'Journal d’audit', route: '/gestion/audit', roles: ['ADMIN'], testId: 'nav-audit', admin: true, icon: 'list' },
];

/** Onglets de l'espace adhérent (UX-socle §2.1), même règle que ci-dessus. */
export const MEMBER_NAVIGATION: readonly NavItem[] = [
  { label: 'Mon espace', route: '/mon-espace', roles: ['MEMBER'], testId: 'nav-member-space', icon: 'home' },
  { label: 'Catalogue', route: '/catalogue', roles: ['MEMBER'], testId: 'nav-member-catalog', icon: 'search' },
  { label: 'Mes coordonnées', route: '/mon-espace/coordonnees', roles: ['MEMBER'], testId: 'nav-member-contact', icon: 'id-card' },
];
