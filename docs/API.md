# TaskForge REST API Specification

Base path: `/api/v1`  
Interactive OpenAPI UI: `http://localhost:8080/swagger-ui.html`  
OpenAPI JSON Specification: `http://localhost:8080/api-docs`

---

## 1. Jobs API (`/api/v1/jobs`)

### 1.1 Create Single Job
- **Method**: `POST /api/v1/jobs`
- **Request Body**:
```json
{
  "name": "Prime Number Search",
  "jobType": "COMPUTE",
  "priority": 1,
  "payload": "{\"limit\": 10000}",
  "maxAttempts": 3,
  "delaySeconds": 0,
  "idempotencyKey": "order-settle-txn-982314"
}
```
*Note: If `idempotencyKey` is provided and a job with this key already exists, the server atomically deduplicates the submission and returns the existing task (`200 OK`) without repeating execution side effects. If `delaySeconds > 0`, the job enters `RETRY_WAIT` status and transitions to `QUEUED` automatically once the scheduled execution timestamp is reached.*
- **Response** (`201 Created` or `200 OK` on deduplication):
```json
{
  "id": "e30dcda7-5757-4148-8df0-7d6f51bfd0be",
  "name": "Prime Number Search",
  "jobType": "COMPUTE",
  "status": "QUEUED",
  "priority": 1,
  "payload": "{\"limit\": 10000}",
  "createdAt": "2026-10-18T12:00:00Z",
  "attemptCount": 0,
  "maxAttempts": 3,
  "idempotencyKey": "order-settle-txn-982314",
  "version": 0
}
```
- **Error Responses**:
  - `400 Bad Request`: Input validation failed (e.g. blank name).
  - `429 Too Many Requests`: Execution queue buffer capacity reached.

### 1.2 Submit Batch of Jobs (Primary Demonstration)
- **Method**: `POST /api/v1/jobs/batch`
- **Request Body**:
```json
{
  "count": 100,
  "prefix": "batch-sim",
  "jobType": "SIMULATED_IO",
  "failureRate": 0.2,
  "simulatedDurationMs": 80,
  "maxAttempts": 3
}
```
- **Response** (`202 Accepted`):
```json
{
  "submittedCount": 100,
  "rejectedCount": 0,
  "jobIds": ["e30dcda7...", "a1b2c3d4..."],
  "message": "Submitted 100 jobs (100 enqueued immediately, 0 buffered/backpressured)"
}
```

### 1.3 List Jobs (Paginated & Filtered)
- **Method**: `GET /api/v1/jobs?status=FAILED&jobType=FAILURE_DEMO&search=sim&page=0&size=20&sortBy=createdAt&direction=DESC`
- **Response** (`200 OK`):
```json
{
  "content": [ ... ],
  "totalElements": 42,
  "totalPages": 3,
  "size": 20,
  "number": 0
}
```

### 1.4 Get Job Details
- **Method**: `GET /api/v1/jobs/{id}`
- **Response** (`200 OK`): Full `JobResponse` object.
- **Error Response**: `404 Not Found` if job ID does not exist.

### 1.5 Get Job DAG Hierarchy
- **Method**: `GET /api/v1/jobs/{id}/dag`
- **Response** (`200 OK`):
```json
{
  "currentJob": {
    "id": "e30dcda7-5757-4148-8df0-7d6f51bfd0be",
    "name": "Pipeline Step 2 - Data Enrichment",
    "jobType": "DATA_PROCESSING",
    "status": "RUNNING",
    "priority": 5,
    "payload": "{\"dependsOnJobId\":\"8f64dc55...\",\"pipelineStep\":2}",
    "createdAt": "2026-11-14T10:00:00Z",
    "attemptCount": 1,
    "maxAttempts": 3,
    "workerId": "taskforge-worker-1",
    "executionDurationMs": 45,
    "queueWaitDurationMs": 12,
    "idempotencyKey": null,
    "version": 1
  },
  "parentJob": {
    "id": "8f64dc55-481a-4cdd-987c-bcdbc307967a",
    "name": "Pipeline Step 1 - Ingestion",
    "status": "SUCCEEDED"
  },
  "downstreamJobs": [
    {
      "id": "b2c3d4e5-6789-4012-89ab-cdef01234567",
      "name": "Pipeline Step 3 - Settlement",
      "status": "QUEUED"
    }
  ],
  "pipelineStep": 2
}
```

### 1.6 Get Attempt History
- **Method**: `GET /api/v1/jobs/{id}/attempts`
- **Response** (`200 OK`):
```json
[
  {
    "id": "92f70418-7b98-4c47-9204-7476837881c1",
    "jobId": "e30dcda7-5757-4148-8df0-7d6f51bfd0be",
    "attemptNumber": 1,
    "workerId": "taskforge-worker-2",
    "startedAt": "2026-10-18T12:00:01Z",
    "completedAt": "2026-10-18T12:00:01.082Z",
    "outcome": "FAILED",
    "errorMessage": "Simulated rate failure (roll=0.12 < rate=0.20)",
    "durationMs": 82
  }
]
```

### 1.6 Get Event History
- **Method**: `GET /api/v1/jobs/{id}/events`
- **Response** (`200 OK`): Ordered chronological audit events for the job.

### 1.7 Cancel Job
- **Method**: `POST /api/v1/jobs/{id}/cancel`
- **Response** (`200 OK`): Updated Job with status `CANCELLED`.
- **Error**: `409 Conflict` if job is already in terminal state.

### 1.8 Retry Job
- **Method**: `POST /api/v1/jobs/{id}/retry`
- **Response** (`200 OK`): Requeued job with status `QUEUED`.
- **Error**: `409 Conflict` if job is not in `FAILED` or `CANCELLED` state.

### 1.9 Bulk Cancel Pending Jobs
- **Method**: `POST /api/v1/jobs/bulk/cancel`
- **Response** (`200 OK`):
```json
{
  "cancelledCount": 15,
  "message": "Cancelled 15 pending jobs"
}
```

### 1.10 Purge Terminal Jobs
- **Method**: `DELETE /api/v1/jobs/bulk/terminal`
- **Response** (`200 OK`):
```json
{
  "purgedCount": 84,
  "message": "Purged 84 terminal jobs"
}
```
*Note: Cascading deletion cleans up associated execution attempts and lifecycle audit event records at the database level.*

---

## 2. Engine Telemetry & Configuration (`/api/v1/engine`)

### 2.1 Get Status & Worker Snapshots
- **Method**: `GET /api/v1/engine/status`
- **Response** (`200 OK`):
```json
{
  "isRunning": true,
  "isPaused": false,
  "workerCount": 4,
  "activeWorkers": 2,
  "queueSize": 18,
  "queueCapacity": 100,
  "workers": [
    {
      "workerId": "taskforge-worker-0",
      "state": "BUSY",
      "currentJobId": "e30dcda7...",
      "currentJobName": "sim-batch-001",
      "attemptNumber": 1,
      "elapsedMs": 45
    },
    {
      "workerId": "taskforge-worker-1",
      "state": "IDLE"
    }
  ]
}
```

### 2.2 Update Engine Settings
- **Method**: `POST /api/v1/engine/settings`
- **Request Body**:
```json
{
  "workerCount": 8,
  "queueCapacity": 150,
  "maxAttempts": 4,
  "baseBackoffMs": 1500
}
```

### 2.3 Pause / Resume Engine
- **Method**: `POST /api/v1/engine/pause`
- **Method**: `POST /api/v1/engine/resume`

---

## 3. Metrics API (`/api/v1/metrics`)

### 3.1 Get Aggregate Metrics
- **Method**: `GET /api/v1/metrics`
- **Response** (`200 OK`):
```json
{
  "totalJobs": 100,
  "queuedJobs": 12,
  "runningJobs": 4,
  "retryWaitJobs": 6,
  "succeededJobs": 72,
  "failedJobs": 6,
  "cancelledJobs": 0,
  "successRatePercent": 92.3,
  "failureRatePercent": 7.7,
  "averageExecutionDurationMs": 76.4,
  "averageQueueWaitDurationMs": 34.1,
  "throughputPerMinute": 145.0,
  "topFailureReasons": [
    { "reason": "Simulated rate failure", "count": 6 }
  ]
}
```

---

## 4. Real-Time Streaming (`/api/v1/events/stream`)

- **Protocol**: Server-Sent Events (SSE)
- **Content-Type**: `text/event-stream`
- **Event Types**:
  - `CONNECTED`: Handshake event on initial connection.
  - `HEARTBEAT`: Dispatched every 15 seconds to maintain proxy liveness.
  - `JOB_EVENT`: Payload containing `JobLifecycleEvent`:
```json
{
  "jobId": "e30dcda7-5757-4148-8df0-7d6f51bfd0be",
  "jobName": "sim-batch-001",
  "eventType": "JOB_STARTED",
  "status": "RUNNING",
  "message": "Execution attempt #1 started by taskforge-worker-2",
  "workerId": "taskforge-worker-2",
  "timestamp": "2026-10-18T12:00:01Z"
}
```

---

## 5. TaskForge Ops Copilot API (`/api/v1/assistant`)

### 5.1 Submit Query / Action Directive
- **Method**: `POST /api/v1/assistant/chat`
- **Request Body**:
```json
{
  "message": "Why are jobs failing in the Dead Letter Queue?",
  "persona": "SRE",
  "actionId": null
}
```
*Enterprise Personas: `SRE` (Root cause analysis & incident triage), `OPERATOR` (Fleet health & capacity operations), `DEVELOPER` (SDKs & REST integration), `AUDITOR` (Compliance, SLA percentiles & ACID guarantees).*
- **Response** (`200 OK`):
```json
{
  "reply": "### SRE Incident Triage Diagnostic: DLQ Root Cause Analysis...",
  "persona": "SRE",
  "actions": [
    {
      "id": "REPLAY_DLQ",
      "label": "Replay All in DLQ",
      "description": "Re-enqueue all terminal failed jobs for execution",
      "endpoint": "/api/v1/jobs/replay-dlq",
      "httpMethod": "POST"
    }
  ],
  "systemSnapshot": {
    "activeWorkers": 0,
    "totalWorkers": 2,
    "queueSize": 0,
    "queueCapacity": 100,
    "failedJobs": 4
  },
  "timestamp": "2026-11-14T10:30:00Z"
}
```

### 5.2 Get Suggested Prompts
- **Method**: `GET /api/v1/assistant/suggestions?persona=SRE`
- **Response** (`200 OK`):
```json
[
  "Why are jobs failing in the Dead Letter Queue?",
  "How does TaskForge prevent duplicate execution with idempotency keys?",
  "Analyze queue backpressure and explain the 429 status code",
  "How does the DAG scheduler handle cascading aborts on upstream failures?",
  "Audit the two-phase transaction boundaries and thread preemption"
]
```
