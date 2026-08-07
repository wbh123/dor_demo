package com.wust.dormitory.analytics.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface BatchAnalyticsSnapshotMapper {
    Map<String, Object> findBatch(@Param("batchId") long batchId);

    Map<String, Object> findSnapshot(
            @Param("batchId") long batchId,
            @Param("metricVersion") String metricVersion);

    List<Long> findMissingFinishedBatchIds(
            @Param("metricVersion") String metricVersion,
            @Param("limit") int limit);

    int insertStudentFacts(
            @Param("batchId") long batchId,
            @Param("snapshotAt") LocalDateTime snapshotAt);

    Map<String, Object> findAggregateMetrics(@Param("batchId") long batchId);

    int insertSnapshot(
            @Param("batchId") long batchId,
            @Param("metricVersion") String metricVersion,
            @Param("metricsJson") String metricsJson,
            @Param("dimensionsJson") String dimensionsJson,
            @Param("updatedAt") LocalDateTime updatedAt);
}
