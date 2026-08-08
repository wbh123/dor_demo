package com.wust.dormitory.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface RoomLayoutMapper {
    Map<String, Object> findRoomLayout(@Param("roomId") long roomId);

    List<Map<String, Object>> findBeds(@Param("roomId") long roomId);

    Map<String, Object> lockRoom(@Param("roomId") long roomId);

    List<Map<String, Object>> lockRoomBeds(@Param("roomId") long roomId);

    int updateRoomVersioned(
            @Param("roomId") long roomId,
            @Param("roomType") String roomType,
            @Param("capacity") int capacity,
            @Param("expectedVersion") long expectedVersion);

    int updateIndependentBedType(@Param("bedId") long bedId, @Param("bedType") String bedType);

    int deleteBedScope(@Param("bedId") long bedId);

    int deletePlacement(@Param("bedId") long bedId);

    int retireBed(@Param("bedId") long bedId, @Param("roomId") long roomId);

    int updateRepresentativeAfterCollapse(
            @Param("bedId") long bedId,
            @Param("roomId") long roomId,
            @Param("bedType") String bedType);

    int deleteFrame(@Param("frameId") long frameId, @Param("roomId") long roomId);

    int insertFrame(Map<String, Object> values);

    int updateSourceToUpper(@Param("bedId") long bedId, @Param("frameId") long frameId);

    int insertLowerBed(Map<String, Object> values);

    int copyBedScope(@Param("newBedId") long newBedId, @Param("sourceBedId") long sourceBedId);

    int batchUpsertPlacements(@Param("items") List<Map<String, Object>> items);

    int nextPosition(@Param("roomId") long roomId);

    int countBedCode(@Param("roomId") long roomId, @Param("bedCode") String bedCode);
}
