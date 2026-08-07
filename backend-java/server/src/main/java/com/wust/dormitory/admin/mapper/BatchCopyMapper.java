package com.wust.dormitory.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface BatchCopyMapper {
    Map<String, Object> findSourceBatchForUpdate(@Param("batchId") long batchId);

    int countBatchCode(@Param("batchCode") String batchCode);

    Map<String, Object> validateTemplateReferences(
            @Param("questionnaireId") long questionnaireId,
            @Param("schemeId") long schemeId,
            @Param("ruleTemplateId") long ruleTemplateId);

    Map<String, Object> findScopeCounts(@Param("batchId") long batchId);

    List<String> findUnavailableResources(
            @Param("batchId") long batchId,
            @Param("limit") int limit);

    void insertBatch(Map<String, Object> batch);

    int copyBuildingScope(
            @Param("sourceBatchId") long sourceBatchId,
            @Param("newBatchId") long newBatchId);

    int copyRoomScope(
            @Param("sourceBatchId") long sourceBatchId,
            @Param("newBatchId") long newBatchId);

    int copyBedScope(
            @Param("sourceBatchId") long sourceBatchId,
            @Param("newBatchId") long newBatchId);
}
