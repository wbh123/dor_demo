package com.wust.dormitory.admin.mapper;

import com.wust.dormitory.admin.model.persistence.StudentAdminDetailRow;
import com.wust.dormitory.admin.model.persistence.StudentCatalogRow;
import com.wust.dormitory.admin.model.query.StudentAdminDetailQuery;
import com.wust.dormitory.admin.model.query.StudentCatalogQuery;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface StudentAdminMapper {
    long countStudents(StudentCatalogQuery query);

    List<StudentCatalogRow> findStudents(StudentCatalogQuery query);

    long countDetailedStudents(StudentAdminDetailQuery query);

    List<StudentAdminDetailRow> findDetailedStudents(StudentAdminDetailQuery query);
}
