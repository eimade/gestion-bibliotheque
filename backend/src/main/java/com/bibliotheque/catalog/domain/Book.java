package com.bibliotheque.catalog.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.ListIndexBase;

/** Notice bibliographique (ENT-Livre). */
@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "isbn", columnDefinition = "char(13)")
    private String isbn;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "subtitle", length = 255)
    private String subtitle;

    @Column(name = "publisher", length = 150)
    private String publisher;

    @Column(name = "publication_year")
    private Short publicationYear;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "language_code", nullable = false, columnDefinition = "char(3)")
    private Language language;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "summary", length = 4000)
    private String summary;

    /** Ordre d'affichage des auteurs, à partir de 1 ({@code ck_book_authors_order}). */
    @ManyToMany
    @JoinTable(name = "book_authors",
            joinColumns = @JoinColumn(name = "book_id"),
            inverseJoinColumns = @JoinColumn(name = "author_id"))
    @OrderColumn(name = "author_order", columnDefinition = "smallint")
    @ListIndexBase(1)
    private List<Author> authors = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "book_categories",
            joinColumns = @JoinColumn(name = "book_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<Category> categories = new LinkedHashSet<>();

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    protected Book() {
    }

    public Book(BookDescription description, Language language, List<Author> authors, Set<Category> categories) {
        applyDescription(description, language);
        this.authors.addAll(authors);
        this.categories.addAll(categories);
    }

    /** Met à jour les champs descriptifs (US-010). Les auteurs se remplacent en deux temps (ARCH-catalogue §2). */
    public void describe(BookDescription description, Language newLanguage) {
        applyDescription(description, newLanguage);
    }

    private void applyDescription(BookDescription description, Language newLanguage) {
        this.isbn = description.isbn();
        this.title = description.title();
        this.subtitle = description.subtitle();
        this.publisher = description.publisher();
        this.publicationYear = description.publicationYear() == null ? null
                : description.publicationYear().shortValue();
        this.pageCount = description.pageCount();
        this.summary = description.summary();
        this.language = newLanguage;
    }

    /** Première étape du remplacement des auteurs : la liste vidée doit être écrite avant d'ajouter la nouvelle. */
    public void clearAuthors() {
        authors.clear();
    }

    public void addAuthors(Collection<Author> newAuthors) {
        authors.addAll(newAuthors);
    }

    public void replaceCategories(Collection<Category> newCategories) {
        categories.clear();
        categories.addAll(newCategories);
    }

    public Long getId() {
        return id;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getPublisher() {
        return publisher;
    }

    public Integer getPublicationYear() {
        return publicationYear == null ? null : publicationYear.intValue();
    }

    public Language getLanguage() {
        return language;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public String getSummary() {
        return summary;
    }

    public List<Author> getAuthors() {
        return List.copyOf(authors);
    }

    public Set<Category> getCategories() {
        return Set.copyOf(categories);
    }

    public Integer getVersion() {
        return version;
    }
}
