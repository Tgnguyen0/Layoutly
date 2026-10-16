package com.tgnguyen.layoutlybe.service.ai;

import com.tgnguyen.layoutlybe.model.ai.AiAgentEvent;
import com.tgnguyen.layoutlybe.model.ai.AiChatRequest;
import com.tgnguyen.layoutlybe.model.ai.AiChatResponse;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AiAgentJobService {
    private final AiAgentLoopService agent;
    private final ConcurrentHashMap<String, Job> jobs = new ConcurrentHashMap<>();
    private final AtomicReference<Job> active = new AtomicReference<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> { Thread thread = new Thread(r, "layoutly-local-ai"); thread.setDaemon(true); return thread; });

    private static final class Job {
        final String id = UUID.randomUUID().toString();
        final String snapshotId;
        final Instant createdAt = Instant.now();
        final List<AiAgentEvent> events = new CopyOnWriteArrayList<>();
        volatile String state = "QUEUED";
        volatile AiChatResponse result;
        volatile String error = "";
        volatile Future<?> future;
        volatile boolean cancelled;
        Job(String snapshotId) { this.snapshotId = snapshotId; }
        Map<String, Object> view() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("jobId", id); value.put("snapshotId", snapshotId); value.put("state", state);
            value.put("createdAt", createdAt.toString()); value.put("events", List.copyOf(events));
            value.put("result", result); value.put("error", error); return value;
        }
    }

    public AiAgentJobService(AiAgentLoopService agent) { this.agent = agent; }

    public Map<String, Object> start(String snapshotId, AiChatRequest request) {
        // Fail closed before creating a job or touching a workspace when cloud is requested.
        agent.getProviderRegistry().getProvider(request.provider());
        Job job = new Job(snapshotId);
        if (!active.compareAndSet(null, job)) throw new IllegalStateException("Local AI is busy. Cancel or wait for the current job.");
        if (jobs.size() >= 64) jobs.entrySet().removeIf(entry -> !List.of("RUNNING", "QUEUED", "CANCELLING").contains(entry.getValue().state));
        jobs.put(job.id, job);
        job.future = worker.submit(() -> {
            job.state = "RUNNING";
            try {
                if (job.cancelled) throw new CancellationException("Job cancelled before inference.");
                AiChatResponse result = agent.processUserPrompt(snapshotId, request, null, event -> { if (job.events.size() < 80) job.events.add(event); });
                if (job.cancelled) throw new CancellationException("Job cancelled.");
                job.result = result; job.state = "COMPLETED";
            } catch (CancellationException e) { job.state = "CANCELLED"; job.error = e.getMessage(); }
            catch (Exception e) { job.state = job.cancelled ? "CANCELLED" : "FAILED"; job.error = Objects.toString(e.getMessage(), e.getClass().getSimpleName()); }
            finally { active.compareAndSet(job, null); }
        });
        return job.view();
    }

    private Job require(String snapshotId, String id) {
        Job job = jobs.get(id);
        if (job == null || !job.snapshotId.equals(snapshotId)) throw new IllegalArgumentException("Unknown AI job for this snapshot.");
        return job;
    }

    public Map<String, Object> get(String snapshotId, String id) { return require(snapshotId, id).view(); }
    public Map<String, Object> cancel(String snapshotId, String id) {
        Job job = require(snapshotId, id);
        if (!List.of("QUEUED", "RUNNING", "CANCELLING").contains(job.state)) return job.view();
        job.cancelled = true; job.state = "CANCELLING";
        Future<?> task = job.future;
        if (task != null) task.cancel(true);
        // Running tasks publish CANCELLED only after cleanup. A queued cancelled
        // Future never enters its worker, so release that slot explicitly.
        if (job.events.isEmpty()) { job.state = "CANCELLED"; active.compareAndSet(job, null); }
        return job.view();
    }

    @PreDestroy public void close() { worker.shutdownNow(); }
}
