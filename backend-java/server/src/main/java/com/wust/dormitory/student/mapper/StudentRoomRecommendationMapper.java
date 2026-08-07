package com.wust.dormitory.student.mapper;

import com.wust.dormitory.student.model.persistence.AvailableBedRow;
import com.wust.dormitory.student.model.persistence.AvailableBedTypeRow;
import com.wust.dormitory.student.model.persistence.RoomRecommendationCandidateRow;
import com.wust.dormitory.student.model.persistence.RoommateFeatureRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StudentRoomRecommendationMapper {
    boolean isBatchAccessible(
            @Param("batchId") long batchId,
            @Param("studentId") long studentId);

    String findBatchFeature(
            @Param("batchId") long batchId,
            @Param("studentId") long studentId);

    List<RoomRecommendationCandidateRow> findCandidateRooms(
            @Param("batchId") long batchId,
            @Param("roomId") Long roomId);

    List<RoommateFeatureRow> findRoommateFeatures(
            @Param("batchId") long batchId,
            @Param("roomIds") List<Long> roomIds);

    List<AvailableBedTypeRow> findAvailableBedTypes(
            @Param("batchId") long batchId,
            @Param("roomIds") List<Long> roomIds);

    List<AvailableBedRow> findAvailableBeds(
            @Param("batchId") long batchId,
            @Param("roomId") long roomId);
}
