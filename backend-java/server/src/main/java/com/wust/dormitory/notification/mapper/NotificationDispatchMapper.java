package com.wust.dormitory.notification.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface NotificationDispatchMapper {
    Map<String, Object> findTemplateRevision(@Param("templateRevisionId") long templateRevisionId);

    void insertTask(Map<String, Object> task);

    void insertRecipients(
            @Param("taskId") long taskId,
            @Param("studentIds") List<Long> studentIds);

    List<Map<String, Object>> findDueTasks();

    int claimTask(@Param("taskId") long taskId);

    int markSucceeded(@Param("taskId") long taskId);

    int markFailed(
            @Param("taskId") long taskId,
            @Param("reason") String reason);

    int cancelScheduledTask(
            @Param("taskId") long taskId,
            @Param("operatorId") long operatorId);

    List<Map<String, Object>> findStatusPage(
            @Param("limit") int limit,
            @Param("offset") int offset);

    List<Long> findPendingRecipients(
            @Param("taskId") long taskId,
            @Param("limit") int limit);

    int markRecipientsDelivered(
            @Param("taskId") long taskId,
            @Param("studentIds") List<Long> studentIds);
}
