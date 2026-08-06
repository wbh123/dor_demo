package com.wust.dormitory.admin.mapper;

import com.wust.dormitory.admin.model.persistence.RoomCatalogRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoomCatalogMapper {
    List<RoomCatalogRow> findRooms(
            @Param("buildingId") Long buildingId,
            @Param("gender") String gender);
}
