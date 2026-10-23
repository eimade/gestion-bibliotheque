package com.bibliotheque.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import com.bibliotheque.catalog.CatalogFixtures;
import com.bibliotheque.catalog.domain.Book;
import com.bibliotheque.catalog.domain.CopyStatus;
import com.bibliotheque.catalog.repository.AuthorRepository;
import com.bibliotheque.catalog.repository.BookRepository;
import com.bibliotheque.catalog.repository.CategoryRepository;
import com.bibliotheque.catalog.repository.CopyCounts;
import com.bibliotheque.catalog.repository.CopyRepository;
import com.bibliotheque.catalog.repository.LanguageRepository;
import com.bibliotheque.catalog.service.CatalogViews.BookAdminDetail;
import com.bibliotheque.common.audit.AuditAction;
import com.bibliotheque.common.audit.AuditObjectType;
import com.bibliotheque.common.audit.AuditService;
import com.bibliotheque.common.config.BusinessTime;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.support.TestFixtures;

class BookServiceTest {

    private static final long ACTOR = 1L;

    private final BookRepository books = mock(BookRepository.class);
    private final AuthorRepository authors = mock(AuthorRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final LanguageRepository languages = mock(LanguageRepository.class);
    private final CopyRepository copies = mock(CopyRepository.class);
    private final CopyLoanInfo loanInfo = mock(CopyLoanInfo.class);
    private final AuditService audit = mock(AuditService.class);
    private BookService service;

    @BeforeEach
    void setUp() {
        service = new BookService(books, authors, categories, languages, copies, loanInfo, audit,
                new BusinessTime(Clock.fixed(TestFixtures.NOW, ZoneOffset.UTC), TestFixtures.properties()));
        when(languages.findById("fre")).thenReturn(Optional.of(CatalogFixtures.french()));
        when(authors.findAllById(List.of(1L))).thenReturn(List.of(CatalogFixtures.author(1L, "Camus", "Albert")));
        when(categories.findAllById(Set.of(1L))).thenReturn(List.of(CatalogFixtures.category(1L, "Roman")));
        when(books.save(any(Book.class))).thenAnswer(invocation -> {
            Book saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 3L);
            ReflectionTestUtils.setField(saved, "version", 0);
            return saved;
        });
        when(loanInfo.dueDatesOfActiveLoans(any())).thenReturn(Map.of());
    }

    @Test
    void creeUneNoticeAvecUnIsbn10ConvertiEtJournalise() {
        BookAdminDetail created = service.create(command("2070360024", 1942, null), ACTOR);

        assertThat(created.isbn()).isEqualTo("9782070360024");
        assertThat(created.title()).isEqualTo("L'Étranger");
        assertThat(created.copies()).isEmpty();
        verify(audit).recordAs(eq(ACTOR), eq(AuditAction.CREATE), eq(AuditObjectType.BOOK), eq("3"), anyMap());
    }

    @Test
    void refuseUnIsbnDejaPresentEnIndiquantLaNotice() {
        when(books.findByIsbn("9782070360024")).thenReturn(Optional.of(CatalogFixtures.book(9L, "L'Étranger")));

        assertThatThrownBy(() -> service.create(command("978-2-07-036002-4", 1942, null), ACTOR))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ErrorCode.ISBN_ALREADY_EXISTS);
                    assertThat(ex.properties()).containsEntry("existingBook", Map.of("id", 9L, "title", "L'Étranger"));
                });
    }

    @Test
    void refuseUnIsbnInvalide() {
        assertThatThrownBy(() -> service.create(command("9782070360025", 1942, null), ACTOR))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.ISBN_INVALID));
    }

    @Test
    void refuseUneAnneeFuture() {
        assertThatThrownBy(() -> service.create(command(null, 2027, null), ACTOR))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(ex.properties().get("fieldErrors").toString()).contains("publicationYear", "2026");
                });
    }

    @Test
    void accepteLAnneeEnCours() {
        assertThat(service.create(command(null, 2026, null), ACTOR).publicationYear()).isEqualTo(2026);
    }

    @Test
    void refuseUnAuteurInconnu() {
        BookCommand command = new BookCommand(null, "T", null, List.of(1L, 99L), Set.of(1L), null, null, "fre",
                null, null, null);
        when(authors.findAllById(List.of(1L, 99L))).thenReturn(List.of(CatalogFixtures.author(1L, "Camus", "Albert")));

        assertThatThrownBy(() -> service.create(command, ACTOR))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.AUTHOR_NOT_FOUND));
    }

    @Test
    void ignoreUnAuteurEnDoubleEtGardeLOrdre() {
        BookCommand command = new BookCommand(null, "T", null, List.of(2L, 1L, 2L), Set.of(1L), null, null, "fre",
                null, null, null);
        when(authors.findAllById(List.of(2L, 1L))).thenReturn(List.of(
                CatalogFixtures.author(1L, "Camus", "Albert"), CatalogFixtures.author(2L, "Zola", "Émile")));

        assertThat(service.create(command, ACTOR).authors()).extracting("lastName").containsExactly("Zola", "Camus");
    }

    @Test
    void refuseUneCategorieInconnue() {
        BookCommand command = new BookCommand(null, "T", null, List.of(1L), Set.of(1L, 7L), null, null, "fre",
                null, null, null);
        when(categories.findAllById(Set.of(1L, 7L))).thenReturn(List.of(CatalogFixtures.category(1L, "Roman")));

        assertThatThrownBy(() -> service.create(command, ACTOR))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.CATEGORY_NOT_FOUND));
    }

    @Test
    void refuseUneLangueInconnue() {
        BookCommand command = new BookCommand(null, "T", null, List.of(1L), Set.of(1L), null, null, "xxx",
                null, null, null);

        assertThatThrownBy(() -> service.create(command, ACTOR))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void refuseUnTitreFaitDEspaces() {
        BookCommand command = new BookCommand(null, "   ", null, List.of(1L), Set.of(1L), null, null, "fre",
                null, null, null);

        assertThatThrownBy(() -> service.create(command, ACTOR))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void refuseUneModificationAvecUneVersionPerimee() {
        Book book = CatalogFixtures.book(3L, "L'Étranger");
        ReflectionTestUtils.setField(book, "version", 4);
        when(books.findById(3L)).thenReturn(Optional.of(book));

        assertThatThrownBy(() -> service.update(3L, command(null, 1942, 3), ACTOR))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION));
    }

    @Test
    void exigeLaVersionEnModification() {
        when(books.findById(3L)).thenReturn(Optional.of(CatalogFixtures.book(3L, "L'Étranger")));

        assertThatThrownBy(() -> service.update(3L, command(null, 1942, null), ACTOR))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void modifieLaNoticeEtExcluElleMemeDuControleDIsbn() {
        Book book = CatalogFixtures.book(3L, "Ancien titre");
        when(books.findById(3L)).thenReturn(Optional.of(book));
        when(books.findByIsbnAndIdNot("9782070360024", 3L)).thenReturn(Optional.empty());

        BookAdminDetail updated = service.update(3L, command("9782070360024", 1942, 0), ACTOR);

        assertThat(updated.title()).isEqualTo("L'Étranger");
        verify(books, never()).findByIsbn(any());
        verify(audit).recordAs(eq(ACTOR), eq(AuditAction.UPDATE), eq(AuditObjectType.BOOK), eq("3"), anyMap());
    }

    @Test
    void refuseDeSupprimerUneNoticeAvecExemplaires() {
        when(books.findById(3L)).thenReturn(Optional.of(CatalogFixtures.book(3L, "L'Étranger")));
        when(copies.countByBookId(3L)).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(3L, ACTOR))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.BOOK_HAS_COPIES));
        verify(books, never()).delete(any(Book.class));
    }

    @Test
    void supprimeUneNoticeSansExemplaire() {
        Book book = CatalogFixtures.book(3L, "Créée par erreur");
        when(books.findById(3L)).thenReturn(Optional.of(book));

        service.delete(3L, ACTOR);

        verify(books).delete(book);
        verify(audit).recordAs(eq(ACTOR), eq(AuditAction.DELETE), eq(AuditObjectType.BOOK), eq("3"), anyMap());
    }

    @Test
    void ficheDeGestionAvecTousLesExemplairesEtLaDateDeRetour() {
        Book book = CatalogFixtures.book(3L, "L'Étranger");
        when(books.findById(3L)).thenReturn(Optional.of(book));
        when(copies.findByBookIdOrderByCopyNumberAsc(3L)).thenReturn(List.of(
                CatalogFixtures.copy(10L, "00000010", book, CopyStatus.AVAILABLE),
                CatalogFixtures.copy(11L, "00000011", book, CopyStatus.ON_LOAN),
                CatalogFixtures.copy(12L, "00000012", book, CopyStatus.WITHDRAWN)));
        when(loanInfo.dueDatesOfActiveLoans(List.of(10L, 11L, 12L)))
                .thenReturn(Map.of(11L, java.time.LocalDate.of(2026, 11, 5)));

        BookAdminDetail detail = service.get(3L);

        assertThat(detail.copies()).hasSize(3);
        assertThat(detail.copies().get(1).dueDate()).isEqualTo("2026-11-05");
        assertThat(detail.availableCount()).isEqualTo(1);
        assertThat(detail.totalCount()).isEqualTo(2);
    }

    @Test
    void ficheIntrouvable() {
        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.BOOK_NOT_FOUND));
    }

    @Test
    @SuppressWarnings("unchecked")
    void rechercheDeGestionAvecCompteursEtNoticeRetiree() {
        Book book = CatalogFixtures.book(6L, "Vieux traité");
        var pageable = PageRequest.of(0, 20);
        when(books.findAll(any(Specification.class), eq(pageable))).thenReturn(new PageImpl<>(List.of(book), pageable, 1));
        when(copies.countsByBookIds(List.of(6L))).thenReturn(List.of(counts(6L, 0, 0, 1, 1)));

        var page = service.search("traité", null, true, pageable);

        assertThat(page.getContent()).singleElement().satisfies(item -> {
            assertThat(item.withdrawn()).isTrue();
            assertThat(item.copyCount()).isEqualTo(1);
        });
        ArgumentCaptor<Specification<Book>> spec = ArgumentCaptor.forClass(Specification.class);
        verify(books).findAll(spec.capture(), eq(pageable));
        assertThat(spec.getValue()).isNotNull();
    }

    static CopyCounts counts(long bookId, long available, long total, long copies, long withdrawn) {
        return new CopyCounts() {
            public Long getBookId() { return bookId; }
            public Long getAvailable() { return available; }
            public Long getTotal() { return total; }
            public Long getCopies() { return copies; }
            public Long getWithdrawn() { return withdrawn; }
        };
    }

    private static BookCommand command(String isbn, Integer year, Integer version) {
        return new BookCommand(isbn, "  L'Étranger ", " ", List.of(1L), Set.of(1L), "Gallimard", year, "fre", 186,
                null, version);
    }
}
