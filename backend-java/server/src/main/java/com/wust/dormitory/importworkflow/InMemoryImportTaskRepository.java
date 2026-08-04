package com.wust.dormitory.importworkflow;

import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
public class InMemoryImportTaskRepository implements ImportTaskRepository {
    private final ConcurrentMap<String, ImportTaskRecord> tasks = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> idempotencyIndex = new ConcurrentHashMap<>();

    @Override
    public ImportTaskRecord save(ImportTaskRecord task) {
        tasks.put(task.taskId(), task);
        idempotencyIndex.put(task.idempotencyKey(), task.taskId());
        return task;
    }

    @Override
    public Optional<ImportTaskRecord> findById(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    @Override
    public Optional<ImportTaskRecord> findByIdempotencyKey(String idempotencyKey) {
        String taskId = idempotencyIndex.get(idempotencyKey);
        return taskId == null ? Optional.empty() : findById(taskId);
    }

    @Override
    public List<ImportTaskRecord> list() {
        return tasks.values().stream()
                .sorted(Comparator.comparing(ImportTaskRecord::createdAt).reversed())
                .toList();
    }
}
