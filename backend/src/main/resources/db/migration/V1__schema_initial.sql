-- =============================================================================
-- V1__schema_initial.sql — Schéma initial de l'application de gestion de bibliothèque
-- Cible : MariaDB 11.4 LTS, InnoDB, utf8mb4_unicode_ci
-- Référence : docs/architecture/ARCH-socle.md §2, ADR-002
--
-- Conventions :
--   * DATETIME(6) = instant technique en UTC ; DATE = date métier calendaire (Europe/Paris, NFR-11).
--   * La collation utf8mb4_unicode_ci est insensible à la casse et aux accents :
--     les contraintes UNIQUE sur e-mail (RG-14) et libellé de catégorie (RG-12) en bénéficient.
--   * Les règles dépendant de la date du jour ou de paramètres (RG-40) sont appliquées
--     par le service ; tout ce qui est exprimable sans elles est contraint ici.
--   * Les colonnes générées PERSISTENT "active_*" portent des index UNIQUE partiels
--     (NULL hors état actif) qui garantissent NFR-10 même en cas d'accès concurrents.
--   * Migration IMMUABLE une fois appliquée : toute évolution passe par V<n+1>.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Séquences des numéros fonctionnels (jamais réattribués : RG-09, RG-13)
-- -----------------------------------------------------------------------------
CREATE SEQUENCE member_number_seq START WITH 1 MINVALUE 1 MAXVALUE 999999 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE copy_number_seq   START WITH 1 MINVALUE 1 MAXVALUE 99999999 INCREMENT BY 1 NOCACHE NOCYCLE;

-- -----------------------------------------------------------------------------
-- Données de référence : langues (Q-22)
-- -----------------------------------------------------------------------------
CREATE TABLE languages (
    code          CHAR(3)      NOT NULL,
    label         VARCHAR(50)  NOT NULL,
    display_order SMALLINT     NOT NULL,
    PRIMARY KEY (code),
    CONSTRAINT uk_languages_label UNIQUE (label),
    CONSTRAINT ck_languages_code CHECK (code REGEXP '^[a-z]{3}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- Adhérents (SPEC-adherents, ENT-Adherent)
-- Les colonnes d'identité sont NULL uniquement après archivage (RG-18).
-- -----------------------------------------------------------------------------
CREATE TABLE members (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    member_number       CHAR(7)       NOT NULL,
    last_name           VARCHAR(100)  NULL,
    first_name          VARCHAR(100)  NULL,
    birth_date          DATE          NULL,
    birth_year          SMALLINT      NOT NULL,
    phone               VARCHAR(20)   NULL,
    street              VARCHAR(255)  NULL,
    postal_code         VARCHAR(10)   NOT NULL,
    city                VARCHAR(100)  NULL,
    guardian_last_name  VARCHAR(100)  NULL,
    guardian_first_name VARCHAR(100)  NULL,
    guardian_phone      VARCHAR(20)   NULL,
    registration_date   DATE          NOT NULL,
    expiry_date         DATE          NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    suspension_reason   VARCHAR(255)  NULL,
    archived_at         DATETIME(6)   NULL,
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version             INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_members_member_number UNIQUE (member_number),
    CONSTRAINT ck_members_member_number CHECK (member_number REGEXP '^A[0-9]{6}$'),
    CONSTRAINT ck_members_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'SUSPENDED', 'ARCHIVED')),
    CONSTRAINT ck_members_identity CHECK (
        status = 'ARCHIVED'
        OR (last_name IS NOT NULL AND first_name IS NOT NULL AND birth_date IS NOT NULL
            AND street IS NOT NULL AND city IS NOT NULL)),
    CONSTRAINT ck_members_archived CHECK ((status = 'ARCHIVED') = (archived_at IS NOT NULL)),
    CONSTRAINT ck_members_birth_year CHECK (birth_date IS NULL OR birth_year = YEAR(birth_date)),
    CONSTRAINT ck_members_suspension_reason CHECK (
        status <> 'SUSPENDED'
        OR (suspension_reason IS NOT NULL AND CHAR_LENGTH(suspension_reason) BETWEEN 3 AND 255)),
    CONSTRAINT ck_members_guardian CHECK (
        (guardian_last_name IS NULL) = (guardian_first_name IS NULL)
        AND (guardian_last_name IS NULL) = (guardian_phone IS NULL)),
    CONSTRAINT ck_members_dates CHECK (expiry_date >= registration_date),
    INDEX idx_members_last_name_first_name (last_name, first_name),
    INDEX idx_members_status_expiry_date (status, expiry_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- Comptes utilisateurs (SPEC-authentification, ENT-Compte)
-- L'e-mail d'un adhérent est porté par son compte (source unique, RG-14).
-- e-mail NULL uniquement pour un compte adhérent désactivé par archivage (RG-18).
-- -----------------------------------------------------------------------------
CREATE TABLE user_accounts (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    email               VARCHAR(254)  NULL,
    password_hash       VARCHAR(100)  NOT NULL,
    role                VARCHAR(20)   NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    last_name           VARCHAR(100)  NULL,
    first_name          VARCHAR(100)  NULL,
    member_id           BIGINT        NULL,
    password_temporary  BOOLEAN       NOT NULL DEFAULT TRUE,
    failed_login_count  INT           NOT NULL DEFAULT 0,
    locked_until        DATETIME(6)   NULL,
    last_login_at       DATETIME(6)   NULL,
    password_changed_at DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version             INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_accounts_email UNIQUE (email),
    CONSTRAINT uk_user_accounts_member_id UNIQUE (member_id),
    CONSTRAINT fk_user_accounts_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT ck_user_accounts_role CHECK (role IN ('MEMBER', 'LIBRARIAN', 'ADMIN')),
    CONSTRAINT ck_user_accounts_status CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED')),
    CONSTRAINT ck_user_accounts_member_link CHECK ((role = 'MEMBER') = (member_id IS NOT NULL)),
    CONSTRAINT ck_user_accounts_staff_identity CHECK (
        role = 'MEMBER' OR (last_name IS NOT NULL AND first_name IS NOT NULL)),
    CONSTRAINT ck_user_accounts_email CHECK (
        email IS NOT NULL OR (role = 'MEMBER' AND status = 'DISABLED')),
    CONSTRAINT ck_user_accounts_locked CHECK (status <> 'LOCKED' OR locked_until IS NOT NULL),
    CONSTRAINT ck_user_accounts_failed_login_count CHECK (failed_login_count >= 0),
    INDEX idx_user_accounts_role_status (role, status),
    INDEX idx_user_accounts_last_name_first_name (last_name, first_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Historique des empreintes de mot de passe (RG-01 : différent des 3 derniers)
CREATE TABLE password_history_entries (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    user_account_id BIGINT        NOT NULL,
    password_hash   VARCHAR(100)  NOT NULL,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_password_history_entries_user_account FOREIGN KEY (user_account_id)
        REFERENCES user_accounts (id) ON DELETE CASCADE,
    INDEX idx_password_history_entries_account_created (user_account_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- Catalogue (SPEC-catalogue)
-- -----------------------------------------------------------------------------
CREATE TABLE authors (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    last_name   VARCHAR(100)  NOT NULL,
    first_name  VARCHAR(100)  NULL,
    created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version     INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT ck_authors_last_name CHECK (CHAR_LENGTH(TRIM(last_name)) >= 1),
    INDEX idx_authors_last_name_first_name (last_name, first_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- RG-12 : libellé unique insensible à la casse et aux accents (collation), 2 à 50 caractères
CREATE TABLE categories (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    label       VARCHAR(50)   NOT NULL,
    created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version     INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_categories_label UNIQUE (label),
    CONSTRAINT ck_categories_label CHECK (CHAR_LENGTH(TRIM(label)) BETWEEN 2 AND 50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Notices (ENT-Livre). RG-07 : ISBN normalisé en ISBN-13 (clé vérifiée par le service).
-- RG-08 : borne haute de l'année (année en cours) vérifiée par le service.
CREATE TABLE books (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    isbn             CHAR(13)       NULL,
    title            VARCHAR(255)   NOT NULL,
    subtitle         VARCHAR(255)   NULL,
    publisher        VARCHAR(150)   NULL,
    publication_year SMALLINT       NULL,
    language_code    CHAR(3)        NOT NULL DEFAULT 'fre',
    page_count       INT            NULL,
    summary          VARCHAR(4000)  NULL,
    created_at       DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version          INT            NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_books_isbn UNIQUE (isbn),
    CONSTRAINT fk_books_language FOREIGN KEY (language_code) REFERENCES languages (code),
    CONSTRAINT ck_books_isbn CHECK (isbn IS NULL OR isbn REGEXP '^97[89][0-9]{10}$'),
    CONSTRAINT ck_books_title CHECK (CHAR_LENGTH(TRIM(title)) >= 1),
    CONSTRAINT ck_books_publication_year CHECK (publication_year IS NULL OR publication_year >= 1450),
    CONSTRAINT ck_books_page_count CHECK (page_count IS NULL OR page_count > 0),
    INDEX idx_books_title (title),
    INDEX idx_books_publication_year (publication_year),
    INDEX idx_books_language_code (language_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Auteurs ordonnés d'une notice (RG-08 : au moins 1, vérifié par le service)
CREATE TABLE book_authors (
    book_id      BIGINT    NOT NULL,
    author_id    BIGINT    NOT NULL,
    author_order SMALLINT  NOT NULL,
    PRIMARY KEY (book_id, author_id),
    CONSTRAINT uk_book_authors_book_order UNIQUE (book_id, author_order),
    CONSTRAINT fk_book_authors_book FOREIGN KEY (book_id) REFERENCES books (id) ON DELETE CASCADE,
    CONSTRAINT fk_book_authors_author FOREIGN KEY (author_id) REFERENCES authors (id),
    CONSTRAINT ck_book_authors_order CHECK (author_order >= 1),
    INDEX idx_book_authors_author (author_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Catégories d'une notice. RG-12 : une catégorie utilisée ne peut pas être supprimée (RESTRICT).
CREATE TABLE book_categories (
    book_id     BIGINT  NOT NULL,
    category_id BIGINT  NOT NULL,
    PRIMARY KEY (book_id, category_id),
    CONSTRAINT fk_book_categories_book FOREIGN KEY (book_id) REFERENCES books (id) ON DELETE CASCADE,
    CONSTRAINT fk_book_categories_category FOREIGN KEY (category_id) REFERENCES categories (id),
    INDEX idx_book_categories_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Exemplaires (ENT-Exemplaire). RG-09 : numéro issu de copy_number_seq.
-- RG-10 : jamais supprimés (FK RESTRICT depuis loans/reservations) ; notice non supprimable s'il en existe.
CREATE TABLE copies (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    copy_number        CHAR(8)      NOT NULL,
    book_id            BIGINT       NOT NULL,
    shelf_mark         VARCHAR(30)  NOT NULL,
    physical_condition VARCHAR(20)  NOT NULL DEFAULT 'GOOD',
    acquisition_date   DATE         NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    withdrawal_reason  VARCHAR(20)  NULL,
    withdrawn_at       DATETIME(6)  NULL,
    created_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version            INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_copies_copy_number UNIQUE (copy_number),
    CONSTRAINT fk_copies_book FOREIGN KEY (book_id) REFERENCES books (id),
    CONSTRAINT ck_copies_copy_number CHECK (copy_number REGEXP '^[0-9]{8}$'),
    CONSTRAINT ck_copies_shelf_mark CHECK (CHAR_LENGTH(TRIM(shelf_mark)) >= 1),
    CONSTRAINT ck_copies_physical_condition CHECK (physical_condition IN ('GOOD', 'WORN', 'DAMAGED')),
    CONSTRAINT ck_copies_status CHECK (status IN ('AVAILABLE', 'ON_LOAN', 'RESERVED', 'LOST', 'WITHDRAWN')),
    CONSTRAINT ck_copies_withdrawal_reason CHECK (
        withdrawal_reason IS NULL OR withdrawal_reason IN ('DAMAGED', 'LOST', 'WEEDED', 'OTHER')),
    CONSTRAINT ck_copies_withdrawal CHECK (
        (status = 'WITHDRAWN') = (withdrawal_reason IS NOT NULL)
        AND (status = 'WITHDRAWN') = (withdrawn_at IS NOT NULL)),
    INDEX idx_copies_book_status (book_id, status),
    INDEX idx_copies_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- Encaissements (RG-38 : une opération peut régler plusieurs pénalités)
-- -----------------------------------------------------------------------------
CREATE TABLE payments (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    member_id      BIGINT         NOT NULL,
    amount         DECIMAL(10,2)  NOT NULL,
    payment_method VARCHAR(10)    NOT NULL,
    collected_by   BIGINT         NOT NULL,
    collected_at   DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_payments_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_payments_collected_by FOREIGN KEY (collected_by) REFERENCES user_accounts (id),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_method CHECK (payment_method IN ('CASH', 'CARD', 'CHECK')),
    INDEX idx_payments_member (member_id),
    INDEX idx_payments_collected_at_method (collected_at, payment_method)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- Emprunts (SPEC-emprunts, ENT-Emprunt)
-- NFR-10 : au plus 1 emprunt en cours (ACTIVE/OVERDUE) par exemplaire, garanti par uk_loans_active_copy.
-- RG-19 : member_id effacé (NULL) 4 mois après le retour, uniquement pour un emprunt RETURNED.
-- -----------------------------------------------------------------------------
CREATE TABLE loans (
    id                 BIGINT            NOT NULL AUTO_INCREMENT,
    copy_id            BIGINT            NOT NULL,
    member_id          BIGINT            NULL,
    loan_date          DATE              NOT NULL,
    due_date           DATE              NOT NULL,
    return_date        DATE              NULL,
    lost_declared_date DATE              NULL,
    renewal_count      TINYINT UNSIGNED  NOT NULL DEFAULT 0,
    status             VARCHAR(20)       NOT NULL DEFAULT 'ACTIVE',
    loaned_by          BIGINT            NOT NULL,
    returned_by        BIGINT            NULL,
    member_unlinked_at DATETIME(6)       NULL,
    active_copy_id     BIGINT AS (CASE WHEN status IN ('ACTIVE', 'OVERDUE') THEN copy_id END) PERSISTENT,
    created_at         DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version            INT               NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_loans_active_copy UNIQUE (active_copy_id),
    CONSTRAINT fk_loans_copy FOREIGN KEY (copy_id) REFERENCES copies (id),
    CONSTRAINT fk_loans_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_loans_loaned_by FOREIGN KEY (loaned_by) REFERENCES user_accounts (id),
    CONSTRAINT fk_loans_returned_by FOREIGN KEY (returned_by) REFERENCES user_accounts (id),
    CONSTRAINT ck_loans_status CHECK (status IN ('ACTIVE', 'OVERDUE', 'RETURNED', 'LOST')),
    CONSTRAINT ck_loans_due_date CHECK (due_date > loan_date),
    CONSTRAINT ck_loans_returned CHECK (
        (status = 'RETURNED') = (return_date IS NOT NULL)
        AND (status = 'RETURNED') = (returned_by IS NOT NULL)),
    CONSTRAINT ck_loans_return_date CHECK (return_date IS NULL OR return_date >= loan_date),
    CONSTRAINT ck_loans_lost CHECK ((status = 'LOST') = (lost_declared_date IS NOT NULL)),
    CONSTRAINT ck_loans_renewal_count CHECK (renewal_count <= 5),
    CONSTRAINT ck_loans_member CHECK (member_id IS NOT NULL OR status = 'RETURNED'),
    CONSTRAINT ck_loans_unlinked CHECK ((member_unlinked_at IS NOT NULL) = (member_id IS NULL)),
    INDEX idx_loans_member_status (member_id, status),
    INDEX idx_loans_status_due_date (status, due_date),
    INDEX idx_loans_copy_loan_date (copy_id, loan_date),
    INDEX idx_loans_status_return_date (status, return_date),
    INDEX idx_loans_loan_date (loan_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- Réservations (SPEC-reservations, ENT-Reservation)
-- RG-31 : 1 réservation active (PENDING/READY) par adhérent et par notice.
-- RG-32 / NFR-10 : un exemplaire n'est mis de côté que pour 1 réservation READY.
-- -----------------------------------------------------------------------------
CREATE TABLE reservations (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    book_id             BIGINT       NOT NULL,
    member_id           BIGINT       NOT NULL,
    reserved_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    copy_id             BIGINT       NULL,
    ready_at            DATETIME(6)  NULL,
    pickup_deadline     DATE         NULL,
    closed_at           DATETIME(6)  NULL,
    notification_status VARCHAR(20)  NULL,
    created_by          BIGINT       NULL,
    closed_by           BIGINT       NULL,
    active_member_id    BIGINT AS (CASE WHEN status IN ('PENDING', 'READY') THEN member_id END) PERSISTENT,
    ready_copy_id       BIGINT AS (CASE WHEN status = 'READY' THEN copy_id END) PERSISTENT,
    created_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version             INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_reservations_active_member_book UNIQUE (active_member_id, book_id),
    CONSTRAINT uk_reservations_ready_copy UNIQUE (ready_copy_id),
    CONSTRAINT fk_reservations_book FOREIGN KEY (book_id) REFERENCES books (id),
    CONSTRAINT fk_reservations_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_reservations_copy FOREIGN KEY (copy_id) REFERENCES copies (id),
    CONSTRAINT fk_reservations_created_by FOREIGN KEY (created_by) REFERENCES user_accounts (id),
    CONSTRAINT fk_reservations_closed_by FOREIGN KEY (closed_by) REFERENCES user_accounts (id),
    CONSTRAINT ck_reservations_status CHECK (status IN ('PENDING', 'READY', 'FULFILLED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_reservations_pending CHECK (
        status <> 'PENDING' OR (copy_id IS NULL AND ready_at IS NULL AND pickup_deadline IS NULL)),
    CONSTRAINT ck_reservations_ready CHECK (
        status <> 'READY' OR (copy_id IS NOT NULL AND ready_at IS NOT NULL AND pickup_deadline IS NOT NULL)),
    CONSTRAINT ck_reservations_fulfilled CHECK (status <> 'FULFILLED' OR copy_id IS NOT NULL),
    CONSTRAINT ck_reservations_expired CHECK (status <> 'EXPIRED' OR copy_id IS NOT NULL),
    CONSTRAINT ck_reservations_closed CHECK (
        (status IN ('FULFILLED', 'EXPIRED', 'CANCELLED')) = (closed_at IS NOT NULL)),
    CONSTRAINT ck_reservations_notification CHECK (
        notification_status IS NULL OR notification_status IN ('SENT', 'FAILED')),
    INDEX idx_reservations_book_status_reserved_at (book_id, status, reserved_at),
    INDEX idx_reservations_member_status (member_id, status),
    INDEX idx_reservations_status_pickup_deadline (status, pickup_deadline),
    INDEX idx_reservations_status_closed_at (status, closed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- Pénalités (SPEC-penalites, ENT-Penalite)
-- uk_fines_loan_reason : au plus une pénalité de retard et une de perte par emprunt (idempotence).
-- -----------------------------------------------------------------------------
CREATE TABLE fines (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    member_id     BIGINT         NOT NULL,
    loan_id       BIGINT         NOT NULL,
    reason        VARCHAR(20)    NOT NULL,
    late_days     INT            NULL,
    amount        DECIMAL(10,2)  NOT NULL,
    status        VARCHAR(20)    NOT NULL DEFAULT 'UNPAID',
    payment_id    BIGINT         NULL,
    settled_at    DATETIME(6)    NULL,
    settled_by    BIGINT         NULL,
    waiver_reason VARCHAR(255)   NULL,
    created_at    DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version       INT            NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_fines_loan_reason UNIQUE (loan_id, reason),
    CONSTRAINT fk_fines_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_fines_loan FOREIGN KEY (loan_id) REFERENCES loans (id),
    CONSTRAINT fk_fines_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT fk_fines_settled_by FOREIGN KEY (settled_by) REFERENCES user_accounts (id),
    CONSTRAINT ck_fines_reason CHECK (reason IN ('LATE_RETURN', 'LOST_ITEM')),
    CONSTRAINT ck_fines_status CHECK (status IN ('UNPAID', 'PAID', 'WAIVED')),
    CONSTRAINT ck_fines_amount CHECK (amount > 0),
    CONSTRAINT ck_fines_late_days CHECK (
        (reason = 'LATE_RETURN') = (late_days IS NOT NULL) AND (late_days IS NULL OR late_days >= 1)),
    CONSTRAINT ck_fines_paid CHECK ((status = 'PAID') = (payment_id IS NOT NULL)),
    CONSTRAINT ck_fines_waived CHECK (
        (status = 'WAIVED') = (waiver_reason IS NOT NULL)
        AND (waiver_reason IS NULL OR CHAR_LENGTH(waiver_reason) BETWEEN 3 AND 255)),
    CONSTRAINT ck_fines_settled CHECK (
        (status = 'UNPAID') = (settled_at IS NULL) AND (status = 'UNPAID') = (settled_by IS NULL)),
    INDEX idx_fines_member_status (member_id, status),
    INDEX idx_fines_status_settled_at (status, settled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- Paramètres des règles de prêt (SPEC-pilotage, RG-40) — valeurs initiales en V2
-- -----------------------------------------------------------------------------
CREATE TABLE settings (
    code          VARCHAR(50)    NOT NULL,
    setting_group VARCHAR(20)    NOT NULL,
    value_type    VARCHAR(10)    NOT NULL,
    unit          VARCHAR(10)    NOT NULL,
    setting_value DECIMAL(10,2)  NOT NULL,
    min_value     DECIMAL(10,2)  NOT NULL,
    max_value     DECIMAL(10,2)  NOT NULL,
    display_order SMALLINT       NOT NULL,
    updated_by    BIGINT         NULL,
    created_at    DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version       INT            NOT NULL DEFAULT 0,
    PRIMARY KEY (code),
    CONSTRAINT fk_settings_updated_by FOREIGN KEY (updated_by) REFERENCES user_accounts (id),
    CONSTRAINT ck_settings_group CHECK (setting_group IN ('MEMBERSHIP', 'LOANS', 'RESERVATIONS', 'FINES')),
    CONSTRAINT ck_settings_value_type CHECK (value_type IN ('INTEGER', 'MONEY')),
    CONSTRAINT ck_settings_unit CHECK (unit IN ('MONTHS', 'DAYS', 'COUNT', 'EUR')),
    CONSTRAINT ck_settings_range CHECK (min_value <= max_value AND setting_value BETWEEN min_value AND max_value),
    CONSTRAINT ck_settings_integer CHECK (value_type <> 'INTEGER' OR setting_value = FLOOR(setting_value))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- Journal d'audit (NFR-08) — immuable : ni updated_at ni version ; purge après 12 mois.
-- actor_account_id NULL = « Système » (traitements planifiés).
-- -----------------------------------------------------------------------------
CREATE TABLE audit_entries (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    occurred_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actor_account_id BIGINT        NULL,
    actor_label      VARCHAR(150)  NOT NULL,
    action           VARCHAR(30)   NOT NULL,
    object_type      VARCHAR(30)   NOT NULL,
    object_ref       VARCHAR(60)   NOT NULL,
    details          JSON          NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_entries_actor FOREIGN KEY (actor_account_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_audit_entries_action CHECK (action IN (
        'CREATE', 'UPDATE', 'STATE_CHANGE', 'DELETE', 'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'LOGOUT')),
    CONSTRAINT ck_audit_entries_object_type CHECK (object_type IN (
        'ACCOUNT', 'MEMBER', 'BOOK', 'AUTHOR', 'CATEGORY', 'COPY', 'LOAN', 'RESERVATION',
        'FINE', 'PAYMENT', 'SETTING')),
    INDEX idx_audit_entries_occurred_at (occurred_at),
    INDEX idx_audit_entries_object (object_type, object_ref, occurred_at),
    INDEX idx_audit_entries_actor (actor_account_id, occurred_at),
    INDEX idx_audit_entries_action (action, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- Sessions HTTP persistées (ADR-004 : Spring Session JDBC).
-- Noms en MAJUSCULES imposés par Spring Session (exception documentée à la convention snake_case).
-- -----------------------------------------------------------------------------
CREATE TABLE SPRING_SESSION (
    PRIMARY_ID            CHAR(36)      NOT NULL,
    SESSION_ID            CHAR(36)      NOT NULL,
    CREATION_TIME         BIGINT        NOT NULL,
    LAST_ACCESS_TIME      BIGINT        NOT NULL,
    MAX_INACTIVE_INTERVAL INT           NOT NULL,
    EXPIRY_TIME           BIGINT        NOT NULL,
    PRINCIPAL_NAME        VARCHAR(100)  NULL,
    PRIMARY KEY (PRIMARY_ID)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36)      NOT NULL,
    ATTRIBUTE_NAME     VARCHAR(200)  NOT NULL,
    ATTRIBUTE_BYTES    BLOB          NOT NULL,
    PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
