from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
openapi = (ROOT / 'backend-java/model/src/main/resources/student/openapi-student.yaml').read_text(encoding='utf-8')
controller = (ROOT / 'backend-java/server/src/main/java/com/wust/dormitory/student/StudentController.java').read_text(encoding='utf-8')
view = (ROOT / 'frontend/src/views/student/TeamView.vue').read_text(encoding='utf-8')

assert '/api/v1/student/team-invitations:' in openapi
assert 'operationId: inviteTeammate' in openapi
assert '/api/v1/student/teams/{teamId}/members/{studentId}:' in openapi
assert 'operationId: removeTeamMember' in openapi
assert 'implements StudentApi' in controller
assert 'inviteTeammate(InviteRequest request)' in controller
assert 'removeTeamMember(' in controller

assert "api.post<ObjectSuccessResponse>('/api/v1/student/team-invitations'," in view, 'invite UI must call generated StudentApi route'
assert '/api/v1/student/team-invitations/verified' not in view, 'legacy verified invite route must not remain in UI'
assert '/invitations/${Number(member.student_id)}' not in view, 'legacy invitation cancel route must not remain in UI'
assert 'api.delete(`/api/v1/student/teams/${Number(team.id)}/members/${Number(member.student_id)}`)' in view, 'pending-invite cancellation and joined-member removal must use generated removeTeamMember route'

print('Team generated OpenAPI/frontend contract passed')
