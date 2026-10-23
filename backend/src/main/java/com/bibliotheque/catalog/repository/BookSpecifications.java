package com.bibliotheque.catalog.repository;

import java.util.Arrays;
import java.util.List;

import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.jpa.domain.Specification;

import com.bibliotheque.catalog.domain.Author;
import com.bibliotheque.catalog.domain.Book;
import com.bibliotheque.catalog.domain.Category;
import com.bibliotheque.catalog.domain.Copy;
import com.bibliotheque.catalog.domain.CopyStatus;
import com.bibliotheque.common.web.LikePatterns;

/**
 * Critères de recherche du catalogue (RG-11, ARCH-catalogue §2). Tous les critères sont paramétrés ;
 * la collation de la base rend les comparaisons insensibles à la casse et aux accents.
 */
public final class BookSpecifications {

    /** Nombre maximal de mots pris en compte dans un mot-clé. */
    public static final int MAX_TERMS = 5;

    private BookSpecifications() {
    }

    /** Mots du mot-clé (séparés par espaces, tirets et apostrophes), 5 au plus. */
    public static List<String> terms(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return Arrays.stream(keyword.trim().split("[\\s\\-'’]+"))
                .filter(term -> !term.isBlank())
                .limit(MAX_TERMS)
                .toList();
    }

    /**
     * Chaque mot doit figurer dans le titre, le sous-titre ou le nom d'un auteur ; ou bien la notice a
     * l'ISBN donné ({@code isbn13} non nul quand le mot-clé est un ISBN valide).
     */
    public static Specification<Book> matchesKeyword(String keyword, String isbn13) {
        List<String> terms = terms(keyword);
        return (root, query, cb) -> {
            Predicate allTerms = cb.and(terms.stream()
                    .map(term -> termMatches(root, query, cb, LikePatterns.contains(term)))
                    .toArray(Predicate[]::new));
            return isbn13 == null ? allTerms : cb.or(allTerms, cb.equal(root.get("isbn"), isbn13));
        };
    }

    private static Predicate termMatches(Root<Book> root, CommonAbstractCriteria query,
                                         CriteriaBuilder cb, String pattern) {
        Subquery<Long> byAuthor = query.subquery(Long.class);
        Root<Book> book = byAuthor.from(Book.class);
        Join<Book, Author> author = book.join("authors");
        byAuthor.select(book.get("id")).where(
                cb.equal(book, root),
                cb.or(cb.like(author.get("lastName"), pattern, LikePatterns.ESCAPE),
                        cb.like(author.get("firstName"), pattern, LikePatterns.ESCAPE)));
        return cb.or(
                cb.like(root.get("title"), pattern, LikePatterns.ESCAPE),
                cb.like(root.get("subtitle"), pattern, LikePatterns.ESCAPE),
                cb.exists(byAuthor));
    }

    /** RG-11 : visible du public si au moins un exemplaire n'est pas retiré. */
    public static Specification<Book> visibleToPublic() {
        return (root, query, cb) -> cb.exists(copies(root, query, cb, CopyStatus.WITHDRAWN, false));
    }

    /** Gestion : exclut les notices entièrement retirées (au moins un exemplaire, tous retirés). */
    public static Specification<Book> notEntirelyWithdrawn() {
        return (root, query, cb) -> cb.or(
                cb.not(cb.exists(anyCopy(root, query, cb))),
                cb.exists(copies(root, query, cb, CopyStatus.WITHDRAWN, false)));
    }

    public static Specification<Book> hasAvailableCopy() {
        return (root, query, cb) -> cb.exists(copies(root, query, cb, CopyStatus.AVAILABLE, true));
    }

    public static Specification<Book> inCategory(Long categoryId) {
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<Book> book = sub.from(Book.class);
            Join<Book, Category> category = book.join("categories");
            sub.select(book.get("id")).where(cb.equal(book, root), cb.equal(category.get("id"), categoryId));
            return cb.exists(sub);
        };
    }

    public static Specification<Book> inLanguage(String languageCode) {
        return (root, query, cb) -> cb.equal(root.get("language").get("code"), languageCode);
    }

    /**
     * Tri par pertinence : titres commençant par le premier mot d'abord, puis ordre alphabétique.
     * Ignoré pour la requête de comptage de la pagination.
     */
    public static Specification<Book> orderedByRelevance(String keyword) {
        String startsWith = LikePatterns.startsWith(terms(keyword).stream().findFirst().orElse(""));
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                if (startsWith == null) {
                    query.orderBy(cb.asc(root.get("title")));
                } else {
                    query.orderBy(
                            cb.asc(cb.selectCase()
                                    .when(cb.like(root.get("title"), startsWith, LikePatterns.ESCAPE), 0)
                                    .otherwise(1)),
                            cb.asc(root.get("title")));
                }
            }
            return null;
        };
    }

    private static Subquery<Long> copies(Root<Book> root, CommonAbstractCriteria query,
                                         CriteriaBuilder cb, CopyStatus status, boolean equal) {
        Subquery<Long> sub = query.subquery(Long.class);
        Root<Copy> copy = sub.from(Copy.class);
        sub.select(copy.get("id")).where(
                cb.equal(copy.get("book"), root),
                equal ? cb.equal(copy.get("status"), status) : cb.notEqual(copy.get("status"), status));
        return sub;
    }

    private static Subquery<Long> anyCopy(Root<Book> root, CommonAbstractCriteria query,
                                          CriteriaBuilder cb) {
        Subquery<Long> sub = query.subquery(Long.class);
        Root<Copy> copy = sub.from(Copy.class);
        sub.select(copy.get("id")).where(cb.equal(copy.get("book"), root));
        return sub;
    }
}
