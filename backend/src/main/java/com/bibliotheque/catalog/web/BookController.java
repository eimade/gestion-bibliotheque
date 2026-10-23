package com.bibliotheque.catalog.web;

import java.net.URI;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import com.bibliotheque.catalog.service.BookService;
import com.bibliotheque.catalog.service.CatalogViews.BookAdminDetail;
import com.bibliotheque.catalog.service.CatalogViews.BookAdminItem;
import com.bibliotheque.catalog.service.CatalogViews.CopyView;
import com.bibliotheque.catalog.service.CopyService;
import com.bibliotheque.catalog.web.CatalogRequests.AddCopiesRequest;
import com.bibliotheque.catalog.web.CatalogRequests.BookRequest;
import com.bibliotheque.common.security.AccountPrincipal;
import com.bibliotheque.common.web.PageRequests;
import com.bibliotheque.common.web.PageResponse;

/** Notices et ajout d'exemplaires côté personnel (US-009 → US-012). */
@RestController
@RequestMapping("/api/v1/books")
public class BookController {

    private final BookService books;
    private final CopyService copies;

    public BookController(BookService books, CopyService copies) {
        this.books = books;
        this.copies = copies;
    }

    @GetMapping
    public PageResponse<BookAdminItem> search(
            @RequestParam(required = false) @Size(min = 2, max = 100) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "false") boolean includeWithdrawn,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        var pageable = PageRequests.of(page, size, sort, Set.of("title", "publicationYear"), Sort.by("title"));
        return PageResponse.from(books.search(q, categoryId, includeWithdrawn, pageable), item -> item);
    }

    @GetMapping("/{id}")
    public BookAdminDetail get(@PathVariable long id) {
        return books.get(id);
    }

    @PostMapping
    public ResponseEntity<BookAdminDetail> create(@Valid @RequestBody BookRequest body,
                                                  @AuthenticationPrincipal AccountPrincipal principal) {
        BookAdminDetail created = books.create(body.toCommand(), principal.accountId());
        return ResponseEntity.created(URI.create("/api/v1/books/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public BookAdminDetail update(@PathVariable long id, @Valid @RequestBody BookRequest body,
                                  @AuthenticationPrincipal AccountPrincipal principal) {
        return books.update(id, body.toCommand(), principal.accountId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id, @AuthenticationPrincipal AccountPrincipal principal) {
        books.delete(id, principal.accountId());
    }

    @PostMapping("/{id}/copies")
    @ResponseStatus(HttpStatus.CREATED)
    public List<CopyView> addCopies(@PathVariable long id, @Valid @RequestBody AddCopiesRequest body,
                                    @AuthenticationPrincipal AccountPrincipal principal) {
        return copies.add(id, body.quantity(), body.shelfMark(), body.physicalCondition(), body.acquisitionDate(),
                principal.accountId());
    }
}
