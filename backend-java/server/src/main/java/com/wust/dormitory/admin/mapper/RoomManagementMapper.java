package com.wust.dormitory.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface RoomManagementMapper {
    List<Map<String, Object>> findBuildings();

    Long findDefaultCampusId();

    int countBuildingByCode(@Param("code") String code);

    void insertBuilding(Map<String, Object> building);

    void batchInsertFloors(
            @Param("buildingId") long buildingId,
            @Param("floorNumbers") List<Integer> floorNumbers);

    Map<String, Object> findBuildingForValidation(@Param("buildingId") long buildingId);

    Long findFloorId(
            @Param("buildingId") long buildingId,
            @Param("floorNumber") int floorNumber);

    int countRoomNumber(@Param("floorId") long floorId, @Param("roomNumber") String roomNumber);

    void insertRoom(Map<String, Object> room);

    void batchInsertBeds(
            @Param("roomId") long roomId,
            @Param("positions") List<Integer> positions);

    Map<String, Object> lockRoomForUpdate(@Param("roomId") long roomId);

    int countPhysicalBeds(@Param("roomId") long roomId);

    int countIncompatibleResidents(
            @Param("roomId") long roomId,
            @Param("gender") String gender,
            @Param("residentScope") String residentScope,
            @Param("educationScope") String educationScope);

    int updateRoom(Map<String, Object> room);
}
