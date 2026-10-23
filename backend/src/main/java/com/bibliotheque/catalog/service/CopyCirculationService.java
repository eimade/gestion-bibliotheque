package com.bibliotheque.catalog.service;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bibliotheque.catalog.domain.Author;
import com.bibliotheque.catalog.domain.Book;
import com.bibliotheque.catalog.domain.Copy;
import com.bibliotheque.catalog.domain.CopyStatus;
import com.bibliotheque.catalog.repository.BookRepository;
import com.bibliotheque.catalog.repository.CopyRepository;
import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;

/**
 * Exemplaires vus par le module circulation (ARCH-circulation §1) : lecture, verrou de ligne et
 * transitions de circulation. Le catalogue reste seul propriétaire de la table {@code copies}.
 */
@Service
public class CopyCirculationService {

    private static final EnumSet<CopyStatus> IN_CIRCULATION = EnumSet.of(CopyStatus.ON_LOAN, CopyStatus.RESERVED);

    private final CopyRepository copies;
    private final BookRepository books;

    public CopyCirculationService(CopyRepository copies, BookRepository books) {
        this.copies = copies;
        this.books = books;
    }

    /** Exemplaire et sa notice, sans entité JPA. */
    public record CopySnapshot(long id, String copyNumber, long bookId, String title, String authors,
                               String shelfMark, CopyStatus status) {

        static CopySnapshot of(Copy copy) {
            Book book = copy.getBook();
            return new CopySnapshot(copy.getId(), copy.getCopyNumber(), book.getId(), book.getTitle(),
                    authorNames(book), copy.getShelfMark(), copy.getStatus());
        }
    }

    /** RG-29 : exemplaires en rayon et exemplaires en circulation (empruntés ou mis de côté). */
    public record BookAvailability(long available, long inCirculation) {
    }

    /** Verrouille l'exemplaire pour la transaction en cours (ARCH-circulation §5). */
    @Transactional
    public CopySnapshot lockByNumber(String copyNumber) {
        return CopySnapshot.of(copies.lockByCopyNumber(copyNumber)
                .orElseThrow(() -> BusinessException.of(ErrorCode.COPY_NOT_FOUND, "Exemplaire inconnu.")));
    }

    @Transactional
    public CopySnapshot lockById(long copyId) {
        return CopySnapshot.of(copies.lockById(copyId)
                .orElseThrow(() -> new IllegalStateException("Exemplaire introuvable : " + copyId)));
    }

    @Transactional(readOnly = true)
    public Optional<CopySnapshot> findByNumber(String copyNumber) {
        return copies.findByCopyNumber(copyNumber).map(CopySnapshot::of);
    }

    @Transactional
    public void changeStatus(long copyId, CopyStatus target) {
        Copy copy = copies.findById(copyId)
                .orElseThrow(() -> new IllegalStateException("Exemplaire introuvable : " + copyId));
        copy.changeCirculationStatus(target);
    }

    @Transactional(readOnly = true)
    public Map<Long, CopySnapshot> describe(Collection<Long> copyIds) {
        if (copyIds.isEmpty()) {
            return Map.of();
        }
        return copies.findWithBookByIdIn(copyIds).stream()
                .map(CopySnapshot::of)
                .collect(Collectors.toMap(CopySnapshot::id, Function.identity()));
    }

    @Transactional(readOnly = true)
    public BookAvailability availability(long bookId) {
        return new BookAvailability(copies.countByBookIdAndStatus(bookId, CopyStatus.AVAILABLE),
                copies.countByBookIdAndStatusIn(bookId, IN_CIRCULATION));
    }

    /** Titre de la notice ; {@code BOOK_NOT_FOUND} si elle n'existe pas. */
    @Transactional(readOnly = true)
    public String titleOf(long bookId) {
        return books.findById(bookId).map(Book::getTitle)
                .orElseThrow(() -> BusinessException.of(ErrorCode.BOOK_NOT_FOUND, "Ce livre est introuvable."));
    }

    @Transactional(readOnly = true)
    public Map<Long, String> titlesOf(Collection<Long> bookIds) {
        if (bookIds.isEmpty()) {
            return Map.of();
        }
        return books.findAllById(bookIds).stream().collect(Collectors.toMap(Book::getId, Book::getTitle));
    }

    private static String authorNames(Book book) {
        List<Author> authors = book.getAuthors();
        return authors.stream()
                .map(author -> author.getFirstName() == null
                        ? author.getLastName()
                        : author.getFirstName() + " " + author.getLastName())
                .collect(Collectors.joining(", "));
    }
}
