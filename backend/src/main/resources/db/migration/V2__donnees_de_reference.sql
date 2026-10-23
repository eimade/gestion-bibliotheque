-- =============================================================================
-- V2__donnees_de_reference.sql — Données de référence initiales
-- Langues (Q-22) et valeurs par défaut des paramètres (RG-40, bornes incluses).
-- Aucun compte n'est créé ici : l'administrateur initial est créé au démarrage
-- à partir des variables d'environnement APP_BOOTSTRAP_ADMIN_* (ARCH-socle §7.3).
-- =============================================================================

INSERT INTO languages (code, label, display_order) VALUES
    ('fre', 'Français',  1),
    ('eng', 'Anglais',   2),
    ('spa', 'Espagnol',  3),
    ('ger', 'Allemand',  4),
    ('ita', 'Italien',   5),
    ('por', 'Portugais', 6),
    ('ara', 'Arabe',     7),
    ('chi', 'Chinois',   8),
    ('und', 'Autre',     9);

-- Valeurs par défaut issues des SPEC (Q-06, Q-09, Q-13, Q-15, Q-16)
INSERT INTO settings (code, setting_group, value_type, unit, setting_value, min_value, max_value, display_order) VALUES
    ('MEMBERSHIP_DURATION_MONTHS',    'MEMBERSHIP',   'INTEGER', 'MONTHS', 12.00, 1.00,  36.00,  1),  -- RG-15
    ('MEMBERSHIP_EARLY_RENEWAL_DAYS', 'MEMBERSHIP',   'INTEGER', 'DAYS',   30.00, 0.00,  90.00,  2),  -- RG-15
    ('LOAN_DURATION_DAYS',            'LOANS',        'INTEGER', 'DAYS',   21.00, 1.00,  90.00,  3),  -- RG-21
    ('LOAN_MAX_ACTIVE',               'LOANS',        'INTEGER', 'COUNT',   5.00, 1.00,  50.00,  4),  -- RG-22
    ('LOAN_MAX_RENEWALS',             'LOANS',        'INTEGER', 'COUNT',   1.00, 0.00,   5.00,  5),  -- RG-25
    ('LOAN_RENEWAL_DAYS',             'LOANS',        'INTEGER', 'DAYS',   21.00, 1.00,  90.00,  6),  -- RG-25
    ('RESERVATION_MAX_ACTIVE',        'RESERVATIONS', 'INTEGER', 'COUNT',   3.00, 0.00,  20.00,  7),  -- RG-30
    ('RESERVATION_PICKUP_DAYS',       'RESERVATIONS', 'INTEGER', 'DAYS',    3.00, 1.00,  15.00,  8),  -- RG-33
    ('FINE_BLOCKING_THRESHOLD',       'FINES',        'MONEY',   'EUR',     5.00, 0.00, 100.00,  9),  -- RG-35
    ('FINE_DAILY_RATE',               'FINES',        'MONEY',   'EUR',     0.20, 0.00,   5.00, 10),  -- RG-36
    ('FINE_LATE_CAP',                 'FINES',        'MONEY',   'EUR',    10.00, 0.00, 100.00, 11),  -- RG-36
    ('FINE_LOST_ITEM_AMOUNT',         'FINES',        'MONEY',   'EUR',    20.00, 0.00, 500.00, 12);  -- RG-37
