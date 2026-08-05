package com.wust.dormitory.selection;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class SelectionAccessLeaseGuard {
    private static final Duration CONFIRMATION_RENEWAL = Duration.ofSeconds(75);

    private final FeatureAccessService featureAccessService;
    private final ConcurrentSelectionLeaseService leaseService;

    public SelectionAccessLeaseGuard(
            FeatureAccessService featureAccessService,
            ConcurrentSelectionLeaseService leaseService) {
        this.featureAccessService = featureAccessService;
        this.leaseService = leaseService;
    }

    public void requireActive(CurrentUser user, String token) {
        if (!featureAccessService.has(FeatureCodes.P2_CONCURRENT_SELECTION_LIMIT)) {
            return;
        }
        if (token == null || token.isBlank()) {
            throw new BusinessException(
                    "CONCURRENT_SELECTION_LEASE_REQUIRED",
                    "当前选寝访问凭证缺失，请重新进入选寝页面",
                    HttpStatus.CONFLICT);
        }
        leaseService.renew(user.studentId(), token, CONFIRMATION_RENEWAL);
    }
}
