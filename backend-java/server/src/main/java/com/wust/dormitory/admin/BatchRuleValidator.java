package com.wust.dormitory.admin;

import com.wust.dormitory.common.error.BusinessException;
import org.springframework.stereotype.Service;

@Service
public class BatchRuleValidator {
    public void validate(AdminService.BatchCommand command) {
        if (command.startAt() == null || command.endAt() == null) {
            throw new BusinessException("BATCH_TIME_REQUIRED", "选寝开始时间和结束时间不能为空");
        }
        if (!command.startAt().isBefore(command.endAt())) {
            throw new BusinessException("BATCH_TIME_INVALID", "选寝开始时间必须早于结束时间");
        }
        if (command.holdDurationSeconds() < 30 || command.holdDurationSeconds() > 3600) {
            throw new BusinessException("HOLD_DURATION_INVALID", "临时占用时间必须在30秒至3600秒之间");
        }
        if (command.allowTeam() && command.teamMaxSize() < 2) {
            throw new BusinessException("TEAM_SIZE_INVALID", "允许组队时队伍最大人数不能小于2人");
        }
    }
}
