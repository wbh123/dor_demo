package com.wust.dormitory.allocation;

import com.wust.dormitory.common.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OptimizedAllocationRunQueryService {
    private final OptimizedAllocationMapper mapper;

    public OptimizedAllocationRunQueryService(OptimizedAllocationMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Object> runView(long runId) {
        Map<String, Object> run = mapper.findRunView(runId);
        if (run == null) throw notFound();
        Map<String, Object> result = new LinkedHashMap<>(run);
        result.put("candidates", candidates(runId));
        return result;
    }

    public List<OptimizedAllocationRunService.Candidate> candidates(long runId) {
        return mapper.findCandidates(runId).stream().map(this::candidate).toList();
    }

    public String exportCsv(long runId) {
        Map<String, Object> run = runView(runId);
        StringBuilder csv = new StringBuilder("runId,batchId,studentId,bedId,roomId,teamId,score\n");
        for (OptimizedAllocationRunService.Candidate candidate : candidates(runId)) {
            csv.append(runId).append(',').append(run.get("batch_id")).append(',')
                    .append(candidate.studentId()).append(',').append(candidate.bedId()).append(',')
                    .append(candidate.roomId()).append(',')
                    .append(candidate.teamId() == null ? "" : candidate.teamId()).append(',')
                    .append(candidate.score()).append('\n');
        }
        return csv.toString();
    }

    public OptimizedAllocationRunService.Candidate candidate(Map<String, Object> row) {
        return new OptimizedAllocationRunService.Candidate(
                number(row.get("student_id")),
                number(row.get("bed_id")),
                number(row.get("room_id")),
                row.get("team_id") == null ? null : number(row.get("team_id")),
                ((Number) row.getOrDefault("score", 0.0d)).doubleValue());
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private BusinessException notFound() {
        return new BusinessException("ALLOCATION_RUN_NOT_FOUND", "优化分配运行不存在", HttpStatus.NOT_FOUND);
    }
}
