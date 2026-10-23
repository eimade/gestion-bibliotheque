package com.bibliotheque.catalog.web;

import java.util.Set;

import jakarta.validation.Valid;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bibliotheque.catalog.service.CatalogViews.CategoryResponse;
import com.bibliotheque.catalog.service.CategoryService;
import com.bibliotheque.catalog.web.CatalogRequests.CategoryRequest;
import com.bibliotheque.common.security.AccountPrincipal;
import com.bibliotheque.common.web.PageRequests;
import com.bibliotheque.common.web.PageResponse;

/** Gestion des catégories (US-014). */
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categories;

    public CategoryController(CategoryService categories) {
        this.categories = categories;
    }

    @GetMapping
    public PageResponse<CategoryResponse> list(@RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size,
                                               @RequestParam(required = false) String sort) {
        var pageable = PageRequests.of(page, size, sort, Set.of("label"), Sort.by("label"));
        return PageResponse.from(categories.list(pageable), item -> item);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CategoryRequest body,
                                   @AuthenticationPrincipal AccountPrincipal principal) {
        return categories.create(body.label(), principal.accountId());
    }

    @PutMapping("/{id}")
    public CategoryResponse rename(@PathVariable long id, @Valid @RequestBody CategoryRequest body,
                                   @AuthenticationPrincipal AccountPrincipal principal) {
        return categories.rename(id, body.label(), body.version(), principal.accountId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id, @AuthenticationPrincipal AccountPrincipal principal) {
        categories.delete(id, principal.accountId());
    }
}
