package com.wust.dormitory.residency;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.dto.ListSuccessResponse;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/rooms/{roomId}/bed-occupancy")
public class BedOccupancyController {
    private final BedOccupancyQueryService service;

    public BedOccupancyController(BedOccupancyQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ListSuccessResponse> list(@PathVariable long roomId) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.list(service.describeRoomAsList(roomId)));
    }
}
