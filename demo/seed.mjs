// Jeu de données de démonstration (catalogue, adhérents, emprunts, retard, pénalité, réservation) créé par l'API.
// Usage : node demo/seed.mjs <url de l'application> <mot de passe administrateur initial> <conteneur MariaDB>
// Les comptes créés sont des comptes de démonstration : ne jamais lancer ce script sur une base réelle.
import { execFileSync } from 'node:child_process';

const [BASE, INITIAL_PASSWORD, DB_CONTAINER] = process.argv.slice(2);
if (!BASE || !INITIAL_PASSWORD || !DB_CONTAINER) {
  console.error('Usage : node demo/seed.mjs <url> <mot de passe admin initial> <conteneur MariaDB>');
  process.exit(2);
}

export const ACCOUNTS = {
  admin: { email: 'admin@bibliotheque.example', password: 'DemoAdmin2026x' },
  librarian: { email: 'nadia.rami@bibliotheque.example', password: 'NadiaGuichet2026' },
  member: { email: 'jean.martin@exemple.fr', password: 'LecteurDemo2026' },
};
const MEMBER_INITIAL_PASSWORD = 'Bienvenue2026x';

class Session {
  constructor() { this.cookies = new Map(); }
  cookieHeader() { return [...this.cookies].map(([k, v]) => `${k}=${v}`).join('; '); }
  store(response) {
    for (const raw of response.headers.getSetCookie()) {
      const [pair] = raw.split(';');
      const i = pair.indexOf('=');
      const value = pair.slice(i + 1);
      if (value) this.cookies.set(pair.slice(0, i), value); else this.cookies.delete(pair.slice(0, i));
    }
  }
  async call(method, path, body) {
    if (!this.cookies.has('XSRF-TOKEN')) this.store(await fetch(`${BASE}/api/v1/auth/me`, { headers: { cookie: this.cookieHeader() } }));
    const headers = { cookie: this.cookieHeader(), 'X-XSRF-TOKEN': this.cookies.get('XSRF-TOKEN') ?? '' };
    if (body !== undefined) headers['content-type'] = 'application/json';
    const response = await fetch(`${BASE}/api/v1${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    this.store(response);
    const text = await response.text();
    let json = null;
    try { json = text ? JSON.parse(text) : null; } catch { json = text; }
    if (response.status >= 400) throw new Error(`${method} ${path} → ${response.status} ${text}`);
    return json;
  }
  get(p) { return this.call('GET', p); }
  post(p, b = {}) { return this.call('POST', p, b); }
}

/** Première connexion d'un compte au mot de passe provisoire : remplacement par le mot de passe de démonstration. */
async function activate(email, temporary, final) {
  const s = new Session();
  await s.post('/auth/login', { email, password: temporary });
  await s.post('/auth/password', { currentPassword: temporary, newPassword: final });
}

const sql = (statement) => execFileSync('docker', ['exec', '-i', DB_CONTAINER, 'sh', '-c',
  'mariadb -u root -p"$MARIADB_ROOT_PASSWORD" -N "$MARIADB_DATABASE"'], { input: statement, encoding: 'utf8' });

const admin = new Session();
await admin.post('/auth/login', { email: ACCOUNTS.admin.email, password: INITIAL_PASSWORD });
await admin.post('/auth/password', { currentPassword: INITIAL_PASSWORD, newPassword: ACCOUNTS.admin.password });
sql("UPDATE user_accounts SET last_name = 'DUBOIS', first_name = 'Claire' WHERE email = 'admin@bibliotheque.example';");
console.log('✓ administratrice (Claire Dubois)');

await admin.post('/staff-accounts', { lastName: 'Rami', firstName: 'Nadia', email: ACCOUNTS.librarian.email, role: 'LIBRARIAN',
  initialPassword: 'Provisoire2026x' });
await activate(ACCOUNTS.librarian.email, 'Provisoire2026x', ACCOUNTS.librarian.password);
console.log('✓ bibliothécaire (Nadia Rami)');

// --- Catégories et auteurs ---
const cat = {};
for (const label of ['Roman', 'Classique', 'Développement personnel', 'Jeunesse', 'Histoire', 'Sciences']) {
  cat[label] = (await admin.post('/categories', { label })).id;
}
const authorIds = {};
async function author(lastName, firstName) {
  const key = `${lastName}|${firstName}`;
  authorIds[key] ??= (await admin.post('/authors', { lastName, firstName })).id;
  return authorIds[key];
}

const BOOKS = [
  ['Le Comte de Monte-Cristo', ['Dumas', 'Alexandre'], ['Roman', 'Classique'], 1844, 'Le Livre de Poche', 1504, 3, 'R DUM', 'Trahi et emprisonné au château d’If, Edmond Dantès s’évade et revient sous les traits du comte de Monte-Cristo pour se venger.'],
  ['Les Misérables', ['Hugo', 'Victor'], ['Roman', 'Classique', 'Histoire'], 1862, 'Gallimard', 1664, 2, 'R HUG', 'Le destin de Jean Valjean, ancien forçat, dans la France du XIXe siècle.'],
  ['Germinal', ['Zola', 'Émile'], ['Roman', 'Classique'], 1885, 'Folio', 592, 2, 'R ZOL', 'La grève des mineurs de Montsou, fresque sociale du Second Empire.'],
  ['L’Étranger', ['Camus', 'Albert'], ['Roman'], 1942, 'Gallimard', 186, 2, 'R CAM', 'Meursault, homme indifférent, commet un meurtre sous le soleil d’Alger.'],
  ['Bel-Ami', ['de Maupassant', 'Guy'], ['Roman', 'Classique'], 1885, 'Folio', 416, 1, 'R MAU', 'L’ascension sociale d’un séducteur sans scrupules dans le Paris de la presse.'],
  ['Le Petit Prince', ['de Saint-Exupéry', 'Antoine'], ['Jeunesse', 'Classique'], 1943, 'Gallimard', 96, 3, 'J SAI', 'Un aviateur rencontre dans le désert un petit prince venu d’une autre planète.'],
  ['L’Alchimiste', ['Coelho', 'Paulo'], ['Roman', 'Développement personnel'], 1988, 'J’ai lu', 192, 2, 'R COE', 'Le voyage initiatique de Santiago, jeune berger andalou, à la recherche de sa Légende personnelle.'],
  ['Les 7 habitudes de ceux qui réalisent tout ce qu’ils entreprennent', ['Covey', 'Stephen R.'], ['Développement personnel'], 1989, 'J’ai lu', 384, 2, 'DP COV', 'Sept principes pour gagner en efficacité personnelle et relationnelle.'],
  ['Père riche, père pauvre', ['Kiyosaki', 'Robert T.'], ['Développement personnel'], 1997, 'Un monde différent', 272, 2, 'DP KIY', 'Ce que les riches enseignent à leurs enfants à propos de l’argent.'],
  ['Réfléchissez et devenez riche', ['Hill', 'Napoleon'], ['Développement personnel'], 1937, 'J’ai lu', 320, 1, 'DP HIL', 'Les treize principes de la réussite tirés de l’étude de centaines de personnalités.'],
  ['Comment se faire des amis', ['Carnegie', 'Dale'], ['Développement personnel'], 1936, 'Le Livre de Poche', 320, 2, 'DP CAR', 'Les clés pour mieux communiquer, convaincre et entretenir de bonnes relations.'],
  ['Le pouvoir du moment présent', ['Tolle', 'Eckhart'], ['Développement personnel'], 1997, 'J’ai lu', 256, 1, 'DP TOL', 'Un guide d’éveil spirituel pour vivre pleinement l’instant présent.'],
  ['Les quatre accords toltèques', ['Ruiz', 'Miguel'], ['Développement personnel'], 1997, 'Jouvence', 128, 2, 'DP RUI', 'Quatre engagements simples pour se libérer des croyances limitantes.'],
  ['Atomic Habits', ['Clear', 'James'], ['Développement personnel'], 2018, 'Marabout', 320, 1, 'DP CLE', 'Changer de petites habitudes pour obtenir de grands résultats.'],
  ['Miracle Morning', ['Elrod', 'Hal'], ['Développement personnel'], 2012, 'First', 240, 1, 'DP ELR', 'Une routine matinale en six étapes pour transformer sa vie.'],
  ['L’homme le plus riche de Babylone', ['Clason', 'George S.'], ['Développement personnel'], 1926, 'Un monde différent', 160, 1, 'DP CLA', 'Les lois de la richesse racontées à travers des paraboles de l’ancienne Babylone.'],
  ['Sapiens : une brève histoire de l’humanité', ['Harari', 'Yuval Noah'], ['Histoire', 'Sciences'], 2011, 'Albin Michel', 512, 2, 'H HAR', 'De l’apparition d’Homo sapiens à la révolution scientifique.'],
  ['Une brève histoire du temps', ['Hawking', 'Stephen'], ['Sciences'], 1988, 'Flammarion', 240, 1, 'S HAW', 'Du big bang aux trous noirs, l’Univers expliqué au grand public.'],
];
const today = new Date().toISOString().slice(0, 10);
const copiesOf = {};
for (const [title, [last, first], cats, year, publisher, pages, qty, shelf, summary] of BOOKS) {
  const book = await admin.post('/books', { title, authorIds: [await author(last, first)], categoryIds: cats.map((c) => cat[c]),
    languageCode: 'fre', publicationYear: year, publisher, pageCount: pages, summary });
  copiesOf[title] = { id: book.id, copies: (await admin.post(`/books/${book.id}/copies`, { quantity: qty, shelfMark: shelf,
    physicalCondition: 'GOOD', acquisitionDate: today })).map((c) => c.copyNumber) };
}
console.log('✓ catalogue :', BOOKS.length, 'notices');

// --- Adhérents ---
const MEMBERS = [
  ['Martin', 'Jean', '1990-03-12', 'jean.martin@exemple.fr', '0612345678', '12 rue des Lilas', '75011', 'Paris'],
  ['Martinez', 'Ana', '2001-07-02', 'ana.martinez@exemple.fr', '0623456789', '5 avenue Foch', '69006', 'Lyon'],
  ['Haddad', 'Karim', '1985-11-20', 'karim.haddad@exemple.fr', '0634567890', '8 rue Victor Hugo', '13001', 'Marseille'],
  ['Laurent', 'Sophie', '1978-05-14', 'sophie.laurent@exemple.fr', '0645678901', '22 boulevard Voltaire', '75011', 'Paris'],
  ['Bernard', 'Lucas', '1996-09-30', 'lucas.bernard@exemple.fr', '0656789012', '3 place Bellecour', '69002', 'Lyon'],
  ['Alaoui', 'Fatima Zahra', '1993-01-08', 'fatima.alaoui@exemple.fr', '0667890123', '17 rue de la Paix', '75002', 'Paris'],
  ['Moreau', 'Thomas', '1988-12-03', 'thomas.moreau@exemple.fr', '0678901234', '40 quai de la Loire', '75019', 'Paris'],
];
const number = {};
for (const [lastName, firstName, birthDate, email, phone, street, postalCode, city] of MEMBERS) {
  const m = await admin.post('/members', { lastName, firstName, birthDate, email, phone, street, postalCode, city,
    password: MEMBER_INITIAL_PASSWORD, confirmDuplicate: false });
  number[firstName] = m.memberNumber;
}
await activate(ACCOUNTS.member.email, MEMBER_INITIAL_PASSWORD, ACCOUNTS.member.password);
console.log('✓ adhérents :', Object.values(number).join(', '));

// --- Circulation ---
const loan = (who, title, i = 0) => admin.post('/loans', { memberNumber: number[who], copyNumber: copiesOf[title].copies[i] });
await loan('Jean', 'Atomic Habits');
await loan('Jean', 'Le Petit Prince');
await loan('Ana', 'L’Étranger');
await loan('Fatima Zahra', 'Les quatre accords toltèques');
await loan('Thomas', 'Sapiens : une brève histoire de l’humanité');
const late = await loan('Karim', 'Le Comte de Monte-Cristo');
sql(`UPDATE loans SET loan_date = DATE_SUB(CURDATE(), INTERVAL 27 DAY), due_date = DATE_SUB(CURDATE(), INTERVAL 6 DAY) WHERE id = ${late.loanId};`);
// Retour tardif de Sophie → pénalité impayée (pour la remise gracieuse)
const sophie = await loan('Sophie', 'Germinal');
sql(`UPDATE loans SET loan_date = DATE_SUB(CURDATE(), INTERVAL 30 DAY), due_date = DATE_SUB(CURDATE(), INTERVAL 9 DAY) WHERE id = ${sophie.loanId};`);
await admin.post('/returns', { copyNumber: copiesOf['Germinal'].copies[0] });
// Lucas réserve « Atomic Habits » (unique exemplaire emprunté par Jean)
await admin.post('/reservations', { bookId: copiesOf['Atomic Habits'].id, memberNumber: number['Lucas'] });
console.log('✓ emprunts, retard, pénalité, réservation');
