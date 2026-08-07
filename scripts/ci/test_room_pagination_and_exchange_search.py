#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parents[2]
room_list = (root / 'frontend/src/views/student/RoomListView.vue').read_text(encoding='utf-8')
room_list_style = (root / 'frontend/src/room-list-card-refinement.css').read_text(encoding='utf-8')
room_change = (root / 'frontend/src/views/student/StudentRoomChangeView.vue').read_text(encoding='utf-8')
openapi = (root / 'backend-java/model/src/main/resources/roomexchange/openapi-room-exchange.yaml').read_text(encoding='utf-8')
controller = (root / 'backend-java/server/src/main/java/com/wust/dormitory/roomexchange/RoomExchangeController.java').read_text(encoding='utf-8')
candidate_controller = (root / 'backend-java/server/src/main/java/com/wust/dormitory/roomexchange/RoomExchangeCandidateController.java').read_text(encoding='utf-8')
candidate_service = (root / 'backend-java/server/src/main/java/com/wust/dormitory/roomexchange/RoomExchangeCandidateService.java').read_text(encoding='utf-8')
candidate_mapper = (root / 'backend-java/server/src/main/resources/mapper/roomexchange/RoomExchangeCandidateMapper.xml').read_text(encoding='utf-8')

compact_room_list = room_list.replace(' ', '')
compact_room_style = room_list_style.replace(' ', '')
checks = {
    'candidate rooms keep four responsive rows per page': all(token in room_list for token in (
        'ROOM_ROWS_PER_PAGE = 4', 'roomColumnCount', 'roomPageSize', 'pagedRooms', 'totalRoomPages',
    )),
    'room filters reset pagination before slicing': all(token in compact_room_list for token in (
        'watch([buildingFilter,floorFilter,roomKeyword,minimumAvailableBeds]', 'roomPage.value=1', 'sortedFilteredRooms',
    )),
    'room filters use direct building floor and capacity options plus room keyword suggestions': all(token in room_list for token in (
        'buildingFilter', 'buildingOptions', '全部楼栋', '全部楼层', 'roomKeyword',
        'roomNumberSuggestions', 'list="room-number-options"', '<datalist id="room-number-options">',
        '最低剩余床位', 'minimumAvailableBedOptions',
    )),
    'room number keyword is independent from building selection': "`${room.building_name} ${room.room_number}`" not in room_list and 'String(room.room_number' in room_list,
    'room cards remove redundant occupancy and mode explanation text': all(token not in room_list for token in (
        '在住 {{ room.activeResidentCount', "{{ isRoomMode?'选择寝室':'选择床位' }}", '个人模式显示你的匹配度；组队模式会分别计算每名已确认队友的匹配分。',
    )),
    'room cards are equal height and preference areas are height limited': all(token in compact_room_style for token in (
        '.room-list-page.room-card-grid{', '.room-list-page.room-selection-card{',
        'align-items:stretch', 'height:100%', 'max-height:120px', 'overflow-y:auto',
    )),
    'room list renders page metadata and page controls': all(token in room_list for token in (
        'pagedRooms', 'currentRoomStart', 'currentRoomEnd', 'totalRoomPages', '上一页', '下一页',
    )),
    'room change candidates search before six-row pagination': all(token in room_change for token in (
        'CHANGE_ROOM_ROWS_PER_PAGE = 6', 'candidateKeyword', 'filteredCandidates', 'pagedCandidates',
        'changeRoomPageSize', 'totalChangeRoomPages',
    )),
    'room change filters reset current page': all(token in room_change for token in (
        'watch(candidateKeyword', 'changeRoomPage.value = 1',
    )),
    'student exchange lookup requires all four exact identifiers': all(token in room_change for token in (
        'exchangeQuery.studentNumber', 'exchangeQuery.studentName', 'exchangeQuery.buildingId',
        'exchangeQuery.roomNumber', '/room-exchanges/exact-candidate', '系统不会列出或模糊搜索其他学生',
    )),
    'exchange candidates are not loaded during generic page initialization': "'/api/v1/student/room-exchanges/exact-candidate'" not in room_change.split('async function load()', 1)[1].split('async function searchExchangeCandidates', 1)[0],
    'OpenAPI candidate query requires four exact identifiers': all(token in openapi for token in (
        'summary: 按学号、姓名、楼栋和寝室号精确查询交换对象',
        'name: studentNumber', 'name: studentName', 'name: buildingId', 'name: roomNumber',
        "pattern: '^\\d{12}$'", '不提供模糊搜索或学生枚举',
    )),
    'generated controller forwards exact query to exact candidate service': all(token in controller for token in (
        'listRoomExchangeCandidates(', 'String studentNumber', 'String studentName', 'Long buildingId',
        'String roomNumber', 'candidateService.exactCandidate(',
    )) and 'service.candidates(student.studentId(), studentNumber)' not in controller,
    'compatibility exact endpoint uses the same exact service': all(token in candidate_controller for token in (
        '@GetMapping("/exact-candidate")', '@RequestParam String studentNumber', '@RequestParam String studentName',
        '@RequestParam Long buildingId', '@RequestParam String roomNumber', 'service.exactCandidate(',
    )),
    'exact candidate service and mapper prohibit fuzzy enumeration': all(token in candidate_service for token in (
        'number.matches("\\\\d{12}")', 'mapper.findExactCandidate(', 'EXCHANGE_EXACT_QUERY_REQUIRED',
    )) and all(token in candidate_mapper for token in (
        'target.student_number=#{studentNumber}', 'target.student_name=#{studentName}',
        'building.id=#{buildingId}', 'room.room_number=#{roomNumber}', 'LIMIT 1',
    )) and ' LIKE ' not in candidate_mapper,
}

failed = [label for label, ok in checks.items() if not ok]
if failed:
    for label in failed:
        print(f'FAIL: {label}')
    raise SystemExit(1)
print('Room pagination, filter and exact exchange-search contract passed.')
