package com.wust.dormitory.importworkflow;

import com.wust.dormitory.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class ImportWorkflowMissingIdentityTest {
    @Test
    void missingStudentNumbersAreValidationErrorsButNotDuplicateRows() {
        ImportMutationService mutationService = mock(ImportMutationService.class);
        doThrow(new BusinessException("STUDENT_NUMBER_INVALID", "学号必须为12位数字"))
                .when(mutationService)
                .validateRow(eq("STUDENT"), any());
        ImportWorkflowService service = new ImportWorkflowService(
                new InMemoryImportTaskRepository(),
                mutationService);
        String csv = "学号,姓名,性别,专业编码,国家/地区代码,学生类别,培养层次,年级,手机号码（含国家码）\n"
                + ",张三,男,SE,CN,国内生,硕士生,2026,+8613800000000\n"
                + ",李四,女,SE,CN,国内生,硕士生,2026,+8613900000000\n";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "students.csv",
                "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> result = service.preview(file, "STUDENT", "missing-student-numbers");

        assertThat(result)
                .containsEntry("totalRows", 2)
                .containsEntry("validRows", 0)
                .containsEntry("invalidRows", 2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errors = (List<Map<String, Object>>) result.get("fieldErrors");
        assertThat(errors).hasSize(2);
        assertThat(errors).noneSatisfy(error ->
                assertThat(error.get("field")).isEqualTo("duplicate"));
    }
}
