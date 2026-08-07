package com.wust.dormitory.student.model.persistence;

public record RoommateFeatureRow(
        Long roomId,
        String featureVectorJson) {
}
