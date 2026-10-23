package com.bibliotheque.auth.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.bibliotheque.auth.domain.AccountRole;
import com.bibliotheque.auth.domain.AccountStatus;
import com.bibliotheque.auth.domain.UserAccount;
import com.bibliotheque.auth.repository.UserAccountRepository;
import com.bibliotheque.common.audit.AuditAction;
import com.bibliotheque.common.audit.AuditObjectType;
import com.bibliotheque.common.audit.AuditService;
import com.bibliotheque.common.config.AppProperties;
import com.bibliotheque.common.config.BusinessTime;

/**
 * Crée l'administrateur initial au démarrage s'il n'existe aucun administrateur non désactivé
 * (ADR-004 §10). Le mot de passe fourni est temporaire (RG-05) ; un mot de passe non conforme à
 * RG-01 arrête le démarrage.
 */
@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final UserAccountRepository accounts;
    private final PasswordService passwords;
    private final PasswordPolicy policy;
    private final AuditService audit;
    private final BusinessTime time;
    private final AppProperties.BootstrapAdmin bootstrapAdmin;

    public BootstrapAdminInitializer(UserAccountRepository accounts, PasswordService passwords,
                                     PasswordPolicy policy, AuditService audit, BusinessTime time,
                                     AppProperties properties) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.policy = policy;
        this.audit = audit;
        this.time = time;
        this.bootstrapAdmin = properties.bootstrapAdmin();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (accounts.existsByRoleAndStatusNot(AccountRole.ADMIN, AccountStatus.DISABLED)) {
            return;
        }
        String email = AuthenticationService.normalize(bootstrapAdmin.email());
        if (email == null || email.isBlank() || bootstrapAdmin.password() == null
                || bootstrapAdmin.password().isBlank()) {
            LOG.warn("Aucun administrateur actif et APP_BOOTSTRAP_ADMIN_EMAIL/PASSWORD non renseignés : "
                    + "aucun administrateur initial créé");
            return;
        }
        if (accounts.existsByEmail(email)) {
            LOG.warn("Administrateur initial non créé : l'e-mail configuré est déjà utilisé par un autre compte");
            return;
        }
        if (!policy.isCompliant(bootstrapAdmin.password())) {
            throw new IllegalStateException("APP_BOOTSTRAP_ADMIN_PASSWORD ne respecte pas la politique de mot de "
                    + "passe (RG-01 : au moins 12 caractères, une majuscule, une minuscule et un chiffre)");
        }
        UserAccount admin = accounts.save(UserAccount.staff(email, "ADMINISTRATEUR", "Initial", AccountRole.ADMIN,
                passwords.hashNewPassword(bootstrapAdmin.password(), "password"), time.now()));
        passwords.recordCurrentInHistory(admin);
        audit.record(null, AuditService.SYSTEM_LABEL, AuditAction.CREATE, AuditObjectType.ACCOUNT,
                String.valueOf(admin.getId()), Map.of("email", email, "role", AccountRole.ADMIN.name(),
                        "origin", "bootstrap"));
        LOG.info("Administrateur initial créé (identifiant {}) avec un mot de passe temporaire", admin.getId());
    }
}
