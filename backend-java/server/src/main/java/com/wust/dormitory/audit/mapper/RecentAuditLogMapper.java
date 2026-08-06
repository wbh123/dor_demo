package com.wust.dormitory.audit.mapper;

import com.wust.dormitory.audit.model.persistence.RecentAuditLogRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RecentAuditLogMapper {
    List<RecentAuditLogRow> findRecent(@Param("limit") int limit);
}
