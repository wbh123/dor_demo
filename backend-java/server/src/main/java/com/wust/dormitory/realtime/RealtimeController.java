package com.wust.dormitory.realtime;

import com.wust.dormitory.model.api.RealtimeApi;
import com.wust.dormitory.security.SecurityUsers;
import com.wust.dormitory.student.StudentService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class RealtimeController implements RealtimeApi {
    private final RoomEventHub eventHub;
    private final StudentService studentService;

    public RealtimeController(RoomEventHub eventHub, StudentService studentService) {
        this.eventHub = eventHub;
        this.studentService = studentService;
    }

    /**
     * OpenAPI将text/event-stream表示为字符串响应；运行时使用原始ResponseEntity
     * 承载SseEmitter，路径、参数和媒体类型仍全部来自生成的RealtimeApi。
     */
    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ResponseEntity subscribeRoomEvents(Long batchId, Long roomId, String lastEventID) {
        var user = SecurityUsers.requireStudent();
        studentService.room(batchId, roomId, user);
        SseEmitter emitter = eventHub.subscribe(batchId, roomId);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
    }
}
