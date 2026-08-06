package com.wust.dormitory.export;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ExportTaskMapper {
    Map<String, Object> findNextQueued();

    int claim(@Param("taskId") long taskId);

    Map<String, Object> downloadRecord(@Param("taskId") long taskId);

    List<Map<String, Object>> auditRows(@Param("filters") Map<String, Object> filters);

    List<Map<String, Object>> reportRows(@Param("filters") Map<String, Object> filters);
}
