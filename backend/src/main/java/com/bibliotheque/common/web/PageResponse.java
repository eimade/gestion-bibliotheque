package com.bibliotheque.common.web;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** Réponse paginée commune (ADR-003 §5). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
