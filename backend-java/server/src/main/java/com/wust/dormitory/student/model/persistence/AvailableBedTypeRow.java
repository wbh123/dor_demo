package com.wust.dormitory.student.model.persistence;

public record AvailableBedTypeRow(
        Long roomId,
        String bedType,
        Integer amount) {

    public int count() {
        return amount == null ? 0 : amount;
    }
}
