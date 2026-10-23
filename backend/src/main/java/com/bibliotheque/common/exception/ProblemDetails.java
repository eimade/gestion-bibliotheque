package com.bibliotheque.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Fabrique des {@link ProblemDetail} de l'application : statut, titre, détail et {@code code}. */
public final class ProblemDetails {

    public static final String CODE = "code";

    private ProblemDetails() {
    }

    public static ProblemDetail of(HttpStatusCode status, ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        HttpStatus resolved = HttpStatus.resolve(status.value());
        if (resolved != null) {
            problem.setTitle(resolved.getReasonPhrase());
        }
        problem.setProperty(CODE, code.name());
        return problem;
    }
}
