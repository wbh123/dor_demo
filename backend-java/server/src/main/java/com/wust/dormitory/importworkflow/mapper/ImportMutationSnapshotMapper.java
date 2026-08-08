package com.wust.dormitory.importworkflow.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface ImportMutationSnapshotMapper {
    Map<String, Object> findStudentSnapshotByNumber(@Param("studentNumber") String studentNumber);

    Map<String, Object> findStudentSnapshot(@Param("studentId") long studentId);

    Map<String, Object> findRoomSnapshot(@Param("roomId") long roomId);

    int countActiveResidents(@Param("roomId") long roomId);
}
