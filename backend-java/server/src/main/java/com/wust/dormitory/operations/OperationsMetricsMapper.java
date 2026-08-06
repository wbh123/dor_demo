package com.wust.dormitory.operations;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationsMetricsMapper {
    long countOccupiedBeds();

    long countActiveResidents();

    long countUnselectedStudents();
}
