package com.bibliotheque.common.exception;

import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduction unique des exceptions en {@code ProblemDetail} portant un {@code code} métier
 * (ADR-003). Une violation de contrainte ou de verrou optimiste donne un 409, jamais un 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Contraintes UNIQUE dont la violation a un code métier précis (filet de sécurité concurrentiel). */
    private static final Map<String, ErrorCode> UNIQUE_CONSTRAINT_CODES = Map.of(
            "uk_user_accounts_email", ErrorCode.EMAIL_ALREADY_USED,
            "uk_books_isbn", ErrorCode.ISBN_ALREADY_EXISTS,
            "uk_categories_label", ErrorCode.CATEGORY_ALREADY_EXISTS,
            "uk_loans_active_copy", ErrorCode.COPY_NOT_AVAILABLE,
            "uk_reservations_active_member_book", ErrorCode.RESERVATION_ALREADY_EXISTS);

    /** Détail propre à une contrainte, quand « valeur déjà utilisée » ne convient pas (AC-025-10). */
    private static final Map<ErrorCode, String> UNIQUE_CONSTRAINT_DETAILS = Map.of(
            ErrorCode.COPY_NOT_AVAILABLE, "Exemplaire déjà emprunté.",
            ErrorCode.RESERVATION_ALREADY_EXISTS, "Une réservation est déjà en cours pour ce livre.");

    static final String CONCURRENT_MODIFICATION_DETAIL =
            "Cet élément a été modifié entre-temps par un autre utilisateur.";

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex) {
        ProblemDetail problem = ProblemDetails.of(ex.status(), ex.code(), ex.getMessage());
        if (!ex.reasons().isEmpty()) {
            problem.setProperty("reasons", ex.reasons());
        }
        ex.properties().forEach(problem::setProperty);
        return problemResponse(problem);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return problemResponse(ProblemDetails.of(HttpStatus.CONFLICT, ErrorCode.CONCURRENT_MODIFICATION,
                CONCURRENT_MODIFICATION_DETAIL));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
        String message = String.valueOf(ex.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
        ErrorCode code = UNIQUE_CONSTRAINT_CODES.entrySet().stream()
                .filter(entry -> message.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(ErrorCode.CONCURRENT_MODIFICATION);
        String detail = code == ErrorCode.CONCURRENT_MODIFICATION
                ? CONCURRENT_MODIFICATION_DETAIL
                : UNIQUE_CONSTRAINT_DETAILS.getOrDefault(code, "Cette valeur est déjà utilisée.");
        return problemResponse(ProblemDetails.of(HttpStatus.CONFLICT, code, detail));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return problemResponse(ProblemDetails.of(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
                "Vous n'avez pas les droits nécessaires pour effectuer cette action."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        LOG.error("Erreur inattendue", ex);
        return problemResponse(ProblemDetails.of(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "Une erreur inattendue est survenue."));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail problem = ProblemDetails.of(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                "Les données envoyées sont invalides.");
        problem.setProperty("fieldErrors", ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorView(error.getField(), error.getCode(), error.getDefaultMessage()))
                .toList());
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /** Complète les ProblemDetail produits par Spring MVC (400, 404, 405…) avec un code et un détail français. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey(ProblemDetails.CODE))) {
            ErrorCode code = codeForFrameworkStatus(statusCode);
            problem.setProperty(ProblemDetails.CODE, code.name());
            problem.setDetail(frameworkDetail(code));
        }
        return response;
    }

    private static ErrorCode codeForFrameworkStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> ErrorCode.VALIDATION_FAILED;
            case 404 -> ErrorCode.ROUTE_NOT_FOUND;
            case 405, 406, 415 -> ErrorCode.REQUEST_NOT_SUPPORTED;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }

    private static String frameworkDetail(ErrorCode code) {
        return switch (code) {
            case VALIDATION_FAILED -> "Les données envoyées sont invalides.";
            case ROUTE_NOT_FOUND -> "La ressource demandée n'existe pas.";
            case REQUEST_NOT_SUPPORTED -> "Cette requête n'est pas prise en charge.";
            default -> "Une erreur inattendue est survenue.";
        };
    }

    private static ResponseEntity<ProblemDetail> problemResponse(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
