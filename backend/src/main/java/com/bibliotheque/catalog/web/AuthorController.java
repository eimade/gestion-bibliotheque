package com.bibliotheque.catalog.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bibliotheque.catalog.service.AuthorService;
import com.bibliotheque.catalog.service.CatalogViews.AuthorView;
import com.bibliotheque.catalog.web.CatalogRequests.AuthorRequest;
import com.bibliotheque.common.security.AccountPrincipal;

/** Suggestions et création d'auteurs pour le formulaire de notice (US-009). */
@RestController
@RequestMapping("/api/v1/authors")
public class AuthorController {

    private final AuthorService authors;

    public AuthorController(AuthorService authors) {
        this.authors = authors;
    }

    @GetMapping
    public List<AuthorView> suggest(@RequestParam String q) {
        return authors.suggest(q);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AuthorView create(@Valid @RequestBody AuthorRequest body,
                             @AuthenticationPrincipal AccountPrincipal principal) {
        return authors.create(body.lastName(), body.firstName(), principal.accountId());
    }
}
