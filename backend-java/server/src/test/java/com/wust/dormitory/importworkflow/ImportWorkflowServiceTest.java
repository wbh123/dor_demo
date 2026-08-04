package com.wust.dormitory.importworkflow;

import com.wust.dormitory.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportWorkflowServiceTest {
    private InMemoryImportTaskRepository repository;
    private ImportMutationService mutationService;
    private ImportWorkflowService service;
    private CurrentUser operator;

    @BeforeEach
    void setUp() {
        repository = new InMemoryImportTaskRepository();
        mutationService = mock(ImportMutationService.class);
        service = new ImportWorkflowService(repository, mutationService);
        operator = new CurrentUser(7L, null, "admin", "管理员", "ADMIN");
    }

    @Test
    void previewRejectsDuplicateStudentsBeforeCommit() {
        String csv = "学号,姓名,性别,专业编码,国家/地区代码,学生类别,培养层次,年级,手机号码（含国家码）\n"
                + "202600000001,张三,男,SE,CN,国内生,硕士生,2026,+8613800000000\n"
                + "202600000001,张三,男,SE,CN,国内生,硕士生,2026,+8613800000000\n";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "students.csv",
                "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> result = service.preview(file, "STUDENT", "duplicate-student-file");

        assertThat(result)
                .containsEntry("status", "PREVIEWED")
                .containsEntry("totalRows", 2)
                .containsEntry("validRows", 1)
                .containsEntry("invalidRows", 1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errors = (List<Map<String, Object>>) result.get("fieldErrors");
        assertThat(errors).anySatisfy(error -> {
            assertThat(error.get("field")).isEqualTo("duplicate");
            assertThat(String.valueOf(error.get("message"))).contains("第2行");
        });
        verify(mutationService, times(2)).validateRow(eq("STUDENT"), any());
    }

    @Test
    void commitAndRollbackInvokeRealMutationBoundaryAndPersistState() {
        Map<String, String> row = Map.of("学号", "202600000001");
        ImportTaskRecord task = new ImportTaskRecord(
                "task-1",
                "STUDENT",
                "students.csv",
                "digest",
                "key",
                "PREVIEWED",
                List.of(row),
                List.of(),
                List.of(),
                Instant.parse("2026-08-04T00:00:00Z"),
                null,
                null);
        repository.save(task);
        ImportJournalEntry journal = new ImportJournalEntry(
                "STUDENT_CREATE",
                101L,
                Map.of(),
                Map.of("studentId", 101L),
                Map.of());
        when(mutationService.applyTask("STUDENT", List.of(row), operator))
                .thenReturn(List.of(journal));

        Map<String, Object> committed = service.commitTask("task-1", operator);
        Map<String, Object> rolledBack = service.rollbackTask("task-1", operator);

        assertThat(committed)
                .containsEntry("status", "COMMITTED")
                .containsEntry("mutationCount", 1);
        assertThat(rolledBack)
                .containsEntry("status", "ROLLED_BACK")
                .containsEntry("mutationCount", 1);
        verify(mutationService).applyTask("STUDENT", List.of(row), operator);
        verify(mutationService).rollbackJournal(List.of(journal), operator);
        assertThat(repository.findById("task-1")).get()
                .extracting(ImportTaskRecord::status)
                .isEqualTo("ROLLED_BACK");
    }
}
