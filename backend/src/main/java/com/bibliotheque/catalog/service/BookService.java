package com.bibliotheque.catalog.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bibliotheque.catalog.domain.Author;
import com.bibliotheque.catalog.domain.Book;
import com.bibliotheque.catalog.domain.BookDescription;
import com.bibliotheque.catalog.domain.Category;
import com.bibliotheque.catalog.domain.Copy;
import com.bibliotheque.catalog.domain.Language;
import com.bibliotheque.catalog.repository.AuthorRepository;
import com.bibliotheque.catalog.repository.BookRepository;
import com.bibliotheque.catalog.repository.BookSpecifications;
import com.bibliotheque.catalog.repository.CategoryRepository;
import com.bibliotheque.catalog.repository.CopyRepository;
import com.bibliotheque.catalog.repository.LanguageRepository;
import com.bibliotheque.catalog.service.CatalogViews.BookAdminDetail;
import com.bibliotheque.catalog.service.CatalogViews.BookAdminItem;
import com.bibliotheque.catalog.service.CatalogViews.CopyView;
import com.bibliotheque.catalog.service.CatalogViews.Counts;
import com.bibliotheque.catalog.service.CatalogViews.LanguageView;
import com.bibliotheque.common.audit.AuditAction;
import com.bibliotheque.common.audit.AuditObjectType;
import com.bibliotheque.common.audit.AuditService;
import com.bibliotheque.common.config.BusinessTime;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;

/** Notices côté personnel (US-009, US-010, US-011, RG-07, RG-08, RG-10). */
@Service
public class BookService {

    static final int MIN_PUBLICATION_YEAR = 1450;

    private final BookRepository books;
    private final AuthorRepository authors;
    private final CategoryRepository categories;
    private final LanguageRepository languages;
    private final CopyRepository copies;
    private final CopyLoanInfo loanInfo;
    private final AuditService audit;
    private final BusinessTime time;

    public BookService(BookRepository books, AuthorRepository authors, CategoryRepository categories,
                       LanguageRepository languages, CopyRepository copies, CopyLoanInfo loanInfo,
                       AuditService audit, BusinessTime time) {
        this.books = books;
        this.authors = authors;
        this.categories = categories;
        this.languages = languages;
        this.copies = copies;
        this.loanInfo = loanInfo;
        this.audit = audit;
        this.time = time;
    }

    /** AC-007-7 : le personnel voit aussi les notices sans exemplaire et, sur demande, les notices retirées. */
    @Transactional(readOnly = true)
    public Page<BookAdminItem> search(String keyword, Long categoryId, boolean includeWithdrawn, Pageable pageable) {
        Specification<Book> spec = CatalogSearchService.keyword(keyword);
        if (categoryId != null) {
            spec = spec.and(BookSpecifications.inCategory(categoryId));
        }
        if (!includeWithdrawn) {
            spec = spec.and(BookSpecifications.notEntirelyWithdrawn());
        }
        Page<Book> page = books.findAll(spec, pageable);
        Map<Long, Counts> counts = CatalogSearchService.countsOf(page.map(Book::getId).getContent(), copies);
        return page.map(book -> BookAdminItem.of(book, counts.getOrDefault(book.getId(), Counts.NONE)));
    }

    @Transactional(readOnly = true)
    public BookAdminDetail get(long bookId) {
        return detail(load(bookId));
    }

    @Transactional
    public BookAdminDetail create(BookCommand command, long actorAccountId) {
        Prepared prepared = prepare(command, null);
        Book book = books.save(new Book(prepared.description(), prepared.language(), prepared.authors(),
                prepared.categories()));
        audit.recordAs(actorAccountId, AuditAction.CREATE, AuditObjectType.BOOK, String.valueOf(book.getId()),
                summaryOf(book));
        return detail(book);
    }

    /** AC-010-3 : refus si la notice a changé depuis sa lecture ; les auteurs sont remplacés en deux temps. */
    @Transactional
    public BookAdminDetail update(long bookId, BookCommand command, long actorAccountId) {
        Book book = load(bookId);
        Validation.requireVersion(command.version(), book.getVersion());
        Prepared prepared = prepare(command, bookId);
        book.describe(prepared.description(), prepared.language());
        book.replaceCategories(prepared.categories());
        book.clearAuthors();
        books.flush();
        book.addAuthors(prepared.authors());
        books.flush();
        audit.recordAs(actorAccountId, AuditAction.UPDATE, AuditObjectType.BOOK, String.valueOf(book.getId()),
                summaryOf(book));
        return detail(book);
    }

    /** RG-10 / AC-011-2 : suppression seulement sans aucun exemplaire, même retiré. */
    @Transactional
    public void delete(long bookId, long actorAccountId) {
        Book book = load(bookId);
        if (copies.countByBookId(bookId) > 0) {
            throw BusinessException.of(ErrorCode.BOOK_HAS_COPIES,
                    "Suppression impossible : la notice possède des exemplaires.");
        }
        Map<String, Object> summary = summaryOf(book);
        books.delete(book);
        audit.recordAs(actorAccountId, AuditAction.DELETE, AuditObjectType.BOOK, String.valueOf(bookId), summary);
    }

    BookAdminDetail detail(Book book) {
        List<Copy> bookCopies = copies.findByBookIdOrderByCopyNumberAsc(book.getId());
        Map<Long, LocalDate> dueDates = loanInfo.dueDatesOfActiveLoans(bookCopies.stream().map(Copy::getId).toList());
        Counts counts = Counts.of(bookCopies);
        return new BookAdminDetail(book.getId(), book.getIsbn(), book.getTitle(), book.getSubtitle(),
                CatalogViews.authors(book), CatalogViews.categories(book), book.getPublisher(),
                book.getPublicationYear(), LanguageView.of(book.getLanguage()), book.getPageCount(), book.getSummary(),
                bookCopies.stream().map(copy -> CopyView.of(copy, dueDates)).toList(), counts.available(),
                counts.total(), book.getVersion());
    }

    private Book load(long bookId) {
        return books.findById(bookId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.BOOK_NOT_FOUND, "Cette notice est introuvable."));
    }

    /** RG-07, RG-08 : validation et chargement des références ; {@code bookId} exclu du contrôle d'unicité. */
    private Prepared prepare(BookCommand command, Long bookId) {
        String isbn = Isbn.normalizeOptional(command.isbn());
        if (isbn != null) {
            (bookId == null ? books.findByIsbn(isbn) : books.findByIsbnAndIdNot(isbn, bookId)).ifPresent(existing -> {
                throw BusinessException.of(ErrorCode.ISBN_ALREADY_EXISTS,
                                "Un livre avec cet ISBN existe déjà : « " + existing.getTitle() + " ».")
                        .withProperty("existingBook", Map.of("id", existing.getId(), "title", existing.getTitle()));
            });
        }
        int currentYear = time.today().getYear();
        Integer year = command.publicationYear();
        if (year != null && (year < MIN_PUBLICATION_YEAR || year > currentYear)) {
            throw Validation.invalidField("publicationYear",
                    "L'année doit être comprise entre " + MIN_PUBLICATION_YEAR + " et " + currentYear + ".");
        }
        if (command.title() == null || command.title().isBlank()) {
            throw Validation.invalidField("title", "Ce champ est obligatoire.");
        }
        Language language = languages.findById(command.languageCode())
                .orElseThrow(() -> Validation.invalidField("languageCode", "Langue inconnue."));
        BookDescription description = new BookDescription(isbn, command.title().trim(),
                Validation.optional(command.subtitle()), Validation.optional(command.publisher()), year,
                command.pageCount(), Validation.optional(command.summary()));
        return new Prepared(description, language, loadAuthors(command.authorIds()),
                loadCategories(command.categoryIds()));
    }

    /** Auteurs dans l'ordre demandé, sans doublon (clé primaire de {@code book_authors}). */
    private List<Author> loadAuthors(List<Long> authorIds) {
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(authorIds));
        Map<Long, Author> found = authors.findAllById(ids).stream()
                .collect(Collectors.toMap(Author::getId, Function.identity()));
        if (found.size() != ids.size()) {
            throw BusinessException.of(ErrorCode.AUTHOR_NOT_FOUND, "Un des auteurs est introuvable.");
        }
        return ids.stream().map(found::get).toList();
    }

    private Set<Category> loadCategories(Set<Long> categoryIds) {
        List<Category> found = categories.findAllById(categoryIds);
        if (found.size() != categoryIds.size()) {
            throw BusinessException.of(ErrorCode.CATEGORY_NOT_FOUND, "Une des catégories est introuvable.");
        }
        return new LinkedHashSet<>(found);
    }

    private static Map<String, Object> summaryOf(Book book) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("title", book.getTitle());
        if (book.getIsbn() != null) {
            summary.put("isbn", book.getIsbn());
        }
        return summary;
    }

    private record Prepared(BookDescription description, Language language, List<Author> authors,
                            Set<Category> categories) {
    }
}
