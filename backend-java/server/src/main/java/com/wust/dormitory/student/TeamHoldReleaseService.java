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
import java.util.Set;

@Service
public class TeamHoldReleaseService {
    private static final Set<String> HOLD_CAPABLE_STATUSES = Set.of("LOCKED", "SELECTING");

    private final NamedParameterJdbcTemplate jdbc;
    private final BedHoldService holdService;
    private final RoomEventHub eventHub;

    public TeamHoldReleaseService(
            NamedParameterJdbcTemplate jdbc,
            BedHoldService holdService,
            RoomEventHub eventHub) {
        this.jdbc = jdbc;
        this.holdService = holdService;
        this.eventHub = eventHub;
    }

    public void requireNoActiveHoldForLeader(long teamId, long leaderStudentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, batch_id, team_status
                FROM selection_team
                WHERE id=:teamId AND leader_student_id=:leaderStudentId
                FOR UPDATE
                """, new MapSqlParameterSource()
                .addValue("teamId", teamId)
                .addValue("leaderStudentId", leaderStudentId));
        if (!rows.isEmpty()) {
            assertNoActiveTeamHold(rows.getFirst());
        }
    }

    public void requireNoActiveHoldForMember(long teamId, long studentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT team.id, team.batch_id, team.team_status
                FROM selection_team team
                JOIN selection_team_member member ON member.team_id=team.id
                WHERE team.id=:teamId
                  AND member.student_id=:studentId
                  AND member.member_status IN ('JOINED','LOCKED')
                FOR UPDATE
                """, new MapSqlParameterSource()
                .addValue("teamId", teamId)
                .addValue("studentId", studentId));
        if (!rows.isEmpty()) {
            assertNoActiveTeamHold(rows.getFirst());
        }
    }

    public void requireNoActiveHoldForPersonalSelection(long batchId, long studentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT team.id, team.batch_id, team.team_status
                FROM selection_team team
                JOIN selection_team_member member ON member.team_id=team.id
                WHERE team.batch_id=:batchId
                  AND member.student_id=:studentId
                  AND member.member_status IN ('JOINED','LOCKED')
                  AND team.team_status IN ('FORMING','LOCKED','SELECTING')
                FOR UPDATE
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("studentId", studentId));
        for (Map<String, Object> row : rows) {
            assertNoActiveTeamHold(row);
        }
    }

    public void lockTeamForHold(long batchId, long teamId, long leaderStudentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, batch_id, team_status
                FROM selection_team
                WHERE id=:teamId
                  AND batch_id=:batchId
                  AND leader_student_id=:leaderStudentId
                FOR UPDATE
                """, new MapSqlParameterSource()
                .addValue("teamId", teamId)
                .addValue("batchId", batchId)
                .addValue("leaderStudentId", leaderStudentId));
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "TEAM_NOT_FOUND",
                    "队伍不存在或你不是队长",
                    HttpStatus.FORBIDDEN);
        }
        if (!"LOCKED".equals(String.valueOf(rows.getFirst().get("team_status")))) {
            throw new BusinessException(
                    "TEAM_STATUS_INVALID",
                    "当前队伍状态不能临时占用床位",
                    HttpStatus.CONFLICT);
        }
    }

    public void release(
            long batchId,
            long teamId,
            List<Long> bedIds,
            String token,
            long leaderStudentId) {
        List<Map<String, Object>> teams = jdbc.queryForList("""
                SELECT id FROM selection_team
                WHERE id=:teamId AND batch_id=:batchId AND leader_student_id=:leaderId
                  AND team_status IN ('LOCKED','SELECTING')
                FOR UPDATE
                """, new MapSqlParameterSource()
                .addValue("teamId", teamId)
                .addValue("batchId", batchId)
                .addValue("leaderId", leaderStudentId));
        if (teams.isEmpty()) {
            throw new BusinessException(
                    "TEAM_NOT_FOUND",
                    "队伍不存在或你不是队长",
                    HttpStatus.FORBIDDEN);
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
        eventHub.publish(
                batchId,
                roomIds.getFirst(),
                "TEAM_BEDS_RELEASED",
                Map.of("bedIds", bedIds));
    }

    private void assertNoActiveTeamHold(Map<String, Object> team) {
        String status = String.valueOf(team.get("team_status"));
        if (!HOLD_CAPABLE_STATUSES.contains(status)) {
            return;
        }
        long teamId = ((Number) team.get("id")).longValue();
        long batchId = ((Number) team.get("batch_id")).longValue();
        List<Long> scopedBedIds = jdbc.query("""
                SELECT DISTINCT bed.id
                FROM bed
                JOIN room ON room.id=bed.room_id
                JOIN dormitory_floor floor ON floor.id=room.floor_id
                WHERE EXISTS (
                    SELECT 1 FROM batch_bed_scope bed_scope
                    WHERE bed_scope.batch_id=:batchId
                      AND bed_scope.bed_id=bed.id
                )
                OR EXISTS (
                    SELECT 1 FROM batch_room_scope room_scope
                    WHERE room_scope.batch_id=:batchId
                      AND room_scope.room_id=room.id
                )
                OR EXISTS (
                    SELECT 1 FROM batch_building_scope building_scope
                    WHERE building_scope.batch_id=:batchId
                      AND building_scope.building_id=floor.building_id
                )
                """, Map.of("batchId", batchId), (rs, rowNum) -> rs.getLong(1));
        boolean active = scopedBedIds.stream()
                .anyMatch(bedId -> holdService.isHeldByTeam(batchId, bedId, teamId));
        if (active) {
            throw new BusinessException(
                    "TEAM_HOLD_ACTIVE",
                    "队伍仍有临时占用的床位，请先释放队伍临时占用的床位后再变更成员或进入个人选寝",
                    HttpStatus.CONFLICT);
        }
    }
}
