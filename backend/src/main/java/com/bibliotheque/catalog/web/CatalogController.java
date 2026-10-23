package com.bibliotheque.catalog.web;

import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bibliotheque.catalog.service.CatalogSearchService;
import com.bibliotheque.catalog.service.CatalogSearchService.PublicSearch;
import com.bibliotheque.catalog.service.CatalogViews.BookPublicDetail;
import com.bibliotheque.catalog.service.CatalogViews.BookSearchItem;
import com.bibliotheque.catalog.service.CatalogViews.CategoryView;
import com.bibliotheque.catalog.service.CatalogViews.LanguageView;
import com.bibliotheque.common.web.PageRequests;
import com.bibliotheque.common.web.PageResponse;

/** Catalogue public, accessible sans connexion (US-007, US-008). */
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    private final CatalogSearchService catalog;

    public CatalogController(CatalogSearchService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/books")
    public PageResponse<BookSearchItem> search(
            @RequestParam(required = false) @Size(min = 2, max = 100) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @Pattern(regexp = "[a-z]{3}") String language,
            @RequestParam(defaultValue = "false") boolean available,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) @Pattern(regexp = "title,asc|publicationYear,desc") String sort) {
        // Sans tri explicite, pertinence si un mot-clé est saisi (ECR-08)
        boolean byRelevance = sort == null && q != null && !q.isBlank();
        Pageable pageable = PageRequests.of(page, size, sort, Set.of("title", "publicationYear"),
                byRelevance ? Sort.unsorted() : Sort.by("title"));
        return PageResponse.from(
                catalog.search(new PublicSearch(q, categoryId, language, available), pageable, byRelevance),
                item -> item);
    }

    @GetMapping("/books/{id}")
    public BookPublicDetail book(@PathVariable long id) {
        return catalog.publicDetail(id);
    }

    @GetMapping("/categories")
    public List<CategoryView> categories() {
        return catalog.categoryOptions();
    }

    @GetMapping("/languages")
    public List<LanguageView> languages() {
        return catalog.languageOptions();
    }
}
