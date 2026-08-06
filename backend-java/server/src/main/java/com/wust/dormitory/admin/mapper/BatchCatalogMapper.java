package com.wust.dormitory.admin.mapper;

import com.wust.dormitory.admin.model.persistence.BatchCatalogRow;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface BatchCatalogMapper {
    List<BatchCatalogRow> findBatches();
}
