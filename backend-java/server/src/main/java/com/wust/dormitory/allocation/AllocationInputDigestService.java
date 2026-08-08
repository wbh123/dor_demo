package com.wust.dormitory.allocation;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;

@Service
public class AllocationInputDigestService {
    public String digest(AllocationModels.InputSnapshot snapshot, Map<String, Object> batchMetadata) {
        StringBuilder canonical = new StringBuilder(4096);
        canonical.append("batch=").append(snapshot.batchId())
                .append("|status=").append(value(batchMetadata, "batch_status"))
                .append("|scheme=").append(value(batchMetadata, "matching_weight_scheme_id"))
                .append("|ruleTemplate=").append(value(batchMetadata, "rule_template_id"))
                .append("|ruleVersion=").append(value(batchMetadata, "rule_version"));
        snapshot.students().stream()
                .sorted(Comparator.comparingLong(AllocationModels.StudentCandidate::studentId))
                .forEach(student -> canonical.append("|s:")
                        .append(student.studentId()).append(':')
                        .append(student.studentNumber()).append(':')
                        .append(student.gender()).append(':')
                        .append(student.majorId()).append(':')
                        .append(student.accountStatus()));
        snapshot.beds().stream()
                .sorted(Comparator.comparingLong(AllocationModels.BedCandidate::bedId))
                .forEach(bed -> canonical.append("|b:")
                        .append(bed.bedId()).append(':')
                        .append(bed.roomId()).append(':')
                        .append(bed.gender()).append(':')
                        .append(bed.positionIndex()));
        snapshot.lockedTeams().stream()
                .sorted(Comparator.comparingLong(AllocationModels.TeamCandidate::teamId))
                .forEach(team -> {
                    canonical.append("|t:").append(team.teamId()).append(':').append(team.gender());
                    team.members().stream()
                            .sorted(Comparator.comparingLong(AllocationModels.StudentCandidate::studentId))
                            .forEach(member -> canonical.append(':').append(member.studentId()));
                });
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private Object value(Map<String, Object> metadata, String key) {
        return metadata.get(key) == null ? "" : metadata.get(key);
    }
}
