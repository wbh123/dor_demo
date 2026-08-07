package com.wust.dormitory.matching.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface MatchingSchemeMapper {
    List<Map<String, Object>> findSchemes();

    int countSchemeCode(@Param("code") String code);

    Map<String, Object> findScheme(@Param("id") long id);

    Map<String, Object> findSchemeForUpdate(@Param("id") long id);

    int claimVersion(@Param("id") long id, @Param("expectedVersion") int expectedVersion);

    Integer findLatestRevisionForUpdate(@Param("code") String code);

    int deactivateAll();

    int insertScheme(Map<String, Object> values);

    Long findPolicySchemeIdForBatch(@Param("batchId") long batchId);

    Map<String, Object> findPolicyScheme(@Param("schemeId") long schemeId);
}
