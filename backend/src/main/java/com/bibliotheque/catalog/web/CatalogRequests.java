package com.bibliotheque.catalog.web;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.bibliotheque.catalog.domain.PhysicalCondition;
import com.bibliotheque.catalog.domain.WithdrawalReason;
import com.bibliotheque.catalog.service.BookCommand;

/** DTO de requête du catalogue (ARCH-catalogue §3). */
public final class CatalogRequests {

    private CatalogRequests() {
    }

    public record BookRequest(
            @Size(max = 20) String isbn,
            @NotBlank @Size(max = 255) String title,
            @Size(max = 255) String subtitle,
            @NotEmpty List<@NotNull Long> authorIds,
            @NotEmpty Set<@NotNull Long> categoryIds,
            @Size(max = 150) String publisher,
            @Min(1450) Integer publicationYear,
            @NotBlank @Size(min = 3, max = 3) String languageCode,
            @Positive Integer pageCount,
            @Size(max = 4000) String summary,
            Integer version) {

        BookCommand toCommand() {
            return new BookCommand(isbn, title, subtitle, authorIds, categoryIds, publisher, publicationYear,
                    languageCode, pageCount, summary, version);
        }
    }

    public record AuthorRequest(@NotBlank @Size(max = 100) String lastName, @Size(max = 100) String firstName) {
    }

    public record AddCopiesRequest(
            @Min(1) @Max(20) int quantity,
            @NotBlank @Size(max = 30) String shelfMark,
            @NotNull PhysicalCondition physicalCondition,
            @NotNull LocalDate acquisitionDate) {
    }

    public record UpdateCopyRequest(
            @NotBlank @Size(max = 30) String shelfMark,
            @NotNull PhysicalCondition physicalCondition,
            @NotNull Integer version) {
    }

    public record WithdrawCopyRequest(@NotNull WithdrawalReason reason) {
    }

    /** Longueur 2-50 après suppression des espaces : vérifiée par le service (RG-12). */
    public record CategoryRequest(@NotBlank String label, Integer version) {
    }
}
