package com.wust.dormitory.notification;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NotificationRecipientMapper {
    List<Long> findRecipients(
            @Param("batchIds") List<Long> batchIds,
            @Param("majorIds") List<Long> majorIds,
            @Param("gradeYears") List<Integer> gradeYears,
            @Param("degreeLevels") List<String> degreeLevels,
            @Param("studentCategories") List<String> studentCategories,
            @Param("buildingIds") List<Long> buildingIds,
            @Param("unselectedOnly") boolean unselectedOnly,
            @Param("pendingReviewOnly") boolean pendingReviewOnly);
}
