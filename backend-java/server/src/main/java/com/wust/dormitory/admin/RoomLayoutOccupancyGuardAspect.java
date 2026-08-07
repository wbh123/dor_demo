package com.wust.dormitory.admin;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.residency.BedOccupancyQueryService;
import com.wust.dormitory.security.CurrentUser;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Aspect
@Component
public class RoomLayoutOccupancyGuardAspect {
    private final RoomLayoutOccupancyGuardMapper layoutMapper;
    private final BedOccupancyQueryService occupancyQueryService;

    public RoomLayoutOccupancyGuardAspect(
            RoomLayoutOccupancyGuardMapper layoutMapper,
            BedOccupancyQueryService occupancyQueryService) {
        this.layoutMapper = layoutMapper;
        this.occupancyQueryService = occupancyQueryService;
    }

    @Before(
            value = "execution(* com.wust.dormitory.admin.RoomLayoutService.updateLayout(..))"
                    + " && args(roomId,command,operator)")
    public void verifyTypeChanges(
            long roomId,
            RoomLayoutService.LayoutCommand command,
            CurrentUser operator) {
        if (command == null || command.beds() == null || command.beds().isEmpty()) return;
        List<Map<String, Object>> beds = layoutMapper.findLayoutBeds(roomId);
        Map<Long, Map<String, Object>> byId = new HashMap<>();
        for (Map<String, Object> bed : beds) byId.put(number(bed.get("id")), bed);
        Map<Long, BedOccupancyQueryService.BedOccupancy> occupancy = occupancyQueryService.describeRoom(roomId);

        for (RoomLayoutService.LayoutItem item : command.beds()) {
            Map<String, Object> current = byId.get(item.bedId());
            if (current == null) continue;
            String currentType = String.valueOf(current.get("layout_unit_type"));
            if (Objects.equals(currentType, item.bedType())) continue;
            Object frameId = current.get("bed_frame_id");
            for (Map<String, Object> affected : beds) {
                boolean sameUnit = "BUNK".equals(currentType) && frameId != null
                        ? Objects.equals(frameId, affected.get("bed_frame_id"))
                        : number(affected.get("id")) == item.bedId();
                if (!sameUnit) continue;
                BedOccupancyQueryService.BedOccupancy state = occupancy.get(number(affected.get("id")));
                if (state != null && state.occupied()) {
                    throw new BusinessException(
                            "BED_TYPE_OCCUPIED",
                            state.blockingReason() == null
                                    ? "床位已有学生使用或处于业务处理中，不能修改床型"
                                    : state.blockingReason(),
                            HttpStatus.CONFLICT);
                }
            }
        }
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }
}
