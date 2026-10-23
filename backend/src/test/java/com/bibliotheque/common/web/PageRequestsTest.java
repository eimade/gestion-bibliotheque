package com.bibliotheque.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;

class PageRequestsTest {

    private static final Set<String> FIELDS = Set.of("lastName", "role");
    private static final Sort DEFAULT = Sort.by("lastName");

    @Test
    void valeursParDefaut() {
        Pageable pageable = PageRequests.of(null, null, null, FIELDS, DEFAULT);

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort()).isEqualTo(DEFAULT);
    }

    @Test
    void triExplicite() {
        assertThat(PageRequests.of(2, 50, "role,DESC", FIELDS, DEFAULT).getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "role"));
    }

    @Test
    void tailleMaximaleAcceptee() {
        assertThat(PageRequests.of(0, 100, null, FIELDS, DEFAULT).getPageSize()).isEqualTo(100);
    }

    @ParameterizedTest(name = "page={0}, taille={1}, tri={2}")
    @CsvSource(value = {
            "-1, 20, ",
            "0, 0, ",
            "0, 101, ",
            "0, 20, email,asc",
            "0, 20, role,haut",
            "0, 20, role"
    }, nullValues = "")
    void parametresInvalides(Integer page, Integer size, String sort) {
        assertThatThrownBy(() -> PageRequests.of(page, size, sort, FIELDS, DEFAULT))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }
}
