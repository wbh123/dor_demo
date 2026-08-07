package com.wust.dormitory.admin.mapper;

import com.wust.dormitory.admin.model.persistence.StudentAdminDetailRow;
import com.wust.dormitory.admin.model.query.StudentAdminSortedQuery;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface StudentAdminSortedMapper {
    long countStudents(StudentAdminSortedQuery query);

    List<StudentAdminDetailRow> findStudents(StudentAdminSortedQuery query);
}
