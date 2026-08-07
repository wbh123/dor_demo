package com.wust.dormitory.admin;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface RoomLayoutOccupancyGuardMapper {
    List<Map<String, Object>> findLayoutBeds(@Param("roomId") long roomId);
}
