package com.wust.dormitory.residency;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ResidencyCheckoutMapper {
    int appendAssignmentCancellation(
            @Param("batchId") long batchId,
            @Param("studentId") long studentId,
            @Param("operatorId") long operatorId,
            @Param("reason") String reason);

    int deleteActiveAssignment(
            @Param("batchId") long batchId,
            @Param("studentId") long studentId);
}
