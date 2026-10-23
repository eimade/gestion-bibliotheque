package com.bibliotheque.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import com.bibliotheque.catalog.CatalogFixtures;
import com.bibliotheque.catalog.domain.Book;
import com.bibliotheque.catalog.domain.CopyStatus;
import com.bibliotheque.catalog.repository.BookRepository;
import com.bibliotheque.catalog.repository.CategoryRepository;
import com.bibliotheque.catalog.repository.CopyRepository;
import com.bibliotheque.catalog.repository.LanguageRepository;
import com.bibliotheque.catalog.service.CatalogSearchService.PublicSearch;
import com.bibliotheque.catalog.service.CatalogViews.BookPublicDetail;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;

class CatalogSearchServiceTest {

    private final BookRepository books = mock(BookRepository.class);
    private final CopyRepository copies = mock(CopyRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final LanguageRepository languages = mock(LanguageRepository.class);
    private final CopyLoanInfo loanInfo = mock(CopyLoanInfo.class);
    private final CatalogSearchService service = new CatalogSearchService(books, copies, categories, languages, loanInfo);

    @Test
    @SuppressWarnings("unchecked")
    void rechercheAvecCompteursDisponiblesSurTotal() {
        Book book = CatalogFixtures.book(1L, "Le Comte de Monte-Cristo");
        var pageable = PageRequest.of(0, 20);
        when(books.findAll(any(Specification.class), eq(pageable))).thenReturn(new PageImpl<>(List.of(book), pageable, 1));
        when(copies.countsByBookIds(List.of(1L))).thenReturn(List.of(BookServiceTest.counts(1L, 1, 2, 3, 1)));

        var page = service.search(new PublicSearch("monte cristo", 1L, "fre", true), pageable, true);

        assertThat(page.getContent()).singleElement().satisfies(item -> {
            assertThat(item.availableCount()).isEqualTo(1);
            assertThat(item.totalCount()).isEqualTo(2);
            assertThat(item.authors()).extracting("lastName").containsExactly("Camus");
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void noticeSansExemplaireAvecCompteursAZero() {
        var pageable = PageRequest.of(0, 20);
        when(books.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(CatalogFixtures.book(2L, "Germinal")), pageable, 1));

        assertThat(service.search(new PublicSearch(null, null, null, false), pageable, false).getContent())
                .singleElement().satisfies(item -> assertThat(item.totalCount()).isZero());
    }

    @Test
    void ficheSansExemplaireRetireNiNumeroAvecDateDeRetour() {
        Book book = CatalogFixtures.book(3L, "L'Étranger");
        when(books.findById(3L)).thenReturn(Optional.of(book));
        when(copies.findByBookIdOrderByCopyNumberAsc(3L)).thenReturn(List.of(
                CatalogFixtures.copy(10L, "00000010", book, CopyStatus.AVAILABLE),
                CatalogFixtures.copy(11L, "00000011", book, CopyStatus.ON_LOAN),
                CatalogFixtures.copy(12L, "00000012", book, CopyStatus.WITHDRAWN)));
        when(loanInfo.dueDatesOfActiveLoans(List.of(10L, 11L))).thenReturn(Map.of(11L, LocalDate.of(2026, 11, 5)));

        BookPublicDetail detail = service.publicDetail(3L);

        assertThat(detail.copies()).hasSize(2);
        assertThat(detail.copies().get(1).status()).isEqualTo(CopyStatus.ON_LOAN);
        assertThat(detail.copies().get(1).dueDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(detail.availableCount()).isEqualTo(1);
        assertThat(detail.totalCount()).isEqualTo(2);
        assertThat(detail.language().label()).isEqualTo("Français");
    }

    @Test
    void ficheIntrouvableSiTousLesExemplairesSontRetires() {
        Book book = CatalogFixtures.book(6L, "Vieux traité");
        when(books.findById(6L)).thenReturn(Optional.of(book));
        when(copies.findByBookIdOrderByCopyNumberAsc(6L))
                .thenReturn(List.of(CatalogFixtures.copy(13L, "00000013", book, CopyStatus.WITHDRAWN)));

        assertThatThrownBy(() -> service.publicDetail(6L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.BOOK_NOT_FOUND));
    }

    @Test
    void ficheIntrouvable() {
        assertThatThrownBy(() -> service.publicDetail(99L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.BOOK_NOT_FOUND));
    }

    @Test
    void listesDeReference() {
        when(categories.findAllByOrderByLabelAsc()).thenReturn(List.of(CatalogFixtures.category(1L, "Roman")));
        when(languages.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(CatalogFixtures.french()));

        assertThat(service.categoryOptions()).extracting("label").containsExactly("Roman");
        assertThat(service.languageOptions()).extracting("code").containsExactly("fre");
    }

    @Test
    void motCleVideSansCritere() {
        assertThat(CatalogSearchService.keyword("  ")).isNotNull();
        assertThat(CatalogSearchService.keyword("978-2-07-036002-4")).isNotNull();
    }
}
