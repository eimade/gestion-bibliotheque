package com.bibliotheque.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LikePatternsTest {

    @Test
    void neutraliseLesJokersSaisis() {
        assertThat(LikePatterns.contains(" 50%_de!rabais ")).isEqualTo("%50!%!_de!!rabais%");
    }

    @Test
    void saisieVideDesactiveLeFiltre() {
        assertThat(LikePatterns.contains("   ")).isNull();
        assertThat(LikePatterns.contains(null)).isNull();
    }
}
