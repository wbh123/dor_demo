package com.wust.dormitory.admin.mapper;

import com.wust.dormitory.admin.model.persistence.AdminDashboardStatsRow;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminDashboardMapper {
    AdminDashboardStatsRow findStats();
}
