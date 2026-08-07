package com.wust.dormitory.admin.mapper;

import com.wust.dormitory.admin.model.persistence.BuildingCatalogRow;
import com.wust.dormitory.admin.model.persistence.BuildingStaticRow;
import com.wust.dormitory.admin.model.persistence.FloorCatalogRow;
import com.wust.dormitory.admin.model.persistence.MajorCatalogRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminCatalogMapper {
    List<MajorCatalogRow> findMajors(@Param("enabled") Boolean enabled);

    List<BuildingCatalogRow> findBuildings();

    List<BuildingStaticRow> findAllBuildingStatic();

    BuildingStaticRow findBuildingStatic(@Param("buildingId") long buildingId);

    List<FloorCatalogRow> findAllFloors();

    List<FloorCatalogRow> findFloors(@Param("buildingId") long buildingId);
}
