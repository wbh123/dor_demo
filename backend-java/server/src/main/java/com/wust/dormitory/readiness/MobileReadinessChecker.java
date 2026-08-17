package com.wust.dormitory.readiness;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MobileReadinessChecker implements ReadinessChecker {
    @Override
    public String category() {
        return "MOBILE";
    }

    @Override
    public List<ReadinessCheckResult> check(ReadinessContext context) {
        return List.of(ReadinessCheckResult.info(
                "MOBILE_PRIVATE_RELEASE", category(), "移动管理端发布",
                "公开演示仓库未保留正式 APK 发布记录；私有部署体检会复用移动版本发布服务检查版本、最低支持版本、SHA-256 与强制更新配置。",
                context.checkedAt()));
    }
}
