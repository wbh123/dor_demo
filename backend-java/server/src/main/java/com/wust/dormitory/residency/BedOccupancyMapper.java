package com.wust.dormitory.residency;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface BedOccupancyMapper {
    List<Map<String, Object>> findRoomBedOccupancy(@Param("roomId") long roomId);
}
