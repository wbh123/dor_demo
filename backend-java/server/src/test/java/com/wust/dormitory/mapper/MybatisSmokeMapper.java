package com.wust.dormitory.mapper;

import org.apache.ibatis.annotations.Mapper;

@Mapper
interface MybatisSmokeMapper {
    Integer selectOne();
}
