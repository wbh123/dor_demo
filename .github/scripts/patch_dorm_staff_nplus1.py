from pathlib import Path

path = Path('private-repo/backend-java/server/src/main/java/com/wust/dormitory/admin/DormStaffWebController.java')
text = path.read_text(encoding='utf-8')


def replace_once(old: str, new: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'expected exactly one match, got {count}: {old[:100]!r}')
    text = text.replace(old, new, 1)


replace_once(
    'import com.wust.dormitory.security.AdminAuthorizationService;\nimport com.wust.dormitory.security.CurrentUser;',
    'import com.wust.dormitory.security.AdminAuthorizationService;\nimport com.wust.dormitory.security.AdminScopeAccess;\nimport com.wust.dormitory.security.CurrentUser;',
)

replace_once(
    '''        List<Map<String, Object>> rooms = context.hasPermission("dormitory.read")
                ? workspaceFacade.roomOptions().stream()
                    .filter(room -> authorizationService.canAccessRoom(operator, number(room.get("roomId"))))
                    .toList()
                : List.of();''',
    '''        List<Map<String, Object>> rooms = context.hasPermission("dormitory.read")
                ? scopeRooms(context, workspaceFacade.roomOptions())
                : List.of();''',
)

replace_once(
    '''        authorizationService.requireBusinessPermission(operator, "dormitory.read");
        List<Map<String, Object>> rooms = workspaceFacade.roomOptions().stream()
                .filter(room -> authorizationService.canAccessRoom(operator, number(room.get("roomId"))))
                .toList();
        return ResponseEntity.ok(ResponseFactory.list(rooms));''',
    '''        AdminAuthorizationContext context = authorizationService.requireBusinessPermission(
                operator, "dormitory.read");
        return ResponseEntity.ok(ResponseFactory.list(
                scopeRooms(context, workspaceFacade.roomOptions())));''',
)

replace_once(
    '''        authorizationService.requireBusinessPermission(operator, "dormitory.read");
        List<Map<String, Object>> rooms = workspaceFacade.roomStatuses(buildingId, floorNumber).stream()
                .filter(room -> authorizationService.canAccessRoom(operator, number(room.get("roomId"))))
                .toList();
        return ResponseEntity.ok(ResponseFactory.list(rooms));''',
    '''        AdminAuthorizationContext context = authorizationService.requireBusinessPermission(
                operator, "dormitory.read");
        return ResponseEntity.ok(ResponseFactory.list(
                scopeRooms(context, workspaceFacade.roomStatuses(buildingId, floorNumber))));''',
)

marker = '    private Map<String, Object> capabilities(AdminAuthorizationContext context) {'
helper = '''    private List<Map<String, Object>> scopeRooms(
            AdminAuthorizationContext context,
            List<Map<String, Object>> rooms) {
        Set<Long> accessibleRoomIds = authorizationService.filterAccessibleRoomIds(
                context,
                rooms.stream().map(room -> number(room.get("roomId"))).toList(),
                AdminScopeAccess.READ);
        return rooms.stream()
                .filter(room -> accessibleRoomIds.contains(number(room.get("roomId"))))
                .toList();
    }

'''
if marker not in text:
    raise SystemExit('capabilities marker not found')
if 'private List<Map<String, Object>> scopeRooms(' in text:
    raise SystemExit('scopeRooms already exists unexpectedly')
text = text.replace(marker, helper + marker, 1)
path.write_text(text, encoding='utf-8')
