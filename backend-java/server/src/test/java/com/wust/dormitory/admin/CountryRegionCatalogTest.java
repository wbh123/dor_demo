package com.wust.dormitory.admin;

import com.wust.dormitory.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CountryRegionCatalogTest {
    @Test
    void acceptsChineseAndEnglishCountryNames() {
        assertThat(CountryRegionCatalog.code("中国", "DOMESTIC")).isEqualTo("CN");
        assertThat(CountryRegionCatalog.code("Japan", "INTERNATIONAL")).isEqualTo("JP");
        assertThat(CountryRegionCatalog.name("KR")).contains("韩国");
    }

    @Test
    void internationalStudentRequiresRecognizableCountry() {
        assertThatThrownBy(() -> CountryRegionCatalog.code("", "INTERNATIONAL"))
                .isInstanceOf(BusinessException.class);
    }
}
