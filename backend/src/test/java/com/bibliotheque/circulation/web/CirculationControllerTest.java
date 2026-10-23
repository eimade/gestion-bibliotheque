package com.bibliotheque.circulation.web;

import static com.bibliotheque.support.SecurityRequests.authenticated;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.bibliotheque.circulation.domain.FineStatus;
import com.bibliotheque.circulation.domain.PaymentMethod;
import com.bibliotheque.circulation.domain.ReservationStatus;
import com.bibliotheque.circulation.service.CirculationReadService;
import com.bibliotheque.circulation.service.CirculationReadService.OverdueSort;
import com.bibliotheque.circulation.service.CirculationViews.CopyRelease;
import com.bibliotheque.circulation.service.CirculationViews.DebtorList;
import com.bibliotheque.circulation.service.CirculationViews.Destination;
import com.bibliotheque.circulation.service.CirculationViews.LoanResult;
import com.bibliotheque.circulation.service.CirculationViews.LossAmounts;
import com.bibliotheque.circulation.service.CirculationViews.MemberFines;
import com.bibliotheque.circulation.service.CirculationViews.PaymentResult;
import com.bibliotheque.circulation.service.CirculationViews.ReservationCancelled;
import com.bibliotheque.circulation.service.CirculationViews.ReservationCreated;
import com.bibliotheque.circulation.service.CirculationViews.ReturnResult;
import com.bibliotheque.circulation.service.CirculationViews.WaiverResult;
import com.bibliotheque.circulation.service.FineService;
import com.bibliotheque.circulation.service.FineService.DebtorSort;
import com.bibliotheque.circulation.service.LoanService;
import com.bibliotheque.circulation.service.ReservationService;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.common.security.AccountPrincipal;
import com.bibliotheque.common.security.SecurityConfig;
import com.bibliotheque.common.security.SecuritySessionManager;
import com.bibliotheque.common.web.PageResponse;
import com.bibliotheque.member.MemberFixtures;
import com.bibliotheque.member.domain.MemberStatus;
import com.bibliotheque.member.service.MemberLookup;

@WebMvcTest(controllers = {LoanController.class, ReservationController.class, FineController.class,
        MemberCirculationController.class, MySpaceCirculationController.class})
@Import({SecurityConfig.class, SecuritySessionManager.class})
class CirculationControllerTest {

    private static final AccountPrincipal LIBRARIAN = new AccountPrincipal(2L, "LIBRARIAN", false);
    private static final AccountPrincipal ADMIN = new AccountPrincipal(1L, "ADMIN", false);
    private static final AccountPrincipal MEMBER = new AccountPrincipal(50L, "MEMBER", false);

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private LoanService loans;
    @MockitoBean
    private CirculationReadService reads;
    @MockitoBean
    private ReservationService reservations;
    @MockitoBean
    private FineService fines;
    @MockitoBean
    private MemberLookup members;

    @Test
    void guichetReserveAuPersonnel() throws Exception {
        mvc.perform(post("/api/v1/loans").with(authenticated(MEMBER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberNumber\":\"A000001\",\"copyNumber\":\"00000010\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void empruntEtRetour() throws Exception {
        when(loans.borrow("A000001", "00000010", 2L)).thenReturn(new LoanResult(9L, "00000010", "L'Étranger",
                LocalDate.of(2026, 11, 7), false, MemberFixtures.eligible()));
        when(loans.giveBack("00000010", 2L)).thenReturn(new ReturnResult("00000010", "L'Étranger", "R CAM", null, 0,
                null, Destination.SHELF, null));

        mvc.perform(post("/api/v1/loans").with(authenticated(LIBRARIAN)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberNumber\":\"A000001\",\"copyNumber\":\"00000010\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dueDate").value("2026-11-07"));
        mvc.perform(post("/api/v1/returns").with(authenticated(LIBRARIAN)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"copyNumber\":\"00000010\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destination").value("SHELF"));
    }

    @Test
    void numerosInvalidesAuGuichet() throws Exception {
        mvc.perform(post("/api/v1/loans").with(authenticated(LIBRARIAN)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberNumber\":\"123\",\"copyNumber\":\"10\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }

    @Test
    void refusMetierAvecMotifs() throws Exception {
        when(loans.borrow(any(), any(), eq(2L))).thenThrow(BusinessException.of(ErrorCode.MEMBER_NOT_ELIGIBLE,
                "Emprunt impossible.").withReasons(List.of("OVERDUE_LOAN")));

        mvc.perform(post("/api/v1/loans").with(authenticated(LIBRARIAN)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberNumber\":\"A000001\",\"copyNumber\":\"00000010\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reasons[0]").value("OVERDUE_LOAN"));
    }

    @Test
    void suiviDesEmprunts() throws Exception {
        when(reads.overdue(eq(7), eq(OverdueSort.MEMBER_NAME), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of()));
        when(loans.lossPreview(9L)).thenReturn(new LossAmounts(4, new BigDecimal("0.80"), new BigDecimal("20.00"),
                new BigDecimal("20.80")));
        when(loans.declareLost(9L, 2L)).thenReturn(new LossAmounts(4, new BigDecimal("0.80"),
                new BigDecimal("20.00"), new BigDecimal("20.80")));
        when(loans.markFound("00000010", 2L)).thenReturn(new CopyRelease("00000010", "R CAM", Destination.SHELF, null));
        when(reads.copyHistory(eq("00000010"), any())).thenReturn(new PageImpl<>(List.of()));
        when(loans.renew(9L, null, 2L)).thenReturn(null);

        mvc.perform(get("/api/v1/loans/overdue").param("minDays", "7").param("sort", "memberName")
                .with(authenticated(LIBRARIAN))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/loans/overdue").param("sort", "titre").with(authenticated(LIBRARIAN)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/loans/9/loss-preview").with(authenticated(LIBRARIAN)))
                .andExpect(jsonPath("$.total").value(20.80));
        mvc.perform(post("/api/v1/loans/9/loss").with(authenticated(LIBRARIAN)).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/copies/00000010/found").with(authenticated(LIBRARIAN)).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/copies/00000010/loans").with(authenticated(LIBRARIAN))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/loans/9/renewal").with(authenticated(LIBRARIAN)).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void reservationsCotePersonnel() throws Exception {
        when(members.require("A000001")).thenReturn(MemberFixtures.ref(1L, MemberStatus.ACTIVE));
        when(reservations.create(7L, "A000001", 2L)).thenReturn(new ReservationCreated(3L, ReservationStatus.PENDING, 2,
                2));
        when(reservations.cancel(3L, null, 2L)).thenReturn(new ReservationCancelled(3L, ReservationStatus.CANCELLED,
                null));
        when(reservations.queue(7L)).thenReturn(List.of());
        when(reservations.ready(any())).thenReturn(new PageImpl<>(List.of()));
        when(reservations.released(any())).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/reservations/preview").param("bookId", "7").param("memberNumber", "A000001")
                .with(authenticated(LIBRARIAN))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/reservations").with(authenticated(LIBRARIAN)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bookId\":7,\"memberNumber\":\"A000001\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.position").value(2));
        mvc.perform(post("/api/v1/reservations/3/cancellation").with(authenticated(LIBRARIAN)).with(csrf()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(get("/api/v1/books/7/reservations").with(authenticated(LIBRARIAN))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/reservations/ready").with(authenticated(LIBRARIAN))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/reservations/released").with(authenticated(LIBRARIAN))).andExpect(status().isOk());
    }

    @Test
    void encaissementEtRemiseReserveeALAdministrateur() throws Exception {
        when(fines.collect(eq("A000001"), eq(List.of(11L)), eq(PaymentMethod.CARD), any(), eq(2L)))
                .thenReturn(new PaymentResult(5L, new BigDecimal("1.00"), PaymentMethod.CARD, List.of(11L),
                        BigDecimal.ZERO, true));
        when(fines.waive(11L, "Erreur de saisie", 1L)).thenReturn(new WaiverResult(11L, FineStatus.WAIVED,
                BigDecimal.ZERO, true));
        String waiver = "{\"reason\":\"Erreur de saisie\"}";

        mvc.perform(post("/api/v1/payments").with(authenticated(LIBRARIAN)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberNumber\":\"A000001\",\"fineIds\":[11],\"method\":\"CARD\",\"amount\":1.00}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/payments").with(authenticated(LIBRARIAN)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberNumber\":\"A000001\",\"fineIds\":[],\"amount\":1.00}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/fines/11/waiver").with(authenticated(LIBRARIAN)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(waiver))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/fines/11/waiver").with(authenticated(ADMIN)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(waiver))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WAIVED"));
    }

    @Test
    void debiteursEtOngletsDeLaFiche() throws Exception {
        when(fines.debtors(DebtorSort.NAME, 0, 20)).thenReturn(new DebtorList(0, BigDecimal.ZERO, new BigDecimal("5.00"),
                new PageResponse<>(List.of(), 0, 20, 0, 0)));
        when(members.require("A000001")).thenReturn(MemberFixtures.ref(1L, MemberStatus.ACTIVE));
        when(reads.memberLoans(1L)).thenReturn(List.of());
        when(reservations.memberReservations(1L)).thenReturn(List.of());
        when(fines.unpaid(1L)).thenReturn(new MemberFines(List.of(), BigDecimal.ZERO));
        when(reads.history(eq(1L), any())).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/fines/debtors").param("sort", "name").with(authenticated(LIBRARIAN)))
                .andExpect(jsonPath("$.threshold").value(5.00));
        mvc.perform(get("/api/v1/fines/debtors").param("sort", "x").with(authenticated(LIBRARIAN)))
                .andExpect(status().isBadRequest());
        for (String tab : List.of("loans", "reservations", "fines", "history")) {
            mvc.perform(get("/api/v1/members/A000001/" + tab).with(authenticated(LIBRARIAN)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void espaceAdherentFiltreSurLAdherentDeLaSession() throws Exception {
        when(members.memberIdOfAccount(50L)).thenReturn(1L);
        when(reads.memberLoans(1L)).thenReturn(List.of());
        when(reservations.memberReservations(1L)).thenReturn(List.of());
        when(fines.unpaid(1L)).thenReturn(new MemberFines(List.of(), BigDecimal.ZERO));
        when(reads.history(eq(1L), any())).thenReturn(new PageImpl<>(List.of()));
        when(reservations.create(7L, 1L, 50L)).thenReturn(new ReservationCreated(3L, ReservationStatus.PENDING, 1, 1));
        when(reservations.cancel(3L, 1L, 50L)).thenReturn(new ReservationCancelled(3L, ReservationStatus.CANCELLED,
                null));
        when(reservations.myBookReservation(7L, 1L)).thenReturn(null);

        for (String path : List.of("loans", "reservations", "fines", "history", "books/7/reservation")) {
            mvc.perform(get("/api/v1/me/" + path).with(authenticated(MEMBER))).andExpect(status().isOk());
        }
        mvc.perform(post("/api/v1/me/reservations").with(authenticated(MEMBER)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"bookId\":7}")).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/me/reservations/3/cancellation").with(authenticated(MEMBER)).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/me/loans/9/renewal").with(authenticated(MEMBER)).with(csrf()))
                .andExpect(status().isOk());
        verify(loans).renew(9L, 1L, 50L);
        mvc.perform(get("/api/v1/me/loans").with(authenticated(LIBRARIAN))).andExpect(status().isForbidden());
    }
}
