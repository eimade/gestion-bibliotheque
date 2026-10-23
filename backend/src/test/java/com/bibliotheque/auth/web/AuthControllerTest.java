package com.bibliotheque.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.bibliotheque.support.SecurityRequests.authenticated;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.bibliotheque.auth.domain.AccountRole;
import com.bibliotheque.auth.service.AuthenticationService;
import com.bibliotheque.auth.service.LoginOutcome;
import com.bibliotheque.auth.service.PasswordService;
import com.bibliotheque.auth.service.SessionUser;
import com.bibliotheque.auth.service.SessionUserService;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.common.security.AccountPrincipal;
import com.bibliotheque.common.security.SecurityConfig;
import com.bibliotheque.common.security.SecuritySessionManager;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, SecuritySessionManager.class})
class AuthControllerTest {

    private static final SessionUser ADMIN = new SessionUser(1L, "admin@exemple.fr", AccountRole.ADMIN,
            "LEROY", "Anne", false, null);

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private AuthenticationService authenticationService;
    @MockitoBean
    private SessionUserService sessionUserService;
    @MockitoBean
    private PasswordService passwordService;

    @Test
    void connexionReussieOuvreUneSession() throws Exception {
        when(authenticationService.login("admin@exemple.fr", "Admin2026Secret"))
                .thenReturn(new LoginOutcome.Authenticated(ADMIN));

        MvcResult result = mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@exemple.fr\",\"password\":\"Admin2026Secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(1))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.passwordTemporary").value(false))
                .andReturn();

        assertThat(principalInSession((MockHttpSession) result.getRequest().getSession()).accountId()).isEqualTo(1L);
    }

    @Test
    void connexionSansJetonCsrfEstRefusee() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@exemple.fr\",\"password\":\"x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
    }

    @Test
    void identifiantsIncorrects() throws Exception {
        when(authenticationService.login(anyString(), anyString()))
                .thenReturn(new LoginOutcome.Rejected(ErrorCode.INVALID_CREDENTIALS, null));

        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@exemple.fr\",\"password\":\"faux\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Identifiants incorrects."));
    }

    @Test
    void compteVerrouilleIndiqueLHeureDeFin() throws Exception {
        when(authenticationService.login(anyString(), anyString())).thenReturn(
                new LoginOutcome.Rejected(ErrorCode.ACCOUNT_LOCKED, Instant.parse("2026-10-17T10:15:00Z")));

        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@exemple.fr\",\"password\":\"faux\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.lockedUntil").value("2026-10-17T10:15:00Z"));
    }

    @Test
    void compteDesactive() throws Exception {
        when(authenticationService.login(anyString(), anyString()))
                .thenReturn(new LoginOutcome.Rejected(ErrorCode.ACCOUNT_DISABLED, null));

        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@exemple.fr\",\"password\":\"Admin2026Secret\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void emailInvalideRefuseAvecLesErreursDeChamp() throws Exception {
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pas-un-email\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }

    @Test
    void corpsIllisibleRefuse() throws Exception {
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void sessionCouranteSansConnexion() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void sessionCouranteConnecte() throws Exception {
        when(sessionUserService.current(1L)).thenReturn(ADMIN);

        mvc.perform(get("/api/v1/auth/me").with(authenticated(new AccountPrincipal(1L, "ADMIN", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@exemple.fr"));
    }

    @Test
    void motDePasseTemporaireBloqueLesAutresRequetes() throws Exception {
        mvc.perform(get("/api/v1/staff-accounts").with(authenticated(new AccountPrincipal(1L, "ADMIN", true))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void motDePasseTemporaireAutoriseLaLectureDeLaSession() throws Exception {
        when(sessionUserService.current(1L)).thenReturn(ADMIN);

        mvc.perform(get("/api/v1/auth/me").with(authenticated(new AccountPrincipal(1L, "ADMIN", true))))
                .andExpect(status().isOk());
    }

    @Test
    void changementDeMotDePasseMetFinAuCaractereTemporaire() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/password").with(csrf())
                        .with(authenticated(new AccountPrincipal(1L, "ADMIN", true)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Ancien2026Secret\",\"newPassword\":\"Nouveau2026Secret\"}"))
                .andExpect(status().isNoContent())
                .andReturn();

        verify(passwordService).changeOwnPassword(1L, "Ancien2026Secret", "Nouveau2026Secret");
        assertThat(principalInSession((MockHttpSession) result.getRequest().getSession()).passwordTemporary())
                .isFalse();
    }

    @Test
    void changementDeMotDePasseRefuseParLaPolitique() throws Exception {
        doThrow(BusinessException.of(ErrorCode.PASSWORD_POLICY_VIOLATION, "Non conforme.")
                .withProperty("field", "newPassword"))
                .when(passwordService).changeOwnPassword(1L, "Ancien2026Secret", "court");

        mvc.perform(post("/api/v1/auth/password").with(csrf())
                        .with(authenticated(new AccountPrincipal(1L, "ADMIN", false)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Ancien2026Secret\",\"newPassword\":\"court\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PASSWORD_POLICY_VIOLATION"))
                .andExpect(jsonPath("$.field").value("newPassword"));
    }

    @Test
    void deconnexionInvalideLaSession() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/api/v1/auth/logout").session(session).with(csrf())
                        .with(authenticated(new AccountPrincipal(1L, "ADMIN", false))))
                .andExpect(status().isNoContent());

        verify(authenticationService).recordLogout(1L);
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void cheminInconnuDansUneFamilleAutoriseeDonne404() throws Exception {
        mvc.perform(get("/api/v1/auth/inconnu").with(authenticated(new AccountPrincipal(1L, "ADMIN", false))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROUTE_NOT_FOUND"));
    }


    private static AccountPrincipal principalInSession(MockHttpSession session) {
        SecurityContext context = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        return (AccountPrincipal) context.getAuthentication().getPrincipal();
    }
}
