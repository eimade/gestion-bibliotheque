package com.bibliotheque.common.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import com.bibliotheque.common.exception.ErrorCode;
import com.bibliotheque.common.exception.ProblemDetails;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Écrit une réponse {@code ProblemDetail} depuis la chaîne de filtres, hors Spring MVC. */
final class ProblemDetailWriter {

    private final ObjectMapper objectMapper;

    ProblemDetailWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void write(HttpServletResponse response, HttpStatus status, ErrorCode code, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ProblemDetails.of(status, code, detail));
    }
}
