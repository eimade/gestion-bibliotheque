package com.bibliotheque.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.bibliotheque.common.exception.BusinessException;
import com.bibliotheque.common.exception.ErrorCode;

class IsbnTest {

    @ParameterizedTest(name = "« {0} » → 9782070360024")
    @ValueSource(strings = {"9782070360024", "978-2-07-036002-4", " 978 2 07 036002 4 ", "2070360024", "2-07-036002-4"})
    void normaliseUnIsbnValideEnIsbn13(String raw) {
        assertThat(Isbn.toIsbn13(raw)).contains("9782070360024");
    }

    @Test
    void accepteUnIsbn10DontLaCleEstX() {
        assertThat(Isbn.toIsbn13("080442957X")).contains("9780804429573");
    }

    @ParameterizedTest(name = "refuse « {0} »")
    @ValueSource(strings = {"9782070360025", "2070360025", "12345", "abcdefghij", "X804429570", "9772070360024"})
    void refuseUnIsbnInvalide(String raw) {
        assertThat(Isbn.toIsbn13(raw)).isEmpty();
    }

    @Test
    void saisieVideFacultative() {
        assertThat(Isbn.normalizeOptional("  ")).isNull();
        assertThat(Isbn.normalizeOptional(null)).isNull();
        assertThat(Isbn.toIsbn13(null)).isEmpty();
    }

    @Test
    void saisieInvalideRefuseeAvecLeChamp() {
        assertThatThrownBy(() -> Isbn.normalizeOptional("9782070360025"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ErrorCode.ISBN_INVALID);
                    assertThat(ex.properties()).containsEntry("field", "isbn");
                });
    }
}
