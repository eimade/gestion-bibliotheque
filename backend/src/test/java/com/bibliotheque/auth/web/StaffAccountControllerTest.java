package com.bibliotheque.auth.web;

import static com.bibliotheque.support.SecurityRequests.authenticated;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bibliotheque.auth.domain.AccountRole;
import com.bibliotheque.auth.domain.AccountStatus;
import com.bibliotheque.auth.domain.UserAccount;
import com.bibliotheque.auth.service.StaffAccountService;
import com.bibliotheque.auth.service.StaffAccountView;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.common.security.AccountPrincipal;
import com.bibliotheque.common.security.SecurityConfig;
import com.bibliotheque.common.security.SecuritySessionManager;

@WebMvcTest(controllers = StaffAccountController.class)
@Import({SecurityConfig.class, SecuritySessionManager.class})
class StaffAccountControllerTest {

    private static final AccountPrincipal ADMIN = new AccountPrincipal(1L, "ADMIN", false);
    private static final StaffAccountView LIBRARIAN_VIEW = new StaffAccountView(5L, "Dupont", "Marie",
            "marie.dupont@exemple.fr", AccountRole.LIBRARIAN, AccountStatus.ACTIVE, false, 0);
    private static final String CREATE_BODY = """
            {"lastName":"Dupont","firstName":"Marie","email":"marie.dupont@exemple.fr",
             "role":"LIBRARIAN","initialPassword":"Initial2026Secret"}""";

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private StaffAccountService staffAccountService;

    @Test
    void anonymeNonAuthentifie() throws Exception {
        mvc.perform(get("/api/v1/staff-accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void bibliothecaireNonAutorise() throws Exception {
        mvc.perform(get("/api/v1/staff-accounts").with(authenticated(new AccountPrincipal(2L, "LIBRARIAN", false))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void listePagineeTrieeParNomParDefaut() throws Exception {
        Pageable expected = PageRequest.of(0, 20, org.springframework.data.domain.Sort.by("lastName")
                .and(org.springframework.data.domain.Sort.by("firstName")));
        when(staffAccountService.search(isNull(), isNull(), isNull(), eq(expected), eq(1L)))
                .thenReturn(new PageImpl<>(List.of(LIBRARIAN_VIEW), expected, 1));

        mvc.perform(get("/api/v1/staff-accounts").with(authenticated(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("marie.dupont@exemple.fr"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void rechercheTropCourteRefusee() throws Exception {
        mvc.perform(get("/api/v1/staff-accounts").param("q", "a").with(authenticated(ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void roleInconnuRefuse() throws Exception {
        mvc.perform(get("/api/v1/staff-accounts").param("role", "PIRATE").with(authenticated(ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void creationRenvoie201AvecLocation() throws Exception {
        when(staffAccountService.create(any(), eq(1L))).thenReturn(LIBRARIAN_VIEW);

        mvc.perform(post("/api/v1/staff-accounts").with(csrf()).with(authenticated(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/staff-accounts/5"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void creationAvecChampsManquants() throws Exception {
        mvc.perform(post("/api/v1/staff-accounts").with(csrf()).with(authenticated(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"x@exemple.fr\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void emailEnDoubleEnConcurrenceTraduitParLaContrainte() throws Exception {
        when(staffAccountService.create(any(), eq(1L))).thenThrow(new DataIntegrityViolationException("doublon",
                new SQLIntegrityConstraintViolationException(
                        "Duplicate entry 'x' for key 'uk_user_accounts_email'")));

        mvc.perform(post("/api/v1/staff-accounts").with(csrf()).with(authenticated(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
    }

    @Test
    void desactivationDuDernierAdministrateur() throws Exception {
        when(staffAccountService.deactivate(2L, 1L)).thenThrow(
                BusinessException.of(ErrorCode.LAST_ADMIN_REQUIRED, "Au moins un administrateur actif est requis."));

        mvc.perform(post("/api/v1/staff-accounts/2/deactivation").with(csrf()).with(authenticated(ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN_REQUIRED"));
    }

    @Test
    void modificationConcurrente() throws Exception {
        when(staffAccountService.reactivate(5L, 1L))
                .thenThrow(new ObjectOptimisticLockingFailureException(UserAccount.class, 5L));

        mvc.perform(post("/api/v1/staff-accounts/5/reactivation").with(csrf()).with(authenticated(ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void lectureDUnCompte() throws Exception {
        when(staffAccountService.get(5L, 1L)).thenReturn(LIBRARIAN_VIEW);

        mvc.perform(get("/api/v1/staff-accounts/5").with(authenticated(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void erreurInattendueSansDetailTechnique() throws Exception {
        when(staffAccountService.get(5L, 1L)).thenThrow(new IllegalStateException("secret interne"));

        mvc.perform(get("/api/v1/staff-accounts/5").with(authenticated(ADMIN)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Une erreur inattendue est survenue."));
    }
}
