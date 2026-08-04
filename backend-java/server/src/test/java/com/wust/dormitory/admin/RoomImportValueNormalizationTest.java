package com.wust.dormitory.admin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoomImportValueNormalizationTest {
    @Test
    void normalizesHumanFriendlyRoomValuesToSystemCodes() {
        assertEquals("FOUR_PERSON", RoomImportService.normalizeRoomType("四人间"));
        assertEquals("FIVE_PERSON", RoomImportService.normalizeRoomType("5人间"));
        assertEquals("M", RoomImportService.normalizeGender("男生"));
        assertEquals("F", RoomImportService.normalizeGender("female"));
        assertEquals("DOMESTIC_ONLY", RoomImportService.normalizeResidentScope("国内生宿舍"));
        assertEquals("INTERNATIONAL_ONLY", RoomImportService.normalizeResidentScope("国际生"));
        assertEquals("MIXED", RoomImportService.normalizeResidentScope("不限"));
        assertEquals("ENABLED", RoomImportService.normalizeOperationalStatus("正常"));
        assertEquals("MAINTENANCE", RoomImportService.normalizeOperationalStatus("维修"));
    }
}
