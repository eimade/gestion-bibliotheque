package com.bibliotheque.common.web;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.common.exception.FieldErrorView;

/**
 * Construction validée d'une pagination (ADR-003 §5) : page ≥ 0, taille 1-100 (20 par défaut),
 * tri {@code champ,asc|desc} limité à une liste blanche de champs.
 */
public final class PageRequests {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private PageRequests() {
    }

    public static Pageable of(Integer page, Integer size, String sort, Set<String> allowedSortFields,
                              Sort defaultSort) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageNumber < 0) {
            throw invalid("page", "La page doit être supérieure ou égale à 0.");
        }
        if (pageSize < 1 || pageSize > MAX_SIZE) {
            throw invalid("size", "La taille de page doit être comprise entre 1 et " + MAX_SIZE + ".");
        }
        return PageRequest.of(pageNumber, pageSize, parseSort(sort, allowedSortFields, defaultSort));
    }

    private static Sort parseSort(String sort, Set<String> allowedSortFields, Sort defaultSort) {
        if (sort == null || sort.isBlank()) {
            return defaultSort;
        }
        String[] parts = sort.split(",", -1);
        if (parts.length != 2 || !allowedSortFields.contains(parts[0])) {
            throw invalid("sort", "Tri non autorisé : " + allowedSortFields + " suivi de ,asc ou ,desc.");
        }
        Sort.Direction direction = switch (parts[1].toLowerCase(Locale.ROOT)) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw invalid("sort", "Le sens du tri doit être asc ou desc.");
        };
        return Sort.by(direction, parts[0]);
    }

    private static BusinessException invalid(String field, String message) {
        return BusinessException.of(ErrorCode.VALIDATION_FAILED, "Les paramètres de pagination sont invalides.")
                .withProperty("fieldErrors", List.of(new FieldErrorView(field, "Invalid", message)));
    }
}
