package com.bibliotheque.common.exception;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Refus métier traduit en {@code ProblemDetail} (ADR-003) : code stable, message français,
 * motifs multiples éventuels et propriétés contextuelles. Immuable : chaque méthode {@code with*}
 * renvoie une nouvelle instance.
 */
public final class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final HttpStatus status;
    private final List<String> reasons;
    private final Map<String, Object> properties;

    private BusinessException(ErrorCode code, HttpStatus status, String detail,
                              List<String> reasons, Map<String, Object> properties) {
        super(detail);
        this.code = code;
        this.status = status;
        this.reasons = List.copyOf(reasons);
        this.properties = Map.copyOf(properties);
    }

    public static BusinessException of(ErrorCode code, String detail) {
        return new BusinessException(code, code.defaultStatus(), detail, List.of(), Map.of());
    }

    public BusinessException withStatus(HttpStatus newStatus) {
        return new BusinessException(code, newStatus, getMessage(), reasons, properties);
    }

    public BusinessException withReasons(List<String> newReasons) {
        return new BusinessException(code, status, getMessage(), newReasons, properties);
    }

    public BusinessException withProperty(String name, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(properties);
        copy.put(name, value);
        return new BusinessException(code, status, getMessage(), reasons, copy);
    }

    public ErrorCode code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public List<String> reasons() {
        return reasons;
    }

    public Map<String, Object> properties() {
        return properties;
    }
}
