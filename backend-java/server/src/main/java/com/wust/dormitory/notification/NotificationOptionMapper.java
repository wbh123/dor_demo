package com.wust.dormitory.notification;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface NotificationOptionMapper {
    List<Map<String, Object>> searchStudents(@Param("keyword") String keyword);

    List<Map<String, Object>> listBatches();

    List<Map<String, Object>> listMajors();

    List<Map<String, Object>> listBuildings();
}
