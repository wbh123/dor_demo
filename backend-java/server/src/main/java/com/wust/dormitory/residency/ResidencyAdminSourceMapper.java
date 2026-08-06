package com.wust.dormitory.residency;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ResidencyAdminSourceMapper {
    int markManualAdjustment(@Param("residencyId") long residencyId);
}
