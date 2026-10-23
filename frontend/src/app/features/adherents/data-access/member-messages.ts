import { ApiError } from '../../../core/http/api-error';
import { formatEuro } from '../../../shared/pipes/euro.pipe';
import { formatDateFr } from '../../../shared/pipes/date-fr.pipe';
import { AUTH_MESSAGES, commonErrorMessage } from '../../authentification/data-access/auth-messages';
import { ArchiveBlockingReason, EligibilityReason } from './member.models';

/** Messages exacts d'UX-adherents. */
export const MEMBER_MESSAGES = {
  minSearch: 'Saisissez au moins 2 caractères.',
  noResult: 'Aucun adhérent ne correspond à votre recherche.',
  notFound: (number: string) => `Aucun adhérent ne porte le numéro ${number}.`,
  required: 'Ce champ est obligatoire.',
  max100: '100 caractères maximum.',
  birthDateFuture: 'La date de naissance doit être antérieure à aujourd’hui.',
  guardianRequired: 'Représentant légal obligatoire pour un adhérent de moins de 15 ans.',
  phoneInvalid: 'Saisissez un numéro de téléphone valide.',
  emailInvalid: 'Saisissez une adresse e-mail valide.',
  emailUsed: 'Cette adresse e-mail est déjà utilisée.',
  postalCode: 'Le code postal doit contenir 5 chiffres.',
  passwordPolicy: AUTH_MESSAGES.passwordPolicy,
  passwordMismatch: AUTH_MESSAGES.passwordMismatch,
  archivedNoEdit: 'Un adhérent archivé ne peut plus être modifié.',
  archivedNotice: 'Les données personnelles de cet adhérent ont été effacées.',
  registered: (number: string, expiry: string) =>
    `Adhérent ${number} inscrit. Adhésion valable jusqu’au ${formatDateFr(expiry)}.`,
  saved: 'Modifications enregistrées.',
  renewed: (expiry: string) => `Adhésion renouvelée jusqu’au ${formatDateFr(expiry)}.`,
  renewFrom: (date: string) => `Renouvellement possible à partir du ${formatDateFr(date)}.`,
  renewSuspended: 'Un adhérent suspendu doit d’abord être réactivé.',
  renewArchived: 'Un adhérent archivé ne peut pas être renouvelé.',
  suspendReason: 'Le motif doit contenir entre 3 et 255 caractères.',
  suspended: 'Adhérent suspendu.',
  reactivated: (status: string) => `Adhérent réactivé (statut : ${status}).`,
  archived: 'Adhérent archivé. Ses données personnelles ont été effacées.',
  noLoan: 'Aucun emprunt en cours.',
  noReservation: 'Aucune réservation active.',
  noFine: 'Aucune pénalité impayée.',
  noHistory: 'Aucun emprunt rendu au cours des 4 derniers mois.',
  noneToArchive: 'Aucun adhérent à archiver.',
  contactSaved: 'Vos coordonnées ont été mises à jour.',
  nothingInProgress: 'Vous n’avez aucun document en cours.',
  expiredInvite: 'Votre adhésion a expiré. Renouvelez-la à l’accueil de la bibliothèque.',
  myNoReservation: 'Aucune réservation.',
  myNoFine: 'Aucune pénalité.',
  payAtDesk: 'À régler à l’accueil de la bibliothèque.',
} as const;

/** Libellés exacts des motifs d'inéligibilité (UX-adherents ECR-18), le seuil étant fourni par le serveur. */
export function eligibilityReasonLabel(reason: EligibilityReason, threshold: number): string {
  switch (reason) {
    case 'MEMBERSHIP_EXPIRED':
      return 'Adhésion expirée';
    case 'MEMBER_SUSPENDED':
      return 'Adhérent suspendu';
    case 'MEMBER_ARCHIVED':
      return 'Adhérent archivé';
    case 'OVERDUE_LOAN':
      return 'Emprunt en retard';
    case 'UNPAID_FINES_ABOVE_THRESHOLD':
      return `Pénalités impayées supérieures à ${formatEuro(threshold)}`;
    case 'LOAN_LIMIT_REACHED':
      return 'Nombre maximal d’emprunts atteint';
  }
}

/** Motifs bloquant l'archivage (UX-adherents ECR-20). */
export function archiveReasonLabel(reason: ArchiveBlockingReason, counts: {
  readonly activeLoans: number;
  readonly activeReservations: number;
  readonly unpaidTotal: number;
}): string {
  switch (reason) {
    case 'ACTIVE_LOANS':
      return `${counts.activeLoans} emprunt(s) en cours`;
    case 'ACTIVE_RESERVATIONS':
      return `${counts.activeReservations} réservation(s) active(s)`;
    case 'UNPAID_FINES':
      return `Pénalités impayées (${formatEuro(counts.unpaidTotal)})`;
  }
}

export function memberErrorMessage(error: ApiError): string {
  switch (error.code) {
    case 'EMAIL_ALREADY_USED':
      return MEMBER_MESSAGES.emailUsed;
    case 'GUARDIAN_REQUIRED':
      return MEMBER_MESSAGES.guardianRequired;
    case 'PASSWORD_POLICY_VIOLATION':
      return MEMBER_MESSAGES.passwordPolicy;
    case 'MEMBER_ARCHIVED':
      return error.detail;
    case 'MEMBER_SUSPENDED':
      return MEMBER_MESSAGES.renewSuspended;
    case 'RENEWAL_TOO_EARLY': {
      const from = error.property('renewableFrom');
      return typeof from === 'string' ? MEMBER_MESSAGES.renewFrom(from) : error.detail;
    }
    case 'VALIDATION_FAILED':
      return error.problem.fieldErrors?.[0]?.message ?? error.detail;
    default:
      return commonErrorMessage(error);
  }
}
