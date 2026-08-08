package com.wust.dormitory.allocation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface OptimizedAllocationMapper {
    Map<String, Object> findBatchMetadata(@Param("batchId") long batchId);

    Map<String, Object> lockBatchMetadata(@Param("batchId") long batchId);

    int insertRun(Map<String, Object> values);

    int insertCandidates(@Param("runId") long runId, @Param("items") List<Map<String, Object>> items);

    Map<String, Object> findRunView(@Param("runId") long runId);

    Map<String, Object> lockRun(@Param("runId") long runId);

    List<Map<String, Object>> findCandidates(@Param("runId") long runId);

    List<Map<String, Object>> lockCandidates(
            @Param("runId") long runId,
            @Param("studentIds") List<Long> studentIds);

    int updateCandidate(Map<String, Object> values);

    int bumpRunVersion(@Param("runId") long runId, @Param("expectedVersion") int expectedVersion);

    int markSubmitted(@Param("runId") long runId, @Param("operatorId") long operatorId);
}
