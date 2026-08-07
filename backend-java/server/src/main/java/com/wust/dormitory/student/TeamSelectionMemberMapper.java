package com.wust.dormitory.student;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface TeamSelectionMemberMapper {
    int countLeaderAccess(
            @Param("teamId") long teamId,
            @Param("studentId") long studentId);

    List<Map<String, Object>> findConfirmedMembers(@Param("teamId") long teamId);
}
