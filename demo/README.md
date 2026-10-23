# Tester l'application

Cette copie de l'application tourne dans votre Codespace, avec des données de démonstration fictives.
Vous pouvez tout essayer : rien de ce que vous faites n'affecte les autres visiteurs.

## Ouvrir l'application

1. Patientez pendant le démarrage : la première fois, la construction prend **5 à 10 minutes**
   (progression visible dans le terminal « postStartCommand »).
2. L'application s'ouvre dans un nouvel onglet. Sinon : onglet **Ports** en bas de VS Code → ligne
   **Application (8080)** → icône 🌐.

## Comptes de démonstration

| Profil | E-mail | Mot de passe |
|---|---|---|
| Administrateur | `admin@bibliotheque.example` | `DemoAdmin2026x` |
| Bibliothécaire | `nadia.rami@bibliotheque.example` | `NadiaGuichet2026` |
| Adhérent | `jean.martin@exemple.fr` | `LecteurDemo2026` |

Le catalogue public est accessible sans connexion.

## Idées de parcours

- **Guichet** (bibliothécaire) : *Guichet — Emprunt* (`Alt+E`), adhérent `A000002`, exemplaire `00000002` ;
  *Guichet — Retour* (`Alt+R`), exemplaire `00000001` (retard de 6 jours → pénalité calculée), puis `00000026`
  (réservé par Lucas Bernard → mis de côté).
- **Administration** : comptes du personnel, paramètres de prêt, statistiques, journal d'audit, remise gracieuse
  sur la pénalité de Sophie Laurent (`A000004`).
- **Espace adhérent** : emprunts en cours, prolongation, réservation d'un livre indisponible.

## Repartir des données initiales

```bash
bash demo/start-demo.sh --reset
```
