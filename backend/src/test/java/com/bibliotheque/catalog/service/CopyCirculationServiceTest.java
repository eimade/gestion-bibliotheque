package com.bibliotheque.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.bibliotheque.catalog.CatalogFixtures;
import com.bibliotheque.catalog.domain.Book;
import com.bibliotheque.catalog.domain.Copy;
import com.bibliotheque.catalog.domain.CopyStatus;
import com.bibliotheque.catalog.domain.WithdrawalReason;
import com.bibliotheque.catalog.repository.BookRepository;
import com.bibliotheque.catalog.repository.CopyRepository;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.support.TestFixtures;

class CopyCirculationServiceTest {

    private final CopyRepository copies = mock(CopyRepository.class);
    private final BookRepository books = mock(BookRepository.class);
    private final CopyCirculationService service = new CopyCirculationService(copies, books);
    private final Book book = CatalogFixtures.book(3L, "L'Étranger");

    @Test
    void verrouilleEtDecritUnExemplaire() {
        Copy copy = CatalogFixtures.copy(10L, "00000010", book, CopyStatus.AVAILABLE);
        when(copies.lockByCopyNumber("00000010")).thenReturn(Optional.of(copy));
        when(copies.lockById(10L)).thenReturn(Optional.of(copy));
        when(copies.findByCopyNumber("00000010")).thenReturn(Optional.of(copy));
        when(copies.findWithBookByIdIn(List.of(10L))).thenReturn(List.of(copy));

        var snapshot = service.lockByNumber("00000010");

        assertThat(snapshot.title()).isEqualTo("L'Étranger");
        assertThat(snapshot.authors()).isEqualTo("Albert Camus");
        assertThat(service.lockById(10L).copyNumber()).isEqualTo("00000010");
        assertThat(service.findByNumber("00000010")).isPresent();
        assertThat(service.describe(List.of(10L))).containsKey(10L);
        assertThat(service.describe(List.of())).isEmpty();
    }

    @Test
    void exemplaireInconnu() {
        assertThatThrownBy(() -> service.lockByNumber("00000099"))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.COPY_NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo("Exemplaire inconnu.");
                });
        assertThatThrownBy(() -> service.lockById(99L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void transitionsDeCirculationMaisJamaisPourUnExemplaireRetire() {
        Copy copy = CatalogFixtures.copy(10L, "00000010", book, CopyStatus.AVAILABLE);
        Copy withdrawn = CatalogFixtures.copy(11L, "00000011", book, CopyStatus.AVAILABLE);
        withdrawn.withdraw(WithdrawalReason.WEEDED, TestFixtures.NOW);
        when(copies.findById(10L)).thenReturn(Optional.of(copy));
        when(copies.findById(11L)).thenReturn(Optional.of(withdrawn));

        service.changeStatus(10L, CopyStatus.ON_LOAN);

        assertThat(copy.getStatus()).isEqualTo(CopyStatus.ON_LOAN);
        assertThatThrownBy(() -> service.changeStatus(11L, CopyStatus.AVAILABLE))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.COPY_WITHDRAWN));
        assertThatThrownBy(() -> copy.changeCirculationStatus(CopyStatus.WITHDRAWN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void disponibiliteEtTitresDesNotices() {
        when(copies.countByBookIdAndStatus(3L, CopyStatus.AVAILABLE)).thenReturn(0L);
        when(copies.countByBookIdAndStatusIn(3L, java.util.EnumSet.of(CopyStatus.ON_LOAN, CopyStatus.RESERVED)))
                .thenReturn(2L);
        when(books.findById(3L)).thenReturn(Optional.of(book));
        when(books.findAllById(List.of(3L))).thenReturn(List.of(book));

        assertThat(service.availability(3L).inCirculation()).isEqualTo(2L);
        assertThat(service.titleOf(3L)).isEqualTo("L'Étranger");
        assertThat(service.titlesOf(List.of(3L))).containsEntry(3L, "L'Étranger");
        assertThat(service.titlesOf(List.of())).isEmpty();
        assertThatThrownBy(() -> service.titleOf(9L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.BOOK_NOT_FOUND));
    }
}
