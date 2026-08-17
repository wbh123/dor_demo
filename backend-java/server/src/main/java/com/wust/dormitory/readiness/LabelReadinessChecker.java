package com.wust.dormitory.readiness;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LabelReadinessChecker implements ReadinessChecker {
    @Override
    public String category() {
        return "LABEL";
    }

    @Override
    public List<ReadinessCheckResult> check(ReadinessContext context) {
        return List.of(ReadinessCheckResult.info(
                "LABEL_PRIVATE_CONFIGURATION", category(), "标签与现场管理",
                "公开演示仓库不保存学校现场标签配置；私有部署体检会读取既有模板、标签和查寝配置。",
                context.checkedAt()));
    }
}
