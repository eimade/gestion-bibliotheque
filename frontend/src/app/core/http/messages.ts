/** Messages transverses exacts (UX-socle §5.6). */
export const GENERIC_MESSAGES = {
  network: 'Le service est momentanément indisponible. Vérifiez votre connexion puis réessayez.',
  unexpected: 'Une erreur inattendue est survenue. Réessayez ; si le problème persiste, contactez l’administrateur.',
  forbidden: 'Vous n’avez pas les droits nécessaires pour effectuer cette action.',
  sessionExpired: 'Votre session a expiré. Veuillez vous reconnecter.',
  concurrentModification:
    'Cet élément a été modifié entre-temps par un autre utilisateur. Rechargez la page pour voir la dernière version.',
  notFound: 'L’élément demandé est introuvable.',
  required: 'Ce champ est obligatoire.',
  saved: 'Modifications enregistrées.',
  minSearchLength: (min: number) => `Saisissez au moins ${min} caractères.`,
} as const;
