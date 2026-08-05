package com.wust.dormitory.admin;

import com.wust.dormitory.audit.AuditService;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 保留稳定的首选 Bean 名称；单人床、上床下桌和上下铺的转换逻辑已经统一收口到父类。
 */
@Service
@Primary
public class SingleBedRoomLayoutService extends RoomLayoutService {
    public static final String SINGLE_BED = "SINGLE_BED";
    public SingleBedRoomLayoutService(
            NamedParameterJdbcTemplate jdbc,
            AuditService auditService) {
        super(jdbc, auditService);
    }
}
