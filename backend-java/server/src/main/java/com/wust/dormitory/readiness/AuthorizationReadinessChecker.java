package com.wust.dormitory.readiness;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AuthorizationReadinessChecker implements ReadinessChecker {
    @Override
    public String category() {
        return "AUTHORIZATION";
    }

    @Override
    public boolean critical() {
        return true;
    }

    @Override
    public List<ReadinessCheckResult> check(ReadinessContext context) {
        return List.of(ReadinessCheckResult.info(
                "AUTHORIZATION_PRIVATE_PROJECTION", category(), "权限与授权准备",
                "公开演示仓库不保存学校订阅与岗位授权投影；私有部署体检会读取现有授权服务完成该项检查。",
                context.checkedAt()));
    }
}
