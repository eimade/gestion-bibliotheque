package com.bibliotheque.auth.web;

import static com.bibliotheque.support.SecurityRequests.authenticated;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bibliotheque.auth.service.AccountPasswordResetService;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.common.security.AccountPrincipal;
import com.bibliotheque.common.security.SecurityConfig;
import com.bibliotheque.common.security.SecuritySessionManager;

@WebMvcTest(controllers = AccountController.class)
@Import({SecurityConfig.class, SecuritySessionManager.class})
class AccountControllerTest {

    private static final AccountPrincipal LIBRARIAN = new AccountPrincipal(2L, "LIBRARIAN", false);
    private static final String BODY = "{\"newPassword\":\"Temporaire2026x\"}";

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private AccountPasswordResetService passwordResetService;

    @Test
    void unBibliothecaireReinitialise() throws Exception {
        mvc.perform(post("/api/v1/accounts/9/password-reset").with(csrf()).with(authenticated(LIBRARIAN))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNoContent());

        verify(passwordResetService).reset(9L, "Temporaire2026x", LIBRARIAN);
    }

    @Test
    void unAdherentNEstPasAutorise() throws Exception {
        mvc.perform(post("/api/v1/accounts/9/password-reset").with(csrf())
                        .with(authenticated(new AccountPrincipal(9L, "MEMBER", false)))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void refusMetierTraduit() throws Exception {
        doThrow(BusinessException.of(ErrorCode.ACCESS_DENIED, "Seul un administrateur…"))
                .when(passwordResetService).reset(eq(3L), any(), eq(LIBRARIAN));

        mvc.perform(post("/api/v1/accounts/3/password-reset").with(csrf()).with(authenticated(LIBRARIAN))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
