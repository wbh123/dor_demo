#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parents[2]
room_list = (root / 'frontend/src/views/student/RoomListView.vue').read_text(encoding='utf-8')
room_change = (root / 'frontend/src/views/student/StudentRoomChangeView.vue').read_text(encoding='utf-8')
openapi = (root / 'backend-java/model/src/main/resources/roomexchange/openapi-room-exchange.yaml').read_text(encoding='utf-8')
controller = (root / 'backend-java/server/src/main/java/com/wust/dormitory/roomexchange/RoomExchangeController.java').read_text(encoding='utf-8')
service = (root / 'backend-java/server/src/main/java/com/wust/dormitory/roomexchange/RoomExchangeService.java').read_text(encoding='utf-8')

checks = {
    'candidate rooms keep four responsive rows per page': all(token in room_list for token in (
        'ROOM_ROWS_PER_PAGE = 4', 'roomColumnCount', 'roomPageSize', 'pagedRooms', 'totalRoomPages',
    )),
    'room filters reset pagination before slicing': all(token in room_list for token in (
        'watch([keyword, floorFilter, minimumAvailableBeds]', 'roomPage.value = 1', 'sortedFilteredRooms',
    )),
    'room cards are equal height and tags are height limited': all(token in room_list for token in (
        'room-card-equal', 'room-tag-viewport', 'overflow-y: auto',
    )),
    'room list renders page metadata and page controls': all(token in room_list for token in (
        'pagedRooms', 'currentRoomStart', 'currentRoomEnd', '第{{ roomPage }} / {{ totalRoomPages }}页',
    )),
    'room change candidates search before six-row pagination': all(token in room_change for token in (
        'CHANGE_ROOM_ROWS_PER_PAGE = 6', 'candidateKeyword', 'filteredCandidates', 'pagedCandidates',
        'changeRoomPageSize', 'totalChangeRoomPages',
    )),
    'room change filters reset current page': all(token in room_change for token in (
        'watch(candidateKeyword', 'changeRoomPage.value = 1',
    )),
    'student exchange lookup starts empty and only searches student numbers': all(token in room_change for token in (
        'exchangeStudentNumber', 'searchExchangeCandidates', "params: { studentNumber }", '请输入完整或部分学号',
    )),
    'exchange candidates are not loaded during generic page initialization': "'/api/v1/student/room-exchanges/candidates'" not in room_change.split('async function load()', 1)[1].split('async function searchExchangeCandidates', 1)[0],
    'OpenAPI requires a student-number query': all(token in openapi for token in (
        'name: studentNumber', 'required: true', 'minLength: 1', 'maxLength: 32',
    )),
    'controller forwards the exact query to service': 'listRoomExchangeCandidates(String studentNumber)' in controller and 'service.candidates(student.studentId(), studentNumber)' in controller,
    'service limits and scopes student-number lookup': all(token in service for token in (
        'candidates(long studentId, String studentNumber)', 'target.student_number LIKE :studentNumber', 'LIMIT 20',
        'participant_lock.student_id IS NULL', "assignment.assignment_status='ACTIVE'",
    )),
}

failed = [label for label, ok in checks.items() if not ok]
if failed:
    for label in failed:
        print(f'FAIL: {label}')
    raise SystemExit(1)
print('Room pagination and exchange-search contract passed.')
