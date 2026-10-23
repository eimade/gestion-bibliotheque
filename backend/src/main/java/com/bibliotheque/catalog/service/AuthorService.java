package com.bibliotheque.catalog.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bibliotheque.catalog.domain.Author;
import com.bibliotheque.catalog.repository.AuthorRepository;
import com.bibliotheque.catalog.service.CatalogViews.AuthorView;
import com.bibliotheque.common.audit.AuditAction;
import com.bibliotheque.common.audit.AuditObjectType;
import com.bibliotheque.common.audit.AuditService;
import com.bibliotheque.common.web.LikePatterns;

/** Auteurs (US-009 : suggestions et création à la volée, AC-009-2). */
@Service
public class AuthorService {

    static final int SUGGESTIONS = 10;
    static final int MIN_QUERY = 2;

    private final AuthorRepository authors;
    private final AuditService audit;

    public AuthorService(AuthorRepository authors, AuditService audit) {
        this.authors = authors;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AuthorView> suggest(String query) {
        if (query == null || query.trim().length() < MIN_QUERY) {
            throw Validation.invalidField("q", "Saisissez au moins 2 caractères.");
        }
        return authors.suggest(LikePatterns.contains(query), PageRequest.of(0, SUGGESTIONS)).stream()
                .map(AuthorView::of)
                .toList();
    }

    @Transactional
    public AuthorView create(String lastName, String firstName, long actorAccountId) {
        Author author = authors.save(new Author(lastName.trim(), Validation.optional(firstName)));
        Map<String, Object> details = new HashMap<>();
        details.put("lastName", author.getLastName());
        if (author.getFirstName() != null) {
            details.put("firstName", author.getFirstName());
        }
        audit.recordAs(actorAccountId, AuditAction.CREATE, AuditObjectType.AUTHOR, String.valueOf(author.getId()),
                details);
        return AuthorView.of(author);
    }
}
