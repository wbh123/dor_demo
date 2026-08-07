package com.wust.dormitory.admin;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AdminResidencyAdjustmentMapper {
    List<Map<String, Object>> findCompatibleBeds(
            @Param("studentId") long studentId,
            @Param("currentRoomId") long currentRoomId,
            @Param("currentBedId") Long currentBedId);
}
