package com.bibliotheque.catalog.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bibliotheque.catalog.service.CatalogViews.CopyView;
import com.bibliotheque.catalog.service.CopyService;
import com.bibliotheque.catalog.web.CatalogRequests.UpdateCopyRequest;
import com.bibliotheque.catalog.web.CatalogRequests.WithdrawCopyRequest;
import com.bibliotheque.common.security.AccountPrincipal;

/** Modification et retrait d'un exemplaire, identifié par son numéro (US-013). */
@RestController
@RequestMapping("/api/v1/copies")
public class CopyController {

    private static final String COPY_NUMBER = "\\d{8}";

    private final CopyService copies;

    public CopyController(CopyService copies) {
        this.copies = copies;
    }

    @PutMapping("/{copyNumber}")
    public CopyView update(@PathVariable @Pattern(regexp = COPY_NUMBER) String copyNumber,
                           @Valid @RequestBody UpdateCopyRequest body,
                           @AuthenticationPrincipal AccountPrincipal principal) {
        return copies.update(copyNumber, body.shelfMark(), body.physicalCondition(), body.version(),
                principal.accountId());
    }

    @PostMapping("/{copyNumber}/withdrawal")
    public CopyView withdraw(@PathVariable @Pattern(regexp = COPY_NUMBER) String copyNumber,
                             @Valid @RequestBody WithdrawCopyRequest body,
                             @AuthenticationPrincipal AccountPrincipal principal) {
        return copies.withdraw(copyNumber, body.reason(), principal.accountId());
    }
}
