package com.wust.dormitory.residency.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ResidencyMapper {
    Map<String, Object> findCurrentResidency(@Param("studentId") long studentId);

    List<Map<String, Object>> findResidencies(
            @Param("roomId") Long roomId,
            @Param("keyword") String keyword,
            @Param("bedMappingStatus") String bedMappingStatus);

    List<Map<String, Object>> findRoomSummaries();

    Map<String, Object> findBedRoom(@Param("bedId") long bedId);

    Map<String, Object> lockActiveResidency(@Param("studentId") long studentId);

    int insertAssignment(Map<String, Object> assignment);

    int updateBedAssignment(
            @Param("id") long id,
            @Param("bedId") long bedId,
            @Param("method") String method);

    Map<String, Object> lockResidency(@Param("residencyId") long residencyId);

    Map<String, Object> lockBed(@Param("bedId") long bedId);

    int countOtherActiveBedOccupants(
            @Param("bedId") long bedId,
            @Param("residencyId") long residencyId);

    int confirmBed(@Param("residencyId") long residencyId, @Param("bedId") long bedId);

    int endResidency(@Param("residencyId") long residencyId, @Param("reason") String reason);

    Map<String, Object> findResidency(@Param("residencyId") long residencyId);

    int insertHistory(Map<String, Object> history);
}
