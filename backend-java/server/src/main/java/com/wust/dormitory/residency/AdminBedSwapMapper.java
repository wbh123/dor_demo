package com.wust.dormitory.residency;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface AdminBedSwapMapper {
    Map<String, Object> findActiveResidencyForUpdate(@Param("studentId") long studentId);

    Map<String, Object> findActiveAllocationForUpdate(@Param("studentId") long studentId);

    Map<String, Object> findBedForUpdate(@Param("bedId") long bedId);

    int countStudentRoomCompatible(
            @Param("studentId") long studentId,
            @Param("roomId") long roomId);

    int updateResidencyPlacement(
            @Param("residencyId") long residencyId,
            @Param("roomId") long roomId,
            @Param("bedId") long bedId,
            @Param("operatorId") long operatorId);

    int updateActiveAllocation(
            @Param("studentId") long studentId,
            @Param("bedId") long bedId);

    int insertResidencyHistory(
            @Param("residencyId") long residencyId,
            @Param("studentId") long studentId,
            @Param("roomId") long roomId,
            @Param("bedId") long bedId,
            @Param("operatorId") long operatorId,
            @Param("reason") String reason,
            @Param("previousJson") String previousJson,
            @Param("currentJson") String currentJson);
}
