package com.wust.dormitory.importworkflow;

import java.util.List;
import java.util.Optional;

public interface ImportTaskRepository {
    ImportTaskRecord save(ImportTaskRecord task);

    Optional<ImportTaskRecord> findById(String taskId);

    Optional<ImportTaskRecord> findByIdempotencyKey(String idempotencyKey);

    List<ImportTaskRecord> list();
}
