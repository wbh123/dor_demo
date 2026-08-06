package com.wust.dormitory.export;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExportTaskMapperContractTest {
    @Test
    void queuedTaskLookupUsesTypedProjectionInsteadOfMapResult() throws Exception {
        Method method = ExportTaskMapper.class.getMethod("findNextQueued");

        assertEquals(
                "com.wust.dormitory.export.ExportTaskQueueRow",
                method.getGenericReturnType().getTypeName());
    }
}
