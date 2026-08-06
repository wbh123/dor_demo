package com.wust.dormitory.admin.mapper;

import com.wust.dormitory.admin.model.persistence.BuildingCatalogRow;
import com.wust.dormitory.admin.model.persistence.MajorCatalogRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminCatalogMapper {
    List<MajorCatalogRow> findMajors(@Param("enabled") Boolean enabled);

    List<BuildingCatalogRow> findBuildings();
}
