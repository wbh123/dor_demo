package com.wust.dormitory.allocation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AllocationSnapshotMapper {
    Long findBatchId(@Param("batchId") long batchId);

    List<Map<String, Object>> findEligibleStudents(@Param("batchId") long batchId);

    List<Map<String, Object>> findAvailableBeds(@Param("batchId") long batchId);

    List<Map<String, Object>> findLockedTeamMembers(@Param("batchId") long batchId);
}
