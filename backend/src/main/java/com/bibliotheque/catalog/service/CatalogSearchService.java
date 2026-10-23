package com.bibliotheque.catalog.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bibliotheque.catalog.domain.Book;
import com.bibliotheque.catalog.domain.Copy;
import com.bibliotheque.catalog.domain.CopyStatus;
import com.bibliotheque.catalog.repository.BookRepository;
import com.bibliotheque.catalog.repository.BookSpecifications;
import com.bibliotheque.catalog.repository.CategoryRepository;
import com.bibliotheque.catalog.repository.CopyCounts;
import com.bibliotheque.catalog.repository.CopyRepository;
import com.bibliotheque.catalog.repository.LanguageRepository;
import com.bibliotheque.catalog.service.CatalogViews.BookPublicDetail;
import com.bibliotheque.catalog.service.CatalogViews.BookSearchItem;
import com.bibliotheque.catalog.service.CatalogViews.CategoryView;
import com.bibliotheque.catalog.service.CatalogViews.Counts;
import com.bibliotheque.catalog.service.CatalogViews.LanguageView;
import com.bibliotheque.catalog.service.CatalogViews.PublicCopy;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;

/** Catalogue public (US-007, US-008, RG-11) : jamais de numéro d'exemplaire ni d'emprunteur. */
@Service
@Transactional(readOnly = true)
public class CatalogSearchService {

    private final BookRepository books;
    private final CopyRepository copies;
    private final CategoryRepository categories;
    private final LanguageRepository languages;
    private final CopyLoanInfo loanInfo;

    public CatalogSearchService(BookRepository books, CopyRepository copies, CategoryRepository categories,
                                LanguageRepository languages, CopyLoanInfo loanInfo) {
        this.books = books;
        this.copies = copies;
        this.categories = categories;
        this.languages = languages;
        this.loanInfo = loanInfo;
    }

    /**
     * @param byRelevance tri par pertinence (mot-clé saisi et aucun tri explicite) ; {@code pageable}
     *                    est alors non trié
     */
    public Page<BookSearchItem> search(PublicSearch criteria, Pageable pageable, boolean byRelevance) {
        Specification<Book> spec = BookSpecifications.visibleToPublic()
                .and(keyword(criteria.keyword()));
        if (criteria.categoryId() != null) {
            spec = spec.and(BookSpecifications.inCategory(criteria.categoryId()));
        }
        if (criteria.languageCode() != null) {
            spec = spec.and(BookSpecifications.inLanguage(criteria.languageCode()));
        }
        if (criteria.availableOnly()) {
            spec = spec.and(BookSpecifications.hasAvailableCopy());
        }
        if (byRelevance) {
            spec = spec.and(BookSpecifications.orderedByRelevance(criteria.keyword()));
        }
        Page<Book> page = books.findAll(spec, pageable);
        Map<Long, Counts> counts = countsOf(page.map(Book::getId).getContent(), copies);
        return page.map(book -> BookSearchItem.of(book, counts.getOrDefault(book.getId(), Counts.NONE)));
    }

    /** AC-008-1 : une notice entièrement retirée ou sans exemplaire est introuvable pour le public (RG-11). */
    public BookPublicDetail publicDetail(long bookId) {
        Book book = books.findById(bookId).orElseThrow(CatalogSearchService::notFound);
        List<Copy> visible = copies.findByBookIdOrderByCopyNumberAsc(bookId).stream()
                .filter(copy -> copy.getStatus() != CopyStatus.WITHDRAWN)
                .toList();
        if (visible.isEmpty()) {
            throw notFound();
        }
        Map<Long, LocalDate> dueDates = loanInfo.dueDatesOfActiveLoans(
                visible.stream().map(Copy::getId).toList());
        Counts counts = Counts.of(visible);
        return new BookPublicDetail(book.getId(), book.getIsbn(), book.getTitle(), book.getSubtitle(),
                CatalogViews.authors(book), CatalogViews.categories(book), book.getPublisher(),
                book.getPublicationYear(), LanguageView.of(book.getLanguage()), book.getPageCount(),
                book.getSummary(),
                visible.stream().map(copy -> new PublicCopy(copy.getShelfMark(), copy.getStatus(),
                        dueDates.get(copy.getId()))).toList(),
                counts.available(), counts.total());
    }

    public List<CategoryView> categoryOptions() {
        return categories.findAllByOrderByLabelAsc().stream().map(CategoryView::of).toList();
    }

    public List<LanguageView> languageOptions() {
        return languages.findAllByOrderByDisplayOrderAsc().stream().map(LanguageView::of).toList();
    }

    /** Mot-clé : mots dans titre/sous-titre/auteurs, ou ISBN si la saisie en est un (ARCH-catalogue §2). */
    static Specification<Book> keyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return (root, query, cb) -> null;
        }
        return BookSpecifications.matchesKeyword(keyword, Isbn.toIsbn13(keyword).orElse(null));
    }

    static Map<Long, Counts> countsOf(Collection<Long> bookIds, CopyRepository copies) {
        if (bookIds.isEmpty()) {
            return Map.of();
        }
        return copies.countsByBookIds(bookIds).stream()
                .collect(Collectors.toMap(CopyCounts::getBookId, Counts::of, (a, b) -> a));
    }

    private static BusinessException notFound() {
        return BusinessException.of(ErrorCode.BOOK_NOT_FOUND, "Ce livre est introuvable.");
    }

    /** Critères de la recherche publique. */
    public record PublicSearch(String keyword, Long categoryId, String languageCode, boolean availableOnly) {
    }
}
