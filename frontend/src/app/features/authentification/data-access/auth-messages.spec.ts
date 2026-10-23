import { ApiError } from '../../../core/http/api-error';
import { GENERIC_MESSAGES } from '../../../core/http/messages';
import { AUTH_MESSAGES, commonErrorMessage, lockedMessage, loginErrorMessage, passwordErrorMessage } from './auth-messages';

const error = (status: number, code: string, problem: Record<string, unknown> = {}) =>
  new ApiError(status, code, `détail ${code}`, problem);

describe('Messages de l’authentification (UX-authentification)', () => {
  it('ne révèle pas l’existence d’un e-mail (AC-001-3)', () => {
    expect(loginErrorMessage(error(401, 'INVALID_CREDENTIALS'))).toBe('Identifiants incorrects.');
  });

  it('indique l’heure de fin du verrouillage, à l’heure de Paris (AC-001-4)', () => {
    expect(loginErrorMessage(error(401, 'ACCOUNT_LOCKED', { lockedUntil: '2026-10-17T12:15:00Z' }))).toBe(
      'Votre compte est temporairement verrouillé suite à plusieurs tentatives infructueuses. Réessayez à partir de 14:15.',
    );
  });

  it('garde le message de verrouillage sans heure exploitable', () => {
    expect(lockedMessage('pas une date')).toBe(
      'Votre compte est temporairement verrouillé suite à plusieurs tentatives infructueuses.',
    );
  });

  it('signale un compte désactivé (AC-001-5)', () => {
    expect(loginErrorMessage(error(401, 'ACCOUNT_DISABLED'))).toBe(AUTH_MESSAGES.accountDisabledLogin);
  });

  it.each([
    ['CURRENT_PASSWORD_INCORRECT', 'Mot de passe actuel incorrect.'],
    ['PASSWORD_RECENTLY_USED', 'Mot de passe déjà utilisé récemment. Choisissez-en un autre.'],
    ['PASSWORD_POLICY_VIOLATION', AUTH_MESSAGES.passwordPolicy],
    ['ACCOUNT_DISABLED', 'Ce compte est désactivé.'],
  ])('traduit %s pour le changement de mot de passe', (code, message) => {
    expect(passwordErrorMessage(error(422, code))).toBe(message);
  });

  it('applique les messages transverses et sinon le texte du serveur', () => {
    expect(commonErrorMessage(error(0, 'NETWORK_ERROR'))).toBe(GENERIC_MESSAGES.network);
    expect(commonErrorMessage(error(409, 'CONCURRENT_MODIFICATION'))).toBe(GENERIC_MESSAGES.concurrentModification);
    expect(commonErrorMessage(error(503, 'AUTRE'))).toBe(GENERIC_MESSAGES.unexpected);
    expect(commonErrorMessage(error(409, 'AUTRE'))).toBe('détail AUTRE');
  });
});
