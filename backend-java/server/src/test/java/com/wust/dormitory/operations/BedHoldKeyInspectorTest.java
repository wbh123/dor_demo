package com.wust.dormitory.operations;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BedHoldKeyInspectorTest {
    @Test
    void parsesTheActualBedHoldKeyFormat() {
        BedHoldKeyInspector.BatchBed parsed = BedHoldKeyInspector.parseKey(
                "dormitory:batch:31:bed:208:hold");

        assertThat(parsed).isNotNull();
        assertThat(parsed.batchId()).isEqualTo(31L);
        assertThat(parsed.bedId()).isEqualTo(208L);
        assertThat(BedHoldKeyInspector.HOLD_PATTERN)
                .isEqualTo("dormitory:batch:*:bed:*:hold");
    }

    @Test
    void rejectsLegacyOrMalformedKeyFormats() {
        assertThat(BedHoldKeyInspector.parseKey("bed:hold:208")).isNull();
        assertThat(BedHoldKeyInspector.parseKey("student:hold:31:208")).isNull();
        assertThat(BedHoldKeyInspector.parseKey("dormitory:batch:31:bed:208")).isNull();
        assertThat(BedHoldKeyInspector.parseKey("dormitory:batch:x:bed:208:hold")).isNull();
    }
}
