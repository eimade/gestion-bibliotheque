import { defineConfig } from 'cypress';

/**
 * Tests E2E (ADR-005) exécutés contre la pile Docker Compose ; les scénarios sont écrits par le QA
 * dans `cypress/e2e/<feature>/`. `CYPRESS_BASE_URL` permet de viser une autre adresse.
 */
export default defineConfig({
  e2e: {
    baseUrl: 'http://localhost:8080',
    specPattern: 'cypress/e2e/**/*.cy.ts',
    supportFile: 'cypress/support/e2e.ts',
    video: false,
  },
});
