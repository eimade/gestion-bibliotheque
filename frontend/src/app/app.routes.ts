import { Routes } from '@angular/router';

import { authGuard } from './core/auth/auth.guard';
import { guestGuard } from './core/auth/guest.guard';
import { passwordChangeGuard } from './core/auth/password-change.guard';
import { roleGuard } from './core/auth/role.guard';
import { ForbiddenPage, NotFoundPage } from './core/errors/error-pages';
import { AccountLayoutComponent } from './core/layout/account-layout.component';
import { MemberLayoutComponent } from './core/layout/member-layout.component';
import { PublicLayoutComponent } from './core/layout/public-layout.component';
import { StaffLayoutComponent } from './core/layout/staff-layout.component';

/**
 * Plan de navigation (UX-socle §2, UX-authentification §2). Le gabarit public est déclaré en
 * dernier : sa route `**` (ECR-01) ne doit pas masquer les autres.
 */
export const routes: Routes = [
  {
    path: 'compte',
    component: AccountLayoutComponent,
    canActivate: [authGuard],
    children: [
      {
        path: 'mot-de-passe',
        title: 'Changer mon mot de passe — Bibliothèque',
        loadComponent: () =>
          import('./features/authentification/pages/change-password.page').then((m) => m.ChangePasswordPage),
      },
    ],
  },
  {
    path: 'gestion',
    component: StaffLayoutComponent,
    canActivate: [authGuard, passwordChangeGuard, roleGuard],
    data: { roles: ['LIBRARIAN', 'ADMIN'] },
    children: [
      {
        path: '',
        pathMatch: 'full',
        title: 'Tableau de bord — Bibliothèque',
        loadComponent: () => import('./features/pilotage/pages/dashboard.page').then((m) => m.DashboardPage),
      },
      {
        path: 'guichet/emprunt',
        title: 'Guichet — Emprunt — Bibliothèque',
        loadComponent: () => import('./features/emprunts/pages/loan-desk.page').then((m) => m.LoanDeskPage),
      },
      {
        path: 'guichet/retour',
        title: 'Guichet — Retour — Bibliothèque',
        loadComponent: () => import('./features/emprunts/pages/return-desk.page').then((m) => m.ReturnDeskPage),
      },
      {
        path: 'retards',
        title: 'Emprunts en retard — Bibliothèque',
        loadComponent: () => import('./features/emprunts/pages/overdue-loans.page').then((m) => m.OverdueLoansPage),
      },
      {
        path: 'adherents',
        title: 'Adhérents — Bibliothèque',
        loadComponent: () => import('./features/adherents/pages/member-search.page').then((m) => m.MemberSearchPage),
      },
      {
        path: 'adherents/nouveau',
        title: 'Inscrire un adhérent — Bibliothèque',
        loadComponent: () => import('./features/adherents/pages/member-form.page').then((m) => m.MemberFormPage),
      },
      {
        path: 'adherents/a-archiver',
        title: 'Adhérents à archiver — Bibliothèque',
        loadComponent: () => import('./features/adherents/pages/members-to-archive.page').then((m) => m.MembersToArchivePage),
      },
      {
        path: 'adherents/:memberNumber',
        title: 'Fiche adhérent — Bibliothèque',
        loadComponent: () => import('./features/adherents/pages/member-detail.page').then((m) => m.MemberDetailPage),
      },
      {
        path: 'adherents/:memberNumber/modifier',
        title: 'Modifier l’adhérent — Bibliothèque',
        loadComponent: () => import('./features/adherents/pages/member-form.page').then((m) => m.MemberFormPage),
      },
      {
        path: 'reservations',
        title: 'Réservations à traiter — Bibliothèque',
        loadComponent: () => import('./features/reservations/pages/reservations-to-process.page').then((m) => m.ReservationsToProcessPage),
      },
      {
        path: 'penalites',
        title: 'Pénalités impayées — Bibliothèque',
        loadComponent: () => import('./features/penalites/pages/unpaid-fines.page').then((m) => m.UnpaidFinesPage),
      },
      {
        path: 'statistiques',
        title: 'Statistiques — Bibliothèque',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () => import('./features/pilotage/pages/statistics.page').then((m) => m.StatisticsPage),
      },
      {
        path: 'parametres',
        title: 'Paramètres — Bibliothèque',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () => import('./features/pilotage/pages/settings.page').then((m) => m.SettingsPage),
      },
      {
        path: 'audit',
        title: 'Journal d’audit — Bibliothèque',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () => import('./features/pilotage/pages/audit-log.page').then((m) => m.AuditLogPage),
      },
      {
        path: 'catalogue',
        title: 'Catalogue — Bibliothèque',
        loadComponent: () => import('./features/catalogue/pages/catalog-admin.page').then((m) => m.CatalogAdminPage),
      },
      {
        path: 'catalogue/nouvelle',
        title: 'Nouvelle notice — Bibliothèque',
        loadComponent: () => import('./features/catalogue/pages/book-form.page').then((m) => m.BookFormPage),
      },
      {
        path: 'catalogue/:bookId',
        title: 'Notice — Bibliothèque',
        loadComponent: () =>
          import('./features/catalogue/pages/book-admin-detail.page').then((m) => m.BookAdminDetailPage),
      },
      {
        path: 'catalogue/:bookId/modifier',
        title: 'Modifier la notice — Bibliothèque',
        loadComponent: () => import('./features/catalogue/pages/book-form.page').then((m) => m.BookFormPage),
      },
      {
        path: 'categories',
        title: 'Catégories — Bibliothèque',
        loadComponent: () => import('./features/catalogue/pages/categories.page').then((m) => m.CategoriesPage),
      },
      {
        path: 'comptes',
        title: 'Comptes du personnel — Bibliothèque',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () =>
          import('./features/authentification/pages/staff-accounts.page').then((m) => m.StaffAccountsPage),
      },
    ],
  },
  {
    path: 'mon-espace',
    component: MemberLayoutComponent,
    canActivate: [authGuard, passwordChangeGuard, roleGuard],
    data: { roles: ['MEMBER'] },
    children: [
      {
        path: '',
        pathMatch: 'full',
        title: 'Mon espace — Bibliothèque',
        loadComponent: () => import('./features/adherents/pages/member-space.page').then((m) => m.MemberSpacePage),
      },
      {
        path: 'coordonnees',
        title: 'Mes coordonnées — Bibliothèque',
        loadComponent: () => import('./features/adherents/pages/my-contact.page').then((m) => m.MyContactPage),
      },
    ],
  },
  {
    path: '',
    component: PublicLayoutComponent,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'catalogue' },
      {
        path: 'catalogue',
        title: 'Catalogue — Bibliothèque',
        loadComponent: () => import('./features/catalogue/pages/catalog-search.page').then((m) => m.CatalogSearchPage),
      },
      {
        path: 'catalogue/:bookId',
        title: 'Notice — Bibliothèque',
        loadComponent: () =>
          import('./features/catalogue/pages/book-public-detail.page').then((m) => m.BookPublicDetailPage),
      },
      {
        path: 'connexion',
        title: 'Se connecter — Bibliothèque',
        canActivate: [guestGuard],
        loadComponent: () => import('./features/authentification/pages/login.page').then((m) => m.LoginPage),
      },
      { path: 'acces-refuse', title: 'Accès refusé — Bibliothèque', component: ForbiddenPage },
      { path: '**', title: 'Page introuvable — Bibliothèque', component: NotFoundPage },
    ],
  },
];
