package com.wust.dormitory.admin.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.Map;

@Mapper
public interface MajorManagementMapper {
    Map<String, Object> findMajor(long id);

    int insertMajor(Map<String, Object> values);

    int updateMajor(Map<String, Object> values);
}
