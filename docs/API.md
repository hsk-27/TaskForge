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
  "delaySeconds": 0
}
```
*Note: If `delaySeconds > 0`, the job enters `RETRY_WAIT` status and transitions to `QUEUED` automatically once the scheduled execution timestamp is reached.*
- **Response** (`201 Created`):
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

### 1.5 Get Attempt History
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

## 5. Intelligent Assistant API (`/api/v1/assistant`)

### 5.1 Submit Query / Action Directive
- **Method**: `POST /api/v1/assistant/chat`
- **Request Body**:
```json
{
  "message": "Explain the producer-consumer pattern in TaskForge",
  "persona": "STUDENT",
  "actionId": null
}
```
*Personas: `STUDENT` (Viva prep), `OPERATOR` (DevOps & health), `DEVELOPER` (APIs & schemas), `NOVICE` (Plain English analogies).*
- **Response** (`200 OK`):
```json
{
  "reply": "### B.Tech Viva Defense: Producer-Consumer Architecture...",
  "persona": "STUDENT",
  "actions": [
    {
      "id": "SUBMIT_SAMPLE",
      "label": "Run 5-Job Smoke Test",
      "description": "Submit 5 compute jobs",
      "endpoint": "/api/v1/jobs/batch",
      "httpMethod": "POST"
    }
  ],
  "systemSnapshot": {
    "activeWorkers": 0,
    "totalWorkers": 4,
    "queueSize": 0,
    "queueCapacity": 100,
    "failedJobs": 0
  },
  "timestamp": "2026-11-03T14:26:30Z"
}
```

### 5.2 Get Suggested Questions
- **Method**: `GET /api/v1/assistant/suggestions?persona=STUDENT`
- **Response** (`200 OK`):
```json
[
  "Explain the producer-consumer pattern in JobQueue",
  "Why is task execution outside @Transactional?",
  "Explain the exponential backoff formula and jitter",
  "Why Server-Sent Events instead of WebSockets?",
  "How does crash recovery work on JVM reboot?"
]
```
