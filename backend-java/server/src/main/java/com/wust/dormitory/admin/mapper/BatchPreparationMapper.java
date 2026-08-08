package com.wust.dormitory.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BatchPreparationMapper {
    String findBatchStatus(@Param("batchId") long batchId);

    int insertEligibleStudents(@Param("batchId") long batchId);

    int insertEnabledBuildings(@Param("batchId") long batchId);
}
