import { ApiError } from '../../../core/http/api-error';
import { commonErrorMessage } from '../../authentification/data-access/auth-messages';

/** Messages exacts de l'UX catalogue (UX-catalogue §3), indexés par code d'erreur du serveur. */
export const CATALOG_MESSAGES = {
  help: 'Saisissez un titre, un auteur ou un ISBN.',
  noResult: 'Aucun livre ne correspond à votre recherche.',
  bookNotFound: 'Ce livre est introuvable.',
  noticeNotFound: 'Cette notice est introuvable.',
  noAdminResult: 'Aucune notice ne correspond à votre recherche.',
  availableOnShelf: 'Un exemplaire est disponible en rayon.',
  noneAvailable: 'Aucun exemplaire n’est disponible pour le moment.',
  isbnInvalid: 'ISBN invalide.',
  authorsRequired: 'Ajoutez au moins un auteur.',
  categoriesRequired: 'Sélectionnez au moins une catégorie.',
  yearRange: (max: number) => `L’année doit être comprise entre 1450 et ${max}.`,
  positiveInteger: 'Saisissez un nombre entier positif.',
  max: (count: number) => `${count} caractères maximum.`,
  summaryMax: '4 000 caractères maximum.',
  bookSaved: 'Notice enregistrée.',
  bookDeleted: 'Notice supprimée.',
  bookHasCopies: 'Suppression impossible : la notice possède des exemplaires.',
  noCopy: 'Aucun exemplaire pour cette notice. Elle n’est pas visible dans le catalogue public.',
  discardChanges: 'Abandonner les modifications non enregistrées ?',
  quantityRange: 'Saisissez un nombre entre 1 et 20.',
  acquisitionFuture: 'La date d’acquisition ne peut pas être dans le futur.',
  withdrawReasonRequired: 'Sélectionnez un motif de retrait.',
  copyInCirculation: 'Retrait impossible : l’exemplaire est en circulation.',
  copyWithdrawn: 'Cet exemplaire a été retiré du fonds et ne peut plus être modifié.',
  copyUpdated: 'Exemplaire modifié.',
  copyWithdrawnToast: 'Exemplaire retiré du fonds.',
  copiesAdded: (numbers: readonly string[]) =>
    `${numbers.length} exemplaire${numbers.length > 1 ? 's' : ''} ajouté${numbers.length > 1 ? 's' : ''} : ${numbers.join(', ')}.`,
  categoryLabelLength: 'Le libellé doit contenir entre 2 et 50 caractères.',
  categoryExists: 'Cette catégorie existe déjà.',
  categoryInUse: (count: number) => `Suppression impossible : cette catégorie est utilisée par ${count} notice(s).`,
  categoryCreated: 'Catégorie créée.',
  categoryRenamed: 'Catégorie renommée.',
  categoryDeleted: 'Catégorie supprimée.',
  noCategory: 'Aucune catégorie. Créez la première ci-dessus.',
} as const;

export function catalogErrorMessage(error: ApiError): string {
  switch (error.code) {
    case 'ISBN_INVALID':
      return CATALOG_MESSAGES.isbnInvalid;
    case 'ISBN_ALREADY_EXISTS':
      return error.detail;
    case 'BOOK_NOT_FOUND':
      return CATALOG_MESSAGES.noticeNotFound;
    case 'BOOK_HAS_COPIES':
      return CATALOG_MESSAGES.bookHasCopies;
    case 'COPY_IN_CIRCULATION':
      return CATALOG_MESSAGES.copyInCirculation;
    case 'COPY_WITHDRAWN':
      return CATALOG_MESSAGES.copyWithdrawn;
    case 'CATEGORY_ALREADY_EXISTS':
      return CATALOG_MESSAGES.categoryExists;
    case 'CATEGORY_IN_USE':
      return CATALOG_MESSAGES.categoryInUse(Number(error.property('bookCount') ?? 0));
    case 'VALIDATION_FAILED':
      return error.problem.fieldErrors?.[0]?.message ?? error.detail;
    default:
      return commonErrorMessage(error);
  }
}
