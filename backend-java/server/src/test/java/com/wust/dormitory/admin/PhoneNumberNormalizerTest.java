package com.wust.dormitory.admin;

import com.wust.dormitory.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumberNormalizerTest {
    @Test
    void addsDialCodeFromNationality() {
        assertThat(PhoneNumberNormalizer.normalize("138 0000 0000", "CN"))
                .isEqualTo("+8613800000000");
        assertThat(PhoneNumberNormalizer.normalize("090-1234-5678", "JP"))
                .isEqualTo("+819012345678");
    }

    @Test
    void preservesExplicitInternationalNumber() {
        assertThat(PhoneNumberNormalizer.normalize("+44 7700 900123", "US"))
                .isEqualTo("+447700900123");
    }

    @Test
    void rejectsInvalidNumber() {
        assertThatThrownBy(() -> PhoneNumberNormalizer.normalize("12", "CN"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("国家码");
    }
}
