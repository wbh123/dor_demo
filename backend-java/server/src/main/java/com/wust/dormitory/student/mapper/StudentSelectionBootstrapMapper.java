package com.wust.dormitory.student.mapper;

import com.wust.dormitory.student.model.persistence.StudentSelectionActiveTeamRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StudentSelectionBootstrapMapper {
    StudentSelectionActiveTeamRow findActiveTeam(
            @Param("batchId") long batchId,
            @Param("studentId") long studentId);
}
