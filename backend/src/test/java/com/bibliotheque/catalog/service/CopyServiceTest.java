package com.bibliotheque.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.bibliotheque.catalog.CatalogFixtures;
import com.bibliotheque.catalog.domain.Book;
import com.bibliotheque.catalog.domain.Copy;
import com.bibliotheque.catalog.domain.CopyStatus;
import com.bibliotheque.catalog.domain.PhysicalCondition;
import com.bibliotheque.catalog.domain.WithdrawalReason;
import com.bibliotheque.catalog.repository.BookRepository;
import com.bibliotheque.catalog.repository.CopyNumberGenerator;
import com.bibliotheque.catalog.repository.CopyRepository;
import com.bibliotheque.catalog.service.CatalogViews.CopyView;
import com.bibliotheque.common.audit.AuditAction;
import com.bibliotheque.common.audit.AuditObjectType;
import com.bibliotheque.common.audit.AuditService;
import com.bibliotheque.common.config.BusinessTime;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.support.TestFixtures;

class CopyServiceTest {

    private static final long ACTOR = 1L;

    private final CopyRepository copies = mock(CopyRepository.class);
    private final BookRepository books = mock(BookRepository.class);
    private final CopyNumberGenerator numbers = mock(CopyNumberGenerator.class);
    private final CopyLoanInfo loanInfo = mock(CopyLoanInfo.class);
    private final CopyAllocation allocation = mock(CopyAllocation.class);
    private final AuditService audit = mock(AuditService.class);
    private CopyService service;
    private Book book;

    @BeforeEach
    void setUp() {
        service = new CopyService(copies, books, numbers, loanInfo, allocation, audit,
                new BusinessTime(Clock.fixed(TestFixtures.NOW, ZoneOffset.UTC), TestFixtures.properties()));
        book = CatalogFixtures.book(3L, "L'Étranger");
        when(books.findById(3L)).thenReturn(Optional.of(book));
        when(numbers.next()).thenReturn("00000001", "00000002", "00000003");
        AtomicLong ids = new AtomicLong(100);
        when(copies.save(any(Copy.class))).thenAnswer(invocation -> {
            Copy saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", ids.incrementAndGet());
            ReflectionTestUtils.setField(saved, "version", 0);
            return saved;
        });
        when(loanInfo.dueDatesOfActiveLoans(any())).thenReturn(Map.of());
    }

    @Test
    void ajouteTroisExemplairesDisponiblesAvecDesNumerosDistincts() {
        List<CopyView> created = service.add(3L, 3, " R CAM ", PhysicalCondition.GOOD, LocalDate.of(2026, 10, 17), ACTOR);

        assertThat(created).extracting(CopyView::copyNumber).containsExactly("00000001", "00000002", "00000003");
        assertThat(created).allSatisfy(copy -> {
            assertThat(copy.status()).isEqualTo(CopyStatus.AVAILABLE);
            assertThat(copy.shelfMark()).isEqualTo("R CAM");
        });
        verify(audit, times(3)).recordAs(eq(ACTOR), eq(AuditAction.CREATE), eq(AuditObjectType.COPY), any(), anyMap());
        verify(allocation).copiesAdded(eq(3L), argThat(ids -> ids.size() == 3), eq(ACTOR));
    }

    @Test
    void refuseUneDateDAcquisitionFuture() {
        assertThatThrownBy(() -> service.add(3L, 1, "R", PhysicalCondition.GOOD, LocalDate.of(2026, 10, 18), ACTOR))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(ex.properties().get("fieldErrors").toString()).contains("acquisitionDate");
                });
    }

    @Test
    void refuseUneNoticeInconnue() {
        assertThatThrownBy(() -> service.add(99L, 1, "R", PhysicalCondition.GOOD, LocalDate.of(2026, 1, 1), ACTOR))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.BOOK_NOT_FOUND));
    }

    @Test
    void modifieLaCoteEtLEtat() {
        Copy copy = CatalogFixtures.copy(10L, "00000010", book, CopyStatus.AVAILABLE);
        when(copies.findByCopyNumber("00000010")).thenReturn(Optional.of(copy));

        CopyView view = service.update("00000010", "R CAM 2", PhysicalCondition.DAMAGED, 0, ACTOR);

        assertThat(view.shelfMark()).isEqualTo("R CAM 2");
        assertThat(view.physicalCondition()).isEqualTo(PhysicalCondition.DAMAGED);
        assertThat(view.copyNumber()).isEqualTo("00000010");
    }

    @Test
    void refuseUneVersionPerimee() {
        when(copies.findByCopyNumber("00000010"))
                .thenReturn(Optional.of(CatalogFixtures.copy(10L, "00000010", book, CopyStatus.AVAILABLE)));

        assertThatThrownBy(() -> service.update("00000010", "R", PhysicalCondition.GOOD, 7, ACTOR))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION));
    }

    @Test
    void refuseDeModifierUnExemplaireRetire() {
        when(copies.findByCopyNumber("00000010"))
                .thenReturn(Optional.of(CatalogFixtures.copy(10L, "00000010", book, CopyStatus.WITHDRAWN)));

        assertThatThrownBy(() -> service.update("00000010", "R", PhysicalCondition.GOOD, 0, ACTOR))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.COPY_WITHDRAWN));
    }

    @Test
    void retireUnExemplaireDisponibleEtJournalise() {
        when(copies.findByCopyNumber("00000010"))
                .thenReturn(Optional.of(CatalogFixtures.copy(10L, "00000010", book, CopyStatus.AVAILABLE)));

        CopyView view = service.withdraw("00000010", WithdrawalReason.WEEDED, ACTOR);

        assertThat(view.status()).isEqualTo(CopyStatus.WITHDRAWN);
        assertThat(view.withdrawalReason()).isEqualTo(WithdrawalReason.WEEDED);
        verify(audit).recordAs(eq(ACTOR), eq(AuditAction.STATE_CHANGE), eq(AuditObjectType.COPY), eq("00000010"),
                anyMap());
    }

    @Test
    void retireUnExemplairePerdu() {
        when(copies.findByCopyNumber("00000010"))
                .thenReturn(Optional.of(CatalogFixtures.copy(10L, "00000010", book, CopyStatus.LOST)));

        assertThat(service.withdraw("00000010", WithdrawalReason.LOST, ACTOR).status()).isEqualTo(CopyStatus.WITHDRAWN);
    }

    @Test
    void refuseDeRetirerUnExemplaireEnCirculation() {
        for (CopyStatus status : List.of(CopyStatus.ON_LOAN, CopyStatus.RESERVED)) {
            when(copies.findByCopyNumber("00000010"))
                    .thenReturn(Optional.of(CatalogFixtures.copy(10L, "00000010", book, status)));

            assertThatThrownBy(() -> service.withdraw("00000010", WithdrawalReason.OTHER, ACTOR))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.code()).isEqualTo(ErrorCode.COPY_IN_CIRCULATION));
        }
    }

    @Test
    void refuseUnExemplaireInconnu() {
        assertThatThrownBy(() -> service.withdraw("99999999", WithdrawalReason.OTHER, ACTOR))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.COPY_NOT_FOUND));
    }
}
