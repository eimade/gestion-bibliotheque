package com.bibliotheque.catalog.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.bibliotheque.catalog.domain.Author;
import com.bibliotheque.catalog.domain.Book;
import com.bibliotheque.catalog.domain.Category;
import com.bibliotheque.catalog.domain.Copy;
import com.bibliotheque.catalog.domain.CopyStatus;
import com.bibliotheque.catalog.domain.Language;
import com.bibliotheque.catalog.domain.PhysicalCondition;
import com.bibliotheque.catalog.domain.WithdrawalReason;
import com.bibliotheque.catalog.repository.CopyCounts;

/**
 * Vues du catalogue renvoyées par les services (records, jamais d'entité) ; leurs champs sont ceux des
 * schémas d'`openapi.yaml` et elles servent directement de DTO de réponse (DEV-catalogue §6).
 */
public final class CatalogViews {

    private CatalogViews() {
    }

    public record AuthorView(long id, String lastName, String firstName) {
        static AuthorView of(Author author) {
            return new AuthorView(author.getId(), author.getLastName(), author.getFirstName());
        }
    }

    public record CategoryView(long id, String label) {
        static CategoryView of(Category category) {
            return new CategoryView(category.getId(), category.getLabel());
        }
    }

    public record LanguageView(String code, String label) {
        static LanguageView of(Language language) {
            return new LanguageView(language.getCode(), language.getLabel());
        }
    }

    /** Compteurs RG-11 d'une notice ; une notice sans exemplaire a tous ses compteurs à zéro. */
    public record Counts(int available, int total, int copies, int withdrawn) {
        static final Counts NONE = new Counts(0, 0, 0, 0);

        static Counts of(CopyCounts counts) {
            return new Counts(counts.getAvailable().intValue(), counts.getTotal().intValue(),
                    counts.getCopies().intValue(), counts.getWithdrawn().intValue());
        }

        static Counts of(List<Copy> copies) {
            int available = (int) copies.stream().filter(c -> c.getStatus() == CopyStatus.AVAILABLE).count();
            int withdrawn = (int) copies.stream().filter(c -> c.getStatus() == CopyStatus.WITHDRAWN).count();
            int lost = (int) copies.stream().filter(c -> c.getStatus() == CopyStatus.LOST).count();
            return new Counts(available, copies.size() - withdrawn - lost, copies.size(), withdrawn);
        }

        boolean entirelyWithdrawn() {
            return copies > 0 && withdrawn == copies;
        }
    }

    /** Résultat de recherche publique (schéma BookSearchItem). */
    public record BookSearchItem(long id, String title, String subtitle, List<AuthorView> authors,
                                 Integer publicationYear, List<CategoryView> categories, int availableCount,
                                 int totalCount) {
        static BookSearchItem of(Book book, Counts counts) {
            return new BookSearchItem(book.getId(), book.getTitle(), book.getSubtitle(), CatalogViews.authors(book),
                    book.getPublicationYear(), CatalogViews.categories(book), counts.available(), counts.total());
        }
    }

    /** Exemplaire vu du public : ni numéro, ni emprunteur (AC-008-1). */
    public record PublicCopy(String shelfMark, CopyStatus status, LocalDate dueDate) {
    }

    /** Fiche publique (schéma BookPublicDetail). */
    public record BookPublicDetail(long id, String isbn, String title, String subtitle, List<AuthorView> authors,
                                   List<CategoryView> categories, String publisher, Integer publicationYear,
                                   LanguageView language, Integer pageCount, String summary, List<PublicCopy> copies,
                                   int availableCount, int totalCount) {
    }

    /** Ligne du catalogue de gestion (schéma BookAdminItem). */
    public record BookAdminItem(long id, String title, List<AuthorView> authors, String isbn, Integer publicationYear,
                                int availableCount, int totalCount, int copyCount, boolean withdrawn) {
        static BookAdminItem of(Book book, Counts counts) {
            return new BookAdminItem(book.getId(), book.getTitle(), CatalogViews.authors(book), book.getIsbn(),
                    book.getPublicationYear(), counts.available(), counts.total(), counts.copies(),
                    counts.entirelyWithdrawn());
        }
    }

    /** Exemplaire vu du personnel (schéma CopyResponse). */
    public record CopyView(long id, String copyNumber, String shelfMark, PhysicalCondition physicalCondition,
                           LocalDate acquisitionDate, CopyStatus status, WithdrawalReason withdrawalReason,
                           LocalDate dueDate, Integer version) {
        static CopyView of(Copy copy, Map<Long, LocalDate> dueDates) {
            return new CopyView(copy.getId(), copy.getCopyNumber(), copy.getShelfMark(), copy.getPhysicalCondition(),
                    copy.getAcquisitionDate(), copy.getStatus(), copy.getWithdrawalReason(),
                    dueDates.get(copy.getId()), copy.getVersion());
        }
    }

    /** Fiche de gestion (schéma BookAdminDetail). */
    public record BookAdminDetail(long id, String isbn, String title, String subtitle, List<AuthorView> authors,
                                  List<CategoryView> categories, String publisher, Integer publicationYear,
                                  LanguageView language, Integer pageCount, String summary, List<CopyView> copies,
                                  int availableCount, int totalCount, Integer version) {
    }

    /** Catégorie et son utilisation (schéma CategoryResponse). */
    public record CategoryResponse(long id, String label, long bookCount, Integer version) {
    }

    static List<AuthorView> authors(Book book) {
        return book.getAuthors().stream().map(AuthorView::of).toList();
    }

    static List<CategoryView> categories(Book book) {
        return book.getCategories().stream()
                .map(CategoryView::of)
                .sorted(Comparator.comparing(CategoryView::label, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
