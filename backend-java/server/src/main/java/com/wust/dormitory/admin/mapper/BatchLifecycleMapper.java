package com.wust.dormitory.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface BatchLifecycleMapper {
    Map<String, Object> lockBatch(@Param("batchId") long batchId);

    int updateStatus(@Param("batchId") long batchId, @Param("status") String status);

    int insertStudentLocks(@Param("batchId") long batchId);

    int deleteStudentLocks(@Param("batchId") long batchId);
}
