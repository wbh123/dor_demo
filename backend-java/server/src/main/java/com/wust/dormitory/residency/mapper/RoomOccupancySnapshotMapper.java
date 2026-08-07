package com.wust.dormitory.residency.mapper;

import com.wust.dormitory.residency.model.persistence.RoomOccupancySnapshotRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoomOccupancySnapshotMapper {
    List<RoomOccupancySnapshotRow> findSnapshots(
            @Param("batchId") long batchId,
            @Param("roomIds") List<Long> roomIds);
}
