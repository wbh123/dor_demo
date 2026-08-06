package com.wust.dormitory.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.Map;

@Mapper
interface MybatisSmokeMapper {
    Integer selectOne();

    Map<String, Object> selectMapRow();
}
