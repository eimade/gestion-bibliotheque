import { ApiError } from '../../../core/http/api-error';
import { eligibilityReasonLabel, archiveReasonLabel } from '../../adherents/data-access/member-messages';
import { refusalLabel, releaseLabel } from '../../reservations/data-access/reservation-messages';
import { holdInstruction, loanErrorMessage, renewalBlockedLabel } from './loan-messages';

describe('Messages de circulation (UX-adherents, UX-emprunts, UX-reservations)', () => {
  it('libellés d’inéligibilité avec le seuil fourni par le serveur', () => {
    expect(eligibilityReasonLabel('OVERDUE_LOAN', 5)).toBe('Emprunt en retard');
    expect(eligibilityReasonLabel('UNPAID_FINES_ABOVE_THRESHOLD', 5).replace(/\s/g, ' '))
      .toBe('Pénalités impayées supérieures à 5,00 €');
    expect(archiveReasonLabel('ACTIVE_LOANS', { activeLoans: 1, activeReservations: 0, unpaidTotal: 0 }))
      .toBe('1 emprunt(s) en cours');
  });

  it('motif de prolongation impossible', () => {
    expect(renewalBlockedLabel({ allowed: false, blockedBy: 'LOAN_OVERDUE', memberReasons: [], newDueDate: '2026-11-21' }, 5))
      .toBe('Prolongation impossible : emprunt en retard.');
    expect(renewalBlockedLabel({ allowed: false, blockedBy: 'MEMBER_NOT_ELIGIBLE', memberReasons: ['MEMBERSHIP_EXPIRED'],
      newDueDate: '2026-11-21' }, 5)).toBe('Prolongation impossible : adhésion expirée.');
  });

  it('instruction de mise de côté en majuscules et destination d’un exemplaire libéré', () => {
    const hold = { memberNumber: 'A000789', lastName: 'Bernard', firstName: 'Luc', pickupDeadline: '2026-10-20' };
    expect(holdInstruction(hold)).toBe('METTRE DE CÔTÉ pour BERNARD Luc (A000789), à retirer avant le 20/10/2026');
    expect(releaseLabel({ copyNumber: '00000877', shelfMark: 'R ZOL', destination: 'SHELF' })).toBe('Remettre en rayon : R ZOL');
  });

  it('motifs de refus de réservation selon le public', () => {
    expect(refusalLabel('RESERVATION_ALREADY_EXISTS', 3, false, [], 5)).toBe('Vous avez déjà une réservation en cours pour ce livre.');
    expect(refusalLabel('BOOK_ALREADY_BORROWED', 3, true, [], 5)).toBe('Cet adhérent a déjà ce livre en cours d’emprunt.');
    expect(refusalLabel('RESERVATION_LIMIT_REACHED', 3, false, [], 5)).toBe('Nombre maximal de réservations atteint (3).');
  });

  it('refus d’emprunt avec tous les motifs, quota seul en message court', () => {
    const notEligible = new ApiError(409, 'MEMBER_NOT_ELIGIBLE', 'Emprunt impossible.',
      { reasons: ['OVERDUE_LOAN', 'UNPAID_FINES_ABOVE_THRESHOLD'] });
    const quota = new ApiError(409, 'MEMBER_NOT_ELIGIBLE', 'Emprunt impossible.', { reasons: ['LOAN_LIMIT_REACHED'] });
    expect(loanErrorMessage(notEligible).replace(/\s/g, ' '))
      .toBe('Emprunt impossible : Emprunt en retard, Pénalités impayées supérieures à 5,00 €.');
    expect(loanErrorMessage(quota)).toBe('Nombre maximal d’emprunts atteint.');
    expect(loanErrorMessage(new ApiError(404, 'COPY_NOT_FOUND', 'x', {}))).toBe('Exemplaire inconnu.');
  });
});
