package com.wust.dormitory.allocation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AllocationCommitMapper {
    Map<String, Object> lockBatch(@Param("batchId") long batchId);

    Map<String, Object> findExistingRun(
            @Param("batchId") long batchId,
            @Param("idempotencyKey") String idempotencyKey);

    List<Map<String, Object>> findUnassignedFailures(@Param("runId") long runId);

    int insertRun(Map<String, Object> values);

    int insertAssignment(Map<String, Object> values);

    int insertAssignmentHistory(Map<String, Object> values);

    int insertAssignedResult(Map<String, Object> values);

    int insertUnassignedResults(@Param("runId") long runId, @Param("items") List<Map<String, Object>> items);

    int completeTeams(@Param("teamIds") List<Long> teamIds);

    int finishBatch(@Param("batchId") long batchId);
}
