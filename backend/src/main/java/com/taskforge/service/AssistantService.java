package com.taskforge.service;

import com.taskforge.config.EngineProperties;
import com.taskforge.domain.Job;
import com.taskforge.domain.JobStatus;
import com.taskforge.domain.JobType;
import com.taskforge.dto.request.BatchJobRequest;
import com.taskforge.dto.request.ChatRequest;
import com.taskforge.dto.response.BatchJobResponse;
import com.taskforge.dto.response.ChatAction;
import com.taskforge.dto.response.ChatResponse;
import com.taskforge.engine.ExecutionEngine;
import com.taskforge.metrics.MetricsService;
import com.taskforge.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Intelligent domain assistant for TaskForge.
 *
 * Provides tailored responses, real-time diagnostic introspection, and one-click
 * cluster actions for four distinct user personas:
 * 1. STUDENT (B.Tech Capstone Viva Defense & CS Theory)
 * 2. OPERATOR (DevOps, Cluster Health, Metrics, and Maintenance Actions)
 * 3. DEVELOPER (REST API specs, JSON payloads, Concurrency Internals)
 * 4. NOVICE (Plain English explanations and analogies)
 */
@Service
public class AssistantService {
    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private final JobRepository jobRepository;
    private final JobService jobService;
    private final EngineService engineService;
    private final ExecutionEngine executionEngine;
    private final MetricsService metricsService;
    private final EngineProperties properties;

    public AssistantService(
            JobRepository jobRepository,
            JobService jobService,
            EngineService engineService,
            ExecutionEngine executionEngine,
            MetricsService metricsService,
            EngineProperties properties
    ) {
        this.jobRepository = jobRepository;
        this.jobService = jobService;
        this.engineService = engineService;
        this.executionEngine = executionEngine;
        this.metricsService = metricsService;
        this.properties = properties;
    }

    public ChatResponse processQuery(ChatRequest request) {
        String persona = request.resolvedPersona();
        String message = request.message().trim();
        String actionId = request.actionId();

        // 1. Gather live system telemetry
        Map<String, Object> snapshot = captureSnapshot();

        // 2. Handle direct Action Execution if requested
        if (actionId != null && !actionId.isBlank()) {
            return executeAction(actionId, persona, snapshot);
        }

        // 3. Process natural language query
        String lowerMsg = message.toLowerCase(Locale.ROOT);

        // Check if query is an explicit operational request
        if (isActionIntent(lowerMsg)) {
            return handleOperationalIntent(lowerMsg, persona, snapshot);
        }

        // Persona-specific routing
        return switch (persona) {
            case "SRE", "STUDENT" -> handleSreQuery(lowerMsg, snapshot, persona);
            case "OPERATOR" -> handleOperatorQuery(lowerMsg, snapshot);
            case "AUDITOR", "NOVICE" -> handleAuditorQuery(lowerMsg, snapshot, persona);
            default -> handleDeveloperQuery(lowerMsg, snapshot);
        };
    }

    private Map<String, Object> captureSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        int activeWorkers = executionEngine.getActiveWorkerCount();
        int totalWorkers = properties.getWorkerCount();
        int queueSize = executionEngine.getQueue().size();
        int queueCapacity = properties.getQueueCapacity();
        long queuedJobs = jobRepository.countByStatus(JobStatus.QUEUED);
        long runningJobs = jobRepository.countByStatus(JobStatus.RUNNING);
        long succeededJobs = jobRepository.countByStatus(JobStatus.SUCCEEDED);
        long failedJobs = jobRepository.countByStatus(JobStatus.FAILED);
        long cancelledJobs = jobRepository.countByStatus(JobStatus.CANCELLED);
        boolean isPaused = executionEngine.isPaused();

        snapshot.put("activeWorkers", activeWorkers);
        snapshot.put("totalWorkers", totalWorkers);
        snapshot.put("queueSize", queueSize);
        snapshot.put("queueCapacity", queueCapacity);
        snapshot.put("queuedJobs", queuedJobs);
        snapshot.put("runningJobs", runningJobs);
        snapshot.put("succeededJobs", succeededJobs);
        snapshot.put("failedJobs", failedJobs);
        snapshot.put("cancelledJobs", cancelledJobs);
        snapshot.put("isPaused", isPaused);
        return snapshot;
    }

    private boolean isActionIntent(String msg) {
        return msg.contains("submit") || msg.contains("launch") || msg.contains("start batch") ||
                msg.contains("replay dlq") || msg.contains("retry failed") ||
                msg.contains("cancel all") || msg.contains("cancel pending") ||
                msg.contains("purge") || msg.contains("pause engine") || msg.contains("resume engine");
    }

    private ChatResponse executeAction(String actionId, String persona, Map<String, Object> snapshot) {
        List<ChatAction> actions = new ArrayList<>();
        StringBuilder reply = new StringBuilder();

        switch (actionId.toUpperCase()) {
            case "SUBMIT_SAMPLE" -> {
                BatchJobRequest batch = new BatchJobRequest(5, "assistant-demo", JobType.COMPUTE, 1, 0.0, 150, 3, null, false);
                BatchJobResponse res = jobService.createBatch(batch);
                reply.append("### Action Executed: 5 Sample Compute Jobs Dispatched\n\n")
                        .append("Successfully enqueued **5 compute tasks** (`assistant-demo-001..005`) into the bounded execution buffer.\n")
                        .append("- **Initial Priority:** 1\n")
                        .append("- **Max Attempts:** 3\n")
                        .append("- **Enqueued Count:** ").append(res.submittedCount()).append("\n\n")
                        .append("Watch the worker threads pick them up live in the **Live Workers** tab.");
            }
            case "REPLAY_DLQ" -> {
                int count = jobService.replayDlq();
                reply.append("### Action Executed: Dead Letter Queue (DLQ) Replay\n\n")
                        .append("Replayed ").append(count).append(" failed jobs back into the queue with reset attempt counters.");
            }
            case "CANCEL_PENDING" -> {
                int count = jobService.cancelAllPending();
                reply.append("### Action Executed: Bulk Cancellation\n\n")
                        .append("Cancelled ").append(count).append(" pending queued/scheduled tasks.");
            }
            case "PURGE_TERMINAL" -> {
                int count = jobService.purgeTerminalJobs();
                reply.append("### Action Executed: Database Purge\n\n")
                        .append("Permanently purged ").append(count).append(" terminal jobs and cascaded history logs.");
            }
            case "PAUSE_ENGINE" -> {
                engineService.pause();
                reply.append("### Action Executed: Engine Paused\n\n")
                        .append("Worker threads will complete their current active jobs and pause polling the queue until resumed.");
                actions.add(new ChatAction("RESUME_ENGINE", "Resume Engine", "Resume queue polling", "/api/v1/engine/resume", "POST"));
            }
            case "RESUME_ENGINE" -> {
                engineService.resume();
                reply.append("### Action Executed: Engine Resumed\n\n")
                        .append("Worker threads have resumed active polling from the job queue.");
            }
            default -> {
                reply.append("Unknown action identifier: `").append(actionId).append("`.");
            }
        }

        // Re-capture snapshot after execution
        return new ChatResponse(reply.toString(), persona, actions, captureSnapshot(), Instant.now());
    }

    private ChatResponse handleOperationalIntent(String msg, String persona, Map<String, Object> snapshot) {
        if (msg.contains("replay") || msg.contains("retry failed")) {
            return executeAction("REPLAY_DLQ", persona, snapshot);
        }
        if (msg.contains("cancel all") || msg.contains("cancel pending")) {
            return executeAction("CANCEL_PENDING", persona, snapshot);
        }
        if (msg.contains("purge") || msg.contains("clean")) {
            return executeAction("PURGE_TERMINAL", persona, snapshot);
        }
        if (msg.contains("pause")) {
            return executeAction("PAUSE_ENGINE", persona, snapshot);
        }
        if (msg.contains("resume")) {
            return executeAction("RESUME_ENGINE", persona, snapshot);
        }
        if (msg.contains("submit") || msg.contains("launch") || msg.contains("demo")) {
            return executeAction("SUBMIT_SAMPLE", persona, snapshot);
        }

        return executeAction("SUBMIT_SAMPLE", persona, snapshot);
    }

    // -------------------------------------------------------------
    // PERSONA 1: SRE (Site Reliability Engineering, Incident Triage & Architecture)
    // -------------------------------------------------------------
    private ChatResponse handleSreQuery(String msg, Map<String, Object> snapshot, String requestedPersona) {
        StringBuilder reply = new StringBuilder();
        List<ChatAction> actions = new ArrayList<>();

        if (msg.contains("producer") || msg.contains("consumer") || msg.contains("queue") || msg.contains("buffer")) {
            reply.append("### Architecture Diagnostic: Producer-Consumer Architecture\n\n")
                    .append("**System Design Specification:**\n\n")
                    .append("- **Producers:** Ingesting services submitting via REST API (`POST /api/v1/jobs`). They persist task records and offer IDs to the execution buffer.\n")
                    .append("- **Bounded Buffer (`JobQueue.java`):** Wraps Java's `PriorityBlockingQueue` with a lock-free CAS `AtomicInteger` to enforce a strict upper bound capacity. This provides **backpressure** (HTTP 429) to prevent Out-Of-Memory crashes during ingress traffic spikes.\n")
                    .append("- **Consumers:** A pool of dedicated OS worker threads (`taskforge-worker-0..N`) execute an infinite loop in `WorkerThread.run()`, pulling tasks and dispatching them to workload handlers.\n\n")
                    .append("**Engineering Invariant:** Priority ordering is O(log N) heap insertion, but deduplication is O(1) via a `ConcurrentHashMap`-backed membership set.");
        } else if (msg.contains("two phase") || msg.contains("transaction") || msg.contains("starvation") || msg.contains("hikaricp")) {
            reply.append("### Reliability Architecture: Two-Phase Transaction Decoupling\n\n")
                    .append("**Incident Prevention (HikariCP Connection Pool Starvation Guard):**\n\n")
                    .append("- **Failure Mode:** If a compute task takes 5 seconds, wrapping the execution handler with `@Transactional` holds a JDBC database connection from HikariCP for the entire duration. With 10 workers, all 10 connections are starved immediately, causing API connection timeouts (HTTP 500).\n")
                    .append("- **The TaskForge Architecture:**\n")
                    .append("  1. **Phase 1 Short Tx (~2ms):** `claimJob()` transitions state to `RUNNING`, records start timestamp, and **commits immediately**, releasing the DB connection back to the HikariCP pool.\n")
                    .append("  2. **Out-of-Tx Execution:** Heavy CPU or I/O workloads execute **completely outside** any database transaction.\n")
                    .append("  3. **Phase 2 Short Tx (~2ms):** `recordSuccess()` or `recordFailure()` opens a second transaction to persist terminal state.\n\n")
                    .append("**Source Reference:** `ExecutionEngine.java` lines 268-340.");
        } else if (msg.contains("backoff") || msg.contains("jitter") || msg.contains("thundering herd") || msg.contains("retry")) {
            reply.append("### Reliability Engineering: Exponential Backoff & Jitter\n\n")
                    .append("**Thundering Herd Mitigation:**\n\n")
                    .append("- If 100 jobs fail simultaneously because a downstream dependency dips for 1 second, retrying with fixed exponential delays ($2^n$) causes all 100 jobs to retry at the exact same millisecond, re-saturating the recovering service.\n")
                    .append("- In `RetryPolicy.java`, TaskForge applies **Full Randomized Jitter**:\n")
                    .append("  $$\\text{Delay} = \\text{Base} \\times 2^{(\\text{attempt}-1)} \\pm (\\text{Delay} \\times \\text{JitterFactor} \\times \\text{random}())$$\n")
                    .append("- This breaks thread synchronization and distributes retry traffic evenly across the time continuum.");
        } else if (msg.contains("websocket") || msg.contains("sse") || msg.contains("polling") || msg.contains("stream")) {
            reply.append("### Telemetry Pipeline: Server-Sent Events (SSE) vs WebSockets\n\n")
                    .append("**Operational Advantages:**\n\n")
                    .append("1. **Over Polling:** Short polling sends HTTP GET requests every second, wasting server bandwidth and triggering redundant DB queries.\n")
                    .append("2. **Over WebSockets:** WebSockets require a protocol upgrade to a custom bidirectional TCP connection. A monitoring dashboard is strictly **server-to-client telemetry**.\n")
                    .append("3. **SSE Advantage:** Operates over standard HTTP (`text/event-stream`), traverses corporate proxies/firewalls effortlessly, and native browser `EventSource` automatically reconnects upon disconnection.");
        } else if (msg.contains("recovery") || msg.contains("crash") || msg.contains("reboot") || msg.contains("restart")) {
            reply.append("### Fault Recovery: Startup Crash Recovery\n\n")
                    .append("**Abrupt Process Failure Reconciliation:**\n\n")
                    .append("- When the node reboots, `recoverInterruptedJobs()` runs in `@PostConstruct`.\n")
                    .append("- It scans `jobs` for records left stranded in `RUNNING` status from the previous crash.\n")
                    .append("- It marks the interrupted attempt as `INTERRUPTED` in `job_attempts`, writes the timestamp, and reschedules the job for retry if attempts remain, or fails it gracefully.");
        } else if (msg.contains("idempotency") || msg.contains("deduplication") || msg.contains("duplicate")) {
            reply.append("### Distributed Idempotency & Deduplication Engine\n\n")
                    .append("**Exactly-Once Ingestion Invariants:**\n\n")
                    .append("- Tasks may declare a unique `idempotencyKey`.\n")
                    .append("- If a client re-submits due to network timeout, TaskForge resolves the key via `idx_jobs_idempotency_key`.\n")
                    .append("- Duplicate execution is prevented, a `JOB_DEDUPLICATED` event is emitted, and the existing job is returned.");
        } else if (msg.contains("dag") || msg.contains("pipeline") || msg.contains("cascade") || msg.contains("dependency")) {
            reply.append("### Directed Acyclic Graph (DAG) Pipeline Orchestration\n\n")
                    .append("**Sequential & Dependency Orchestration Invariants:**\n\n")
                    .append("1. **Prerequisite Declaration:** Tasks declare `\"dependsOnJobId\": \"<parent-uuid>\"` in their payload.\n")
                    .append("2. **Deferred Enqueuing:** Downstream steps remain in `QUEUED` until the parent step succeeds.\n")
                    .append("3. **Post-Commit Handoff:** In `recordSuccess()`, dependent steps are enqueued only after the parent commit.\n")
                    .append("4. **Cascading Failure Aborts:** If an upstream step fails or cancels, `cascadeAbortDependents()` recursively aborts descendants.\n")
                    .append("5. **Lineage Endpoint:** Query `GET /api/v1/jobs/{id}/dag` for parent, children, and step index.");
        } else {
            reply.append("### TaskForge SRE & Reliability Copilot (Incident Triage Mode)\n\n")
                    .append("Ready to triage incidents, analyze DLQ root causes, and audit cluster fault tolerance.\n\n")
                    .append("**Cluster Telemetry:**\n")
                    .append("- Active Worker Threads: **").append(snapshot.get("activeWorkers")).append("/").append(snapshot.get("totalWorkers")).append("**\n")
                    .append("- Queue Utilization: **").append(snapshot.get("queueSize")).append("/").append(snapshot.get("queueCapacity")).append("**\n")
                    .append("- Total Succeeded: **").append(snapshot.get("succeededJobs")).append("** | Dead Letter Queue: **").append(snapshot.get("failedJobs")).append("**\n\n")
                    .append("**Forensic Directives:**\n")
                    .append("1. *\"Analyze producer-consumer backpressure invariants\"*\n")
                    .append("2. *\"Audit two-phase transaction boundaries and connection pool safety\"*\n")
                    .append("3. *\"Inspect exponential backoff retry jitter formula\"*\n")
                    .append("4. *\"Verify startup crash recovery and interrupted job guarantees\"*");
        }

        return new ChatResponse(reply.toString(), requestedPersona, actions, snapshot, Instant.now());
    }

    // -------------------------------------------------------------
    // PERSONA 2: OPERATOR (DevOps, Cluster Health & Actions)
    // -------------------------------------------------------------
    private ChatResponse handleOperatorQuery(String msg, Map<String, Object> snapshot) {
        StringBuilder reply = new StringBuilder();
        List<ChatAction> actions = new ArrayList<>();

        int activeWorkers = (int) snapshot.get("activeWorkers");
        int totalWorkers = (int) snapshot.get("totalWorkers");
        int queueSize = (int) snapshot.get("queueSize");
        int queueCapacity = (int) snapshot.get("queueCapacity");
        long failedJobs = (long) snapshot.get("failedJobs");
        long queuedJobs = (long) snapshot.get("queuedJobs");
        boolean isPaused = (boolean) snapshot.get("isPaused");

        if (msg.contains("health") || msg.contains("status") || msg.contains("load") || msg.contains("metrics")) {
            reply.append("### Cluster Telemetry & Health Audit\n\n")
                    .append("- **Engine State:** ").append(isPaused ? "⏸️ **PAUSED**" : "🟢 **ACTIVE (HEALTHY)**").append("\n")
                    .append("- **Worker Pool Utilization:** `").append(activeWorkers).append("/").append(totalWorkers).append("` threads busy (")
                    .append(totalWorkers > 0 ? (activeWorkers * 100 / totalWorkers) : 0).append("%)\n")
                    .append("- **Execution Buffer Fill:** `").append(queueSize).append("/").append(queueCapacity).append("` slots (")
                    .append(queueCapacity > 0 ? (queueSize * 100 / queueCapacity) : 0).append("%)\n")
                    .append("- **Pending Queue Backlog:** ").append(queuedJobs).append(" tasks\n")
                    .append("- **Dead Letter Queue (DLQ):** ").append(failedJobs).append(" tasks permanently failed\n\n");

            if (failedJobs > 0) {
                reply.append("⚠️ **DLQ Alert:** There are **").append(failedJobs).append("** failed tasks requiring operator review.");
                actions.add(new ChatAction("REPLAY_DLQ", "Replay All DLQ", "Replay all failed tasks", "/api/v1/jobs/dlq/replay", "POST"));
            }
            if (queuedJobs > 0) {
                actions.add(new ChatAction("CANCEL_PENDING", "Cancel Pending", "Cancel all queued tasks", "/api/v1/jobs/bulk/cancel", "POST"));
            }
            actions.add(new ChatAction("SUBMIT_SAMPLE", "Run 5-Job Smoke Test", "Submit 5 compute jobs", "/api/v1/jobs/batch", "POST"));
        } else if (msg.contains("failed") || msg.contains("dlq") || msg.contains("error")) {
            reply.append("### Dead Letter Queue (DLQ) Diagnostic Report\n\n");
            List<Object[]> topReasons = jobRepository.getTopFailureReasons(PageRequest.of(0, 3));
            if (topReasons.isEmpty()) {
                reply.append("✅ **Clean Health:** Zero tasks in Dead Letter Queue. All attempts succeeded.");
            } else {
                reply.append("Found **").append(failedJobs).append("** failed tasks. Top root causes recorded:\n\n");
                for (Object[] r : topReasons) {
                    reply.append("- `").append(r[0]).append("`: **").append(r[1]).append(" occurrences**\n");
                }
                reply.append("\nYou can replay these jobs back into the execution queue once upstream systems have recovered.");
                actions.add(new ChatAction("REPLAY_DLQ", "Replay DLQ", "Replay failed jobs", "/api/v1/jobs/dlq/replay", "POST"));
                actions.add(new ChatAction("PURGE_TERMINAL", "Purge Terminal", "Clear failed and finished jobs", "/api/v1/jobs/bulk/terminal", "DELETE"));
            }
        } else {
            reply.append("### TaskForge Cluster Operations Console\n\n")
                    .append("Ready to manage cluster concurrency, queue backpressure, and DLQ remediation.\n\n")
                    .append("- **Active Workers:** ").append(activeWorkers).append("/").append(totalWorkers).append("\n")
                    .append("- **Queue Fill:** ").append(queueSize).append("/").append(queueCapacity).append("\n")
                    .append("- **Dead Letter Queue:** ").append(failedJobs).append(" jobs\n\n")
                    .append("**Operator Commands:**\n")
                    .append("- *\"Show cluster health\"*\n")
                    .append("- *\"Show DLQ errors\"*\n")
                    .append("- *\"Replay DLQ\"*\n")
                    .append("- *\"Cancel pending jobs\"*\n")
                    .append("- *\"Purge finished records\"*");

            actions.add(new ChatAction("SUBMIT_SAMPLE", "Dispatch Smoke Test (5)", "Dispatch 5 test jobs", "/api/v1/jobs/batch", "POST"));
            if (failedJobs > 0) {
                actions.add(new ChatAction("REPLAY_DLQ", "Replay DLQ", "Replay failed jobs", "/api/v1/jobs/dlq/replay", "POST"));
            }
        }

        return new ChatResponse(reply.toString(), "OPERATOR", actions, snapshot, Instant.now());
    }

    // -------------------------------------------------------------
    // PERSONA 3: DEVELOPER (APIs, JSON Payloads, Internals)
    // -------------------------------------------------------------
    private ChatResponse handleDeveloperQuery(String msg, Map<String, Object> snapshot) {
        StringBuilder reply = new StringBuilder();
        List<ChatAction> actions = new ArrayList<>();

        if (msg.contains("api") || msg.contains("endpoint") || msg.contains("curl") || msg.contains("rest")) {
            reply.append("### REST API Reference (Base: `/api/v1`)\n\n")
                    .append("| Method | Endpoint | Description |\n")
                    .append("|---|---|---|\n")
                    .append("| `POST` | `/jobs` | Create single job (`delaySeconds` supported) |\n")
                    .append("| `POST` | `/jobs/batch` | High-throughput batch dispatch (202 Accepted) |\n")
                    .append("| `GET` | `/jobs` | Paginated search, sorting, and status filtering |\n")
                    .append("| `POST` | `/jobs/{id}/retry` | Manually requeue failed/cancelled job |\n")
                    .append("| `POST` | `/jobs/bulk/cancel` | Cancel all pending queued and delayed jobs |\n")
                    .append("| `DELETE`| `/jobs/bulk/terminal` | Bulk purge terminal records with cascade |\n")
                    .append("| `GET` | `/events/stream` | Server-Sent Events (SSE) real-time stream |\n")
                    .append("| `GET` | `/engine/status` | Engine state, queue capacity & worker threads |\n\n")
                    .append("**cURL Example (Submit Job):**\n")
                    .append("```bash\n")
                    .append("curl -X POST http://localhost:8080/api/v1/jobs \\\n")
                    .append("  -H \"Content-Type: application/json\" \\\n")
                    .append("  -d '{\"name\":\"Prime Calculation\",\"jobType\":\"COMPUTE\",\"priority\":5,\"maxAttempts\":3}'\n")
                    .append("```");
        } else if (msg.contains("payload") || msg.contains("json") || msg.contains("schema")) {
            reply.append("### Job Request Schemas\n\n")
                    .append("**1. Single Job (`POST /api/v1/jobs`):**\n")
                    .append("```json\n")
                    .append("{\n")
                    .append("  \"name\": \"ETL Pipeline Ingestion\",\n")
                    .append("  \"jobType\": \"DATA_PROCESSING\",\n")
                    .append("  \"priority\": 2,\n")
                    .append("  \"delaySeconds\": 0,\n")
                    .append("  \"payload\": \"{\\\"records\\\": 50000}\",\n")
                    .append("  \"maxAttempts\": 3\n")
                    .append("}\n")
                    .append("```\n\n")
                    .append("**2. Batch Dispatch (`POST /api/v1/jobs/batch`):**\n")
                    .append("```json\n")
                    .append("{\n")
                    .append("  \"count\": 50,\n")
                    .append("  \"prefix\": \"burst-test\",\n")
                    .append("  \"jobType\": \"SIMULATED_IO\",\n")
                    .append("  \"durationMs\": 100,\n")
                    .append("  \"failureRate\": 0.15,\n")
                    .append("  \"isSequential\": false\n")
                    .append("}\n")
                    .append("```");
        } else if (msg.contains("idempotency") || msg.contains("deduplication") || msg.contains("duplicate")) {
            reply.append("### Distributed Idempotency & Deduplication Engine\n\n")
                    .append("**Single-Job Ingestion (`POST /api/v1/jobs`):**\n\n")
                    .append("```bash\n")
                    .append("curl -X POST http://localhost:8080/api/v1/jobs \\\n")
                    .append("  -H \"Content-Type: application/json\" \\\n")
                    .append("  -d '{\n")
                    .append("    \"name\": \"Financial Settlement #9021\",\n")
                    .append("    \"jobType\": \"DATA_PROCESSING\",\n")
                    .append("    \"idempotencyKey\": \"tx-settlement-9021-batch\",\n")
                    .append("    \"payload\": \"{\\\"amount\\\": 5000}\"\n")
                    .append("  }'\n")
                    .append("```\n\n")
                    .append("**Behavior:** Re-submitting the same `idempotencyKey` returns the existing job with zero duplicate execution.");
        } else if (msg.contains("dag") || msg.contains("pipeline") || msg.contains("workflow") || msg.contains("dependency")) {
            reply.append("### Directed Acyclic Graph (DAG) Pipeline Orchestration\n\n")
                    .append("- **Prerequisite Declaration:** Tasks declare `\"dependsOnJobId\": \"<parent-uuid>\"` in payload.\n")
                    .append("- **Post-Commit Enqueue:** Dependent steps are enqueued only after the prerequisite transaction commits.\n")
                    .append("- **Cascading Abort:** If parent step fails or cancels, descendants abort cleanly without orphaned tasks.\n")
                    .append("- **DAG Hierarchy API:** Query `GET /api/v1/jobs/{id}/dag` for parent, children, and step lineage.");
        } else {
            reply.append("### TaskForge Developer & Architecture Assistant\n\n")
                    .append("Ask me about endpoint contracts, JSON payload schemas, concurrency primitives, or testing strategies.\n\n")
                    .append("- **Spring Boot Profile:** `default` (embedded H2) / `postgres`\n")
                    .append("- **SSE Stream:** `GET /api/v1/events/stream`\n")
                    .append("- **Swagger Documentation:** `http://localhost:8080/swagger-ui.html`\n\n")
                    .append("**Quick Developer Queries:**\n")
                    .append("1. *\"Show REST API endpoints\"*\n")
                    .append("2. *\"Show JSON payload schema for single and batch jobs\"*\n")
                    .append("3. *\"How does the bounded JobQueue enforce backpressure?\"*");

            actions.add(new ChatAction("SUBMIT_SAMPLE", "Submit 5 Test Jobs", "Dispatch 5 compute jobs", "/api/v1/jobs/batch", "POST"));
        }

        return new ChatResponse(reply.toString(), "DEVELOPER", actions, snapshot, Instant.now());
    }

    // -------------------------------------------------------------
    // PERSONA 4: AUDITOR / NOVICE (Audit Trails, SLA Verification & Analogies)
    // -------------------------------------------------------------
    private ChatResponse handleAuditorQuery(String msg, Map<String, Object> snapshot, String requestedPersona) {
        StringBuilder reply = new StringBuilder();
        List<ChatAction> actions = new ArrayList<>();

        if ("AUDITOR".equalsIgnoreCase(requestedPersona) || msg.contains("audit") || msg.contains("sla") || msg.contains("compliance") || msg.contains("immutable")) {
            reply.append("### TaskForge Audit & Compliance Copilot (Audit Mode)\n\n")
                    .append("**Data Integrity & Forensic Audit Specification:**\n\n")
                    .append("- **Immutable Attempt Logs (`job_attempts`):** Every execution attempt records worker ID, start timestamp, end timestamp, duration, and terminal outcome.\n")
                    .append("- **Event Chain Integrity (`job_events`):** Monotonically ordered lifecycle events (`JOB_SUBMITTED` -> `JOB_STARTED` -> `JOB_SUCCEEDED` / `JOB_FAILED`).\n")
                    .append("- **Optimistic Concurrency Lock:** Enforced via Hibernate `@Version` to guarantee zero lost updates or conflicting state transitions across concurrent worker threads.\n")
                    .append("- **Audit Snapshot:**\n")
                    .append("  - Succeeded Transactions: **").append(snapshot.get("succeededJobs")).append("**\n")
                    .append("  - Quarantined (DLQ): **").append(snapshot.get("failedJobs")).append("**\n")
                    .append("  - Cancelled Operations: **").append(snapshot.get("cancelledJobs")).append("**\n");
            actions.add(new ChatAction("SUBMIT_SAMPLE", "Run Audited Smoke Test", "Submit audited jobs", "/api/v1/jobs/batch", "POST"));
        } else if (msg.contains("what is") || msg.contains("explain") || msg.contains("simple") || msg.contains("dumb") || msg.contains("how it works")) {
            reply.append("### What is TaskForge in Simple Words?\n\n")
                    .append("Imagine a busy **restaurant kitchen**:\n\n")
                    .append("1. **Customers ordering food (You / API):** When customers order 50 meals at once, the cashiers can't cook the food immediately. They write order tickets.\n")
                    .append("2. **The Order Ticket Rack (`JobQueue`):** The tickets sit in a line. The rack can only hold 100 tickets. If it gets full, the restaurant politely asks new customers to wait (that's **backpressure**).\n")
                    .append("3. **The Chefs (`Worker Threads`):** There are 4 chefs working at the stoves. Each chef takes one order ticket at a time, cooks it, marks it finished, and takes the next ticket.\n")
                    .append("4. **Spilled Soup (`Automatic Retry`):** If a chef accidentally drops a soup, they don't give up. They wait a moment and re-cook it up to 3 times before finally discarding the ticket to the **Dead Letter Bin**.\n\n")
                    .append("TaskForge does this for computers: it takes heavy tasks, lines them up safely, and uses background workers so your computer never freezes!");
            actions.add(new ChatAction("SUBMIT_SAMPLE", "Try Cooking 5 Orders", "Launch 5 test tasks", "/api/v1/jobs/batch", "POST"));
        } else if (msg.contains("status") || msg.contains("how is it") || msg.contains("fine")) {
            int busy = (int) snapshot.get("activeWorkers");
            int total = (int) snapshot.get("totalWorkers");
            long failed = (long) snapshot.get("failedJobs");

            reply.append("### Simple Status Check\n\n")
                    .append("Everything is working great!\n\n")
                    .append("- **Chefs working:** ").append(busy).append(" of ").append(total).append(" chefs are currently busy.\n")
                    .append("- **Orders in line:** ").append(snapshot.get("queueSize")).append(" waiting.\n")
                    .append("- **Completed meals:** ").append(snapshot.get("succeededJobs")).append(" tasks finished successfully.\n");

            if (failed > 0) {
                reply.append("- **Discarded orders:** ").append(failed).append(" tasks had errors.\n");
                actions.add(new ChatAction("REPLAY_DLQ", "Re-cook Failed Tasks", "Replay failed jobs", "/api/v1/jobs/dlq/replay", "POST"));
            }
        } else {
            reply.append("### TaskForge Audit & Compliance Guide\n\n")
                    .append("System integrity is operating under normal SLA parameters.\n\n")
                    .append("You can query:\n")
                    .append("- *\"Show immutable audit trail invariants\"*\n")
                    .append("- *\"Verify SLA and state machine transitions\"*\n")
                    .append("- *\"Is the cluster healthy right now?\"*");
            actions.add(new ChatAction("SUBMIT_SAMPLE", "Run a 5-Job Test", "Launch sample jobs", "/api/v1/jobs/batch", "POST"));
        }

        return new ChatResponse(reply.toString(), requestedPersona, actions, snapshot, Instant.now());
    }
}
