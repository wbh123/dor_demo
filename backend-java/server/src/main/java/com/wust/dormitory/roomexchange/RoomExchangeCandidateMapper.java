package com.wust.dormitory.roomexchange;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface RoomExchangeCandidateMapper {
    Map<String, Object> findExactCandidate(
            @Param("requesterStudentId") long requesterStudentId,
            @Param("studentNumber") String studentNumber,
            @Param("studentName") String studentName,
            @Param("buildingId") long buildingId,
            @Param("roomNumber") String roomNumber);

    List<Map<String, Object>> listCandidateBuildings(
            @Param("requesterStudentId") long requesterStudentId);
}
