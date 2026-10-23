import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';

/** Messages exacts de l'UX authentification, indexés par code d'erreur du serveur. */
export const AUTH_MESSAGES = {
  invalidCredentials: 'Identifiants incorrects.',
  accountDisabledLogin: 'Compte désactivé, contactez la bibliothèque.',
  accountDisabled: 'Ce compte est désactivé.',
  currentPasswordIncorrect: 'Mot de passe actuel incorrect.',
  passwordRecentlyUsed: 'Mot de passe déjà utilisé récemment. Choisissez-en un autre.',
  passwordPolicy:
    'Le mot de passe doit contenir au moins 12 caractères, dont une majuscule, une minuscule et un chiffre.',
  passwordMismatch: 'Les mots de passe ne correspondent pas.',
  emailInvalid: 'Saisissez une adresse e-mail valide.',
  emailAlreadyUsed: 'Cette adresse e-mail est déjà utilisée.',
  roleRequired: 'Sélectionnez un rôle.',
  maxLength100: '100 caractères maximum.',
  lastAdminRequired: 'Au moins un administrateur actif est requis.',
  selfDeactivation: 'Vous ne pouvez pas désactiver votre propre compte.',
  passwordChanged: 'Votre mot de passe a été modifié.',
  accountCreated: 'Compte créé. Communiquez le mot de passe initial à l’agent.',
  accountDisabledToast: 'Compte désactivé.',
  accountEnabledToast: 'Compte réactivé.',
  passwordReset: 'Mot de passe réinitialisé. Le compte est débloqué.',
  forcedChange: 'Pour des raisons de sécurité, vous devez choisir un nouveau mot de passe avant de continuer.',
  noStaffAccount: 'Aucun compte ne correspond à votre recherche.',
} as const;

const TIME_FORMAT = new Intl.DateTimeFormat('fr-FR', {
  hour: '2-digit',
  minute: '2-digit',
  timeZone: 'Europe/Paris',
});

/** Message de verrouillage avec l'heure de fin, à l'heure de Paris (AC-001-4, NFR-11). */
export function lockedMessage(lockedUntil: unknown): string {
  const base = 'Votre compte est temporairement verrouillé suite à plusieurs tentatives infructueuses.';
  const date = typeof lockedUntil === 'string' ? new Date(lockedUntil) : null;
  if (!date || Number.isNaN(date.getTime())) {
    return base;
  }
  return `${base} Réessayez à partir de ${TIME_FORMAT.format(date)}.`;
}

export function loginErrorMessage(error: ApiError): string {
  switch (error.code) {
    case 'INVALID_CREDENTIALS':
      return AUTH_MESSAGES.invalidCredentials;
    case 'ACCOUNT_LOCKED':
      return lockedMessage(error.property('lockedUntil'));
    case 'ACCOUNT_DISABLED':
      return AUTH_MESSAGES.accountDisabledLogin;
    default:
      return commonErrorMessage(error);
  }
}

export function passwordErrorMessage(error: ApiError): string {
  switch (error.code) {
    case 'CURRENT_PASSWORD_INCORRECT':
      return AUTH_MESSAGES.currentPasswordIncorrect;
    case 'PASSWORD_RECENTLY_USED':
      return AUTH_MESSAGES.passwordRecentlyUsed;
    case 'PASSWORD_POLICY_VIOLATION':
      return AUTH_MESSAGES.passwordPolicy;
    case 'ACCOUNT_DISABLED':
      return AUTH_MESSAGES.accountDisabled;
    default:
      return commonErrorMessage(error);
  }
}

export function staffAccountErrorMessage(error: ApiError): string {
  switch (error.code) {
    case 'EMAIL_ALREADY_USED':
      return AUTH_MESSAGES.emailAlreadyUsed;
    case 'LAST_ADMIN_REQUIRED':
      return AUTH_MESSAGES.lastAdminRequired;
    case 'SELF_DEACTIVATION_FORBIDDEN':
      return AUTH_MESSAGES.selfDeactivation;
    case 'PASSWORD_POLICY_VIOLATION':
      return AUTH_MESSAGES.passwordPolicy;
    default:
      return commonErrorMessage(error);
  }
}

/** Codes transverses ; à défaut, le texte du serveur (UX-socle §5.6). */
export function commonErrorMessage(error: ApiError): string {
  switch (error.code) {
    case 'NETWORK_ERROR':
      return GENERIC_MESSAGES.network;
    case 'ACCESS_DENIED':
      return GENERIC_MESSAGES.forbidden;
    case 'CONCURRENT_MODIFICATION':
      return GENERIC_MESSAGES.concurrentModification;
    case 'AUTHENTICATION_REQUIRED':
      return GENERIC_MESSAGES.sessionExpired;
    case 'INTERNAL_ERROR':
      return GENERIC_MESSAGES.unexpected;
    default:
      return error.status >= 500 ? GENERIC_MESSAGES.unexpected : error.detail;
  }
}
