package com.wust.dormitory.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface BuildingManagementMapper {
    List<Map<String, Object>> listDetails();

    Map<String, Object> findForUpdate(@Param("buildingId") long buildingId);

    int countDuplicateCode(
            @Param("buildingId") long buildingId,
            @Param("buildingCode") String buildingCode);

    int countIncompatibleRooms(
            @Param("buildingId") long buildingId,
            @Param("gender") String gender,
            @Param("educationScope") String educationScope,
            @Param("residentScope") String residentScope);

    int countRoomsAboveFloor(
            @Param("buildingId") long buildingId,
            @Param("floorCount") int floorCount);

    int updateBuilding(
            @Param("buildingId") long buildingId,
            @Param("buildingCode") String buildingCode,
            @Param("buildingName") String buildingName,
            @Param("gender") String gender,
            @Param("educationScope") String educationScope,
            @Param("residentScope") String residentScope,
            @Param("enabled") boolean enabled);

    int enableFloorsWithinRange(
            @Param("buildingId") long buildingId,
            @Param("floorCount") int floorCount);

    int disableFloorsAboveRange(
            @Param("buildingId") long buildingId,
            @Param("floorCount") int floorCount);

    int insertFloorIfMissing(
            @Param("buildingId") long buildingId,
            @Param("floorNumber") int floorNumber,
            @Param("floorName") String floorName);
}
