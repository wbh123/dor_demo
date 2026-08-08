package com.wust.dormitory.importworkflow.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface ImportRollbackMapper {
    int deleteStudentUser(@Param("studentId") long studentId);

    int deleteStudent(@Param("studentId") long studentId);

    int restoreStudent(Map<String, Object> state);

    int restoreUser(Map<String, Object> state);

    int deleteRoomBeds(@Param("roomId") long roomId);

    int deleteRoom(@Param("roomId") long roomId);

    int deleteFloorIfEmpty(@Param("floorId") long floorId);

    int deleteBuildingIfEmpty(@Param("buildingId") long buildingId);

    int restoreRoom(Map<String, Object> state);
}
