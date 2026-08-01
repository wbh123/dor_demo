package com.wust.dormitory.student;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.realtime.RoomEventHub;
import com.wust.dormitory.selection.BedHoldService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TeamHoldReleaseService {
    private final NamedParameterJdbcTemplate jdbc;
    private final BedHoldService holdService;
    private final RoomEventHub eventHub;

    public TeamHoldReleaseService(NamedParameterJdbcTemplate jdbc, BedHoldService holdService,
                                  RoomEventHub eventHub) {
        this.jdbc = jdbc;
        this.holdService = holdService;
        this.eventHub = eventHub;
    }

    public void release(long batchId, long teamId, List<Long> bedIds, String token,
                        long leaderStudentId) {
        Integer leaderCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM selection_team
                WHERE id=:teamId AND batch_id=:batchId AND leader_student_id=:leaderId
                  AND team_status IN ('LOCKED','SELECTING')
                """, new MapSqlParameterSource()
                .addValue("teamId", teamId)
                .addValue("batchId", batchId)
                .addValue("leaderId", leaderStudentId), Integer.class);
        if (leaderCount == null || leaderCount == 0) {
            throw new BusinessException("TEAM_NOT_FOUND", "队伍不存在或你不是队长", HttpStatus.FORBIDDEN);
        }
        if (bedIds.isEmpty()) {
            throw new BusinessException("BED_REQUIRED", "床位列表不能为空");
        }
        List<Long> roomIds = jdbc.query("""
                SELECT DISTINCT room_id FROM bed WHERE id IN (:bedIds)
                """, Map.of("bedIds", bedIds), (rs, rowNum) -> rs.getLong(1));
        if (roomIds.size() != 1) {
            throw new BusinessException("TEAM_BEDS_INVALID", "队伍床位必须位于同一房间");
        }
        holdService.releaseTeam(batchId, bedIds, teamId, token);
        eventHub.publish(batchId, roomIds.getFirst(), "TEAM_BEDS_RELEASED", Map.of("bedIds", bedIds));
    }
}
