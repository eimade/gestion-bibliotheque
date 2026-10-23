package com.bibliotheque.member.web;

import static com.bibliotheque.support.SecurityRequests.authenticated;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.common.security.AccountPrincipal;
import com.bibliotheque.common.security.SecurityConfig;
import com.bibliotheque.common.security.SecuritySessionManager;
import com.bibliotheque.member.domain.MemberStatus;
import com.bibliotheque.member.service.MemberCirculation.Eligibility;
import com.bibliotheque.member.service.MemberService;
import com.bibliotheque.member.service.MemberSpaceService;
import com.bibliotheque.member.service.MemberViews.ArchiveCheck;
import com.bibliotheque.member.service.MemberViews.MemberDetail;
import com.bibliotheque.member.service.MemberViews.MemberSummary;
import com.bibliotheque.member.service.MemberViews.MyMember;
import com.bibliotheque.member.service.MemberViews.RenewalView;

@WebMvcTest(controllers = {MemberController.class, MemberSpaceController.class})
@Import({SecurityConfig.class, SecuritySessionManager.class})
class MemberControllerTest {

    private static final AccountPrincipal LIBRARIAN = new AccountPrincipal(2L, "LIBRARIAN", false);
    private static final AccountPrincipal MEMBER = new AccountPrincipal(50L, "MEMBER", false);
    private static final MemberDetail DETAIL = new MemberDetail("A000124", "Martin", "Jean", LocalDate.of(1990, 3, 12),
            "jean.martin@exemple.fr", null, "12 rue des Lilas", "75011", "Paris", null, LocalDate.of(2026, 10, 17),
            LocalDate.of(2027, 10, 17), MemberStatus.ACTIVE, null, 50L, null, 0,
            new Eligibility(true, List.of(), 0, 5, 5, BigDecimal.ZERO, new BigDecimal("5.00")),
            new RenewalView(false, LocalDate.of(2028, 10, 17), LocalDate.of(2027, 9, 17), "RENEWAL_TOO_EARLY"), false, 0);
    private static final String CREATE_BODY = """
            {"lastName":"Martin","firstName":"Jean","birthDate":"1990-03-12","email":"jean.martin@exemple.fr",
             "phone":"06 12 34 56 78","street":"12 rue des Lilas","postalCode":"75011","city":"Paris",
             "password":"MotDePasse2026","confirmDuplicate":false}""";

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private MemberService members;
    @MockitoBean
    private MemberSpaceService space;

    @Test
    void personnelSeulementPourLaGestion() throws Exception {
        mvc.perform(get("/api/v1/members")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/members").with(authenticated(MEMBER))).andExpect(status().isForbidden());
    }

    @Test
    void rechercheAvecTriParDefautSurLeNom() throws Exception {
        when(members.search(eq("mart"), eq(null), any())).thenReturn(new PageImpl<>(List.of(new MemberSummary(
                "A000123", "Martin", "Jean", LocalDate.of(1990, 3, 12), MemberStatus.ACTIVE,
                LocalDate.of(2027, 10, 17)))));

        mvc.perform(get("/api/v1/members").param("q", "mart").with(authenticated(LIBRARIAN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].memberNumber").value("A000123"));
        mvc.perform(get("/api/v1/members").param("q", "m").with(authenticated(LIBRARIAN)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inscriptionRenvoie201AvecLocationEtTelephoneCompacte() throws Exception {
        when(members.register(any(), eq("MotDePasse2026"), eq(false), eq(2L))).thenReturn(DETAIL);

        mvc.perform(post("/api/v1/members").with(authenticated(LIBRARIAN)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/members/A000124"))
                .andExpect(jsonPath("$.eligibility.eligible").value(true));
        verify(members).register(org.mockito.ArgumentMatchers.argThat(command ->
                "0612345678".equals(command.contact().phone())), anyString(), eq(false), anyLong());
    }

    @Test
    void inscriptionInvalideSignaleLesChamps() throws Exception {
        String invalid = CREATE_BODY.replace("\"75011\"", "\"750\"").replace("\"06 12 34 56 78\"", "\"12\"");

        mvc.perform(post("/api/v1/members").with(authenticated(LIBRARIAN)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }

    @Test
    void homonymeRenvoie409AvecLAdherentExistant() throws Exception {
        when(members.register(any(), anyString(), eq(false), anyLong())).thenThrow(BusinessException.of(
                ErrorCode.DUPLICATE_MEMBER_SUSPECTED, "Un adhérent existe déjà.").withProperty("existingMember",
                java.util.Map.of("memberNumber", "A000123")));

        mvc.perform(post("/api/v1/members").with(authenticated(LIBRARIAN)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.existingMember.memberNumber").value("A000123"));
    }

    @Test
    void actionsSurLaFiche() throws Exception {
        when(members.get("A000124")).thenReturn(DETAIL);
        when(members.renew("A000124", 2L)).thenReturn(DETAIL);
        when(members.suspend("A000124", "Litige", 2L)).thenReturn(DETAIL);
        when(members.reactivate("A000124", 2L)).thenReturn(DETAIL);
        when(members.archive("A000124", 2L)).thenReturn(DETAIL);
        when(members.archiveCheck("A000124")).thenReturn(new ArchiveCheck(true, List.of(), 0, 0, BigDecimal.ZERO));
        when(members.toArchive(any())).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/members/A000124").with(authenticated(LIBRARIAN))).andExpect(status().isOk())
                .andExpect(jsonPath("$.renewal.blockedBy").value("RENEWAL_TOO_EARLY"));
        mvc.perform(post("/api/v1/members/A000124/renewal").with(authenticated(LIBRARIAN)).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/members/A000124/suspension").with(authenticated(LIBRARIAN)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Litige\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/members/A000124/suspension").with(authenticated(LIBRARIAN)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"ab\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/members/A000124/reactivation").with(authenticated(LIBRARIAN)).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/members/A000124/archive-check").with(authenticated(LIBRARIAN)))
                .andExpect(jsonPath("$.archivable").value(true));
        mvc.perform(post("/api/v1/members/A000124/archive").with(authenticated(LIBRARIAN)).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/members/to-archive").with(authenticated(LIBRARIAN))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/members/B12").with(authenticated(LIBRARIAN))).andExpect(status().isNotFound());
    }

    @Test
    void modificationTransmetLaVersion() throws Exception {
        when(members.update(eq("A000124"), any(), eq(4), eq(2L))).thenReturn(DETAIL);
        String body = CREATE_BODY.replace("\"password\":\"MotDePasse2026\",\"confirmDuplicate\":false", "\"version\":4");

        mvc.perform(put("/api/v1/members/A000124").with(authenticated(LIBRARIAN)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void espaceAdherentReserveAuxAdherents() throws Exception {
        MyMember me = new MyMember("A000124", "Martin", "Jean", LocalDate.of(1990, 3, 12), "jean.martin@exemple.fr",
                null, "12 rue des Lilas", "75011", "Paris", MemberStatus.ACTIVE, LocalDate.of(2027, 10, 17), 0);
        when(space.me(50L)).thenReturn(me);
        when(space.updateContact(eq(50L), any(), eq(0))).thenReturn(me);

        mvc.perform(get("/api/v1/me/member").with(authenticated(MEMBER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.memberNumber").value("A000124"));
        mvc.perform(get("/api/v1/me/member").with(authenticated(LIBRARIAN))).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/me/contact").with(authenticated(MEMBER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"street\":\"1 rue A\",\"postalCode\":\"75011\",\"city\":\"Paris\",\"version\":0}"))
                .andExpect(status().isOk());
    }

    @Test
    void lAdherentNePeutPasModifierSonNomNiUnCodePostalInvalide() throws Exception {
        mvc.perform(put("/api/v1/me/contact").with(authenticated(MEMBER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"street":"1 rue A","postalCode":"75011","city":"Paris","version":0,"lastName":"X"}"""))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/me/contact").with(authenticated(MEMBER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"street\":\"1 rue A\",\"postalCode\":\"750\",\"city\":\"Paris\",\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Le code postal doit contenir 5 chiffres."));
    }
}
