# syntax=docker/dockerfile:1
# Image du frontend (ADR-005) : construction Angular puis service statique nginx non-root,
# qui sert aussi de reverse proxy /api vers le backend (même origine : cookie de session + CSRF).
# Prérequis : projet Angular nommé « bibliotheque-frontend » (sortie dist/bibliotheque-frontend/browser).

# ---- Étape 1 : construction ----------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app
# Le binaire Cypress (tests E2E) est inutile dans l'image (ARCH-authentification §7)
ENV CYPRESS_INSTALL_BINARY=0
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npx ng build --configuration production

# ---- Étape 2 : service ---------------------------------------------------------
FROM nginxinc/nginx-unprivileged:1.28-alpine
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY nginx/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /app/dist/bibliotheque-frontend/browser /usr/share/nginx/html
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -q --spider http://127.0.0.1:8080/ || exit 1
