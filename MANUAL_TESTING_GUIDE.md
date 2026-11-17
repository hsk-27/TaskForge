# TaskForge — Comprehensive Manual Testing & Verification Guide

This guide provides a step-by-step manual test plan to evaluate all features, edge cases, failure scenarios, and concurrency guarantees in TaskForge. Follow these hands-on steps to verify that every component functions properly.

---

## 1. Quick Launch (Environment Setup)

Open two separate terminals (PowerShell or Bash):

### Terminal 1: Start Backend (Port 8080)
```powershell
cd backend
mvn spring-boot:run
```
- **REST API:** `http://localhost:8080/api/v1/jobs`
- **Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **Actuator Health:** `http://localhost:8080/actuator/health`

### Terminal 2: Start Frontend (Port 5173)
```powershell
cd frontend
npm run dev
```
- **Developer Console:** `http://localhost:5173`

---

## 2. Test Scenarios

### Test 1: Single Job Dispatch & Execution Lifecycle
**Goal:** Verify standard job lifecycle (`QUEUED -> RUNNING -> SUCCEEDED`), execution duration tracking, and live SSE event stream.

1. In the top navigation bar, click **"New Job"**.
2. Fill out the modal:
   - **Job Name:** `manual-test-compute-01`
   - **Workload Type:** `COMPUTE`
   - **Priority:** `5`
   - **Max Attempts:** `3`
   - **Delay (Seconds):** `0`
   - **Payload JSON:** `{"iterations": 100000, "durationMs": 300}`
3. Click **"Dispatch Job"**.
4. **Observations to verify:**
   - A success notification appears in the top-right.
   - The job appears in the **Job Explorer** table.
   - In **Event Stream**, a `JOB_SUBMITTED` event appears instantly.
   - In the **Live Workers** panel, an idle worker thread changes to `BUSY`, with its progress bar and elapsed duration ticking.
   - The job transitions to `RUNNING`, then cleanly to `SUCCEEDED`.
   - Click on the job row to open the **Job Details Modal**:
     - Verify attempt #1 is recorded with worker ID, start time, and duration (~300ms).
     - Verify the lifecycle event chain records `JOB_SUBMITTED`, `JOB_STARTED`, and `JOB_SUCCEEDED`.

---

### Test 2: Priority Queue Preemption
**Goal:** Prove that the bounded queue prioritizes high-priority jobs over lower-priority jobs regardless of submission order.

1. In the header bar, click **"Pause Engine"** (turns amber `Engine: Paused`).
2. Dispatch a Low-Priority Job:
   - Click **"New Job"**.
   - Name: `low-pri-task`, Workload: `COMPUTE`, Priority: `1`. Click **Dispatch**.
3. Dispatch a High-Priority Job:
   - Click **"New Job"**.
   - Name: `high-pri-task`, Workload: `COMPUTE`, Priority: `10`. Click **Dispatch**.
4. In the **Job Explorer**, observe both jobs are waiting in `QUEUED`.
5. Click **"Resume Engine"** in the header.
6. **Observations to verify:**
   - In **Live Workers**, notice `high-pri-task` (Priority 10) is claimed and processed **first**, even though `low-pri-task` (Priority 1) was submitted earlier.
   - Once `high-pri-task` completes, workers pick up `low-pri-task`.

---

### Test 3: Sequential DAG Pipeline (Happy Path)
**Goal:** Verify multi-step sequential execution where step $N$ only starts when step $N-1$ succeeds.

1. In the header bar, click the **"ETL Pipeline (5)"** quick-action button.
2. Navigate to **Job Explorer**.
3. **Observations to verify:**
   - 5 jobs are created: `etl-step01` through `etl-step05`.
   - `etl-step01` executes immediately.
   - Steps 2 through 5 remain in `QUEUED` in the database awaiting their respective prerequisites.
   - In **Event Stream**, observe sequential events:
     - `JOB_STARTED` for step 1 -> `JOB_SUCCEEDED` for step 1 -> `JOB_UNBLOCKED` for step 2 -> `JOB_STARTED` for step 2 ... down to step 5.
   - Click on `etl-step02`:
     - In the modal, locate the **Sequential Pipeline Dependency** card:
       - Displays: `Step #2`
       - Prerequisite Job ID: matches `etl-step01`'s ID.
   - Verify all 5 steps complete with `SUCCEEDED` status.

---

### Test 4: DAG Cascading Abort & Orphan Prevention (Fault Injection)
**Goal:** Verify that a mid-pipeline failure cleanly cascades down the entire DAG tree, preventing orphaned tasks from lingering in `QUEUED`.

1. Click **"New Job"**.
   - Create Step 1:
     - Name: `dag-fail-step1`, Type: `SIMULATED_IO`, Priority: `5`, Max Attempts: `1`.
     - Payload: `{"durationMs": 100}`
     - Click **Dispatch**. Note its generated Job ID (e.g. `id-1`).
2. Create Step 2 (The Failing Step):
   - Name: `dag-fail-step2`, Type: `FAILURE_DEMO`, Priority: `5`, Max Attempts: `1`.
   - Payload: `{"dependsOnJobId": "<paste-id-1>", "mode": "alwaysFail"}`
   - Click **Dispatch**. Note its generated Job ID (e.g. `id-2`).
3. Create Step 3 (The Dependent Child):
   - Name: `dag-fail-step3`, Type: `REPORT_GENERATION`, Priority: `5`, Max Attempts: `1`.
   - Payload: `{"dependsOnJobId": "<paste-id-2>"}`
   - Click **Dispatch**.
4. **Observations to verify:**
   - Step 1 executes and finishes `SUCCEEDED`.
   - Step 2 is unblocked, executes, and fails permanently (`FAILED`).
   - Step 3 **immediately cascades to `FAILED`** without worker execution!
   - Event Stream logs: `JOB_FAILED` on Step 3 with reason: `"Dependency failure: Prerequisite step ... failed permanently"`.
   - No tasks remain stranded in `QUEUED`.

---

### Test 5: Operator Cancellation & Worker Thread Interruption
**Goal:** Verify cooperative thread interruption and cascading cancellation.

1. Click **"New Job"**.
   - Name: `long-running-job`
   - Type: `SIMULATED_IO`
   - Priority: `5`
   - Payload: `{"durationMs": 15000}` (runs for 15 seconds)
   - Click **Dispatch**.
2. Immediately go to **Live Workers**:
   - Locate the worker thread running `long-running-job`.
3. In **Job Explorer**, click the red **"Cancel"** / **"Interrupt"** button on this job row (or open its details modal and click **"Interrupt Worker"**).
4. **Observations to verify:**
   - The worker immediately halts and returns to `IDLE` within milliseconds (Java thread cooperative interruption via `Thread.currentThread().isInterrupted()`).
   - The job status transitions to `CANCELLED`.
   - The attempt record logs outcome: `CANCELLED`.

---

### Test 6: Exponential Backoff & Retry with Jitter
**Goal:** Verify automated retries with backoff delay for transient faults.

1. Navigate to the **"Failure Lab"** tab.
2. Click **"Trigger Transient Network Flap (3 Retries)"**.
3. Switch back to **Job Explorer** or watch the **Dashboard**:
   - Attempt 1 executes and fails.
   - Status changes to `RETRY_WAIT`.
   - Event stream shows: `RETRY_SCHEDULED: Attempt #1 failed ... Retry #2 scheduled in 2s`.
   - The system waits for the scheduled delay.
   - The background poller re-enqueues the job to `QUEUED`.
   - Attempt 2 executes and fails. Scheduled retry in ~4s.
   - Attempt 3 executes and **succeeds**.
   - Job final state is `SUCCEEDED` with `attemptCount = 3`.
4. Open the **Job Details Modal** to review the 3 distinct attempt cards with their respective durations and error logs.

---

### Test 7: Queue Backpressure & HTTP 429 Protection
**Goal:** Verify the bounded queue rejects excess submissions when capacity is reached.

1. In the header bar, click **"Pause Engine"**.
2. Navigate to the **Failure Lab** tab.
3. Click **"Trigger Queue Backpressure (Capacity Flood)"**.
4. **Observations to verify:**
   - The burst submits jobs up to queue capacity (default: 200).
   - Once capacity is exhausted, additional job submissions return HTTP `429 Too Many Requests`.
   - A warning banner or toast displays the queue rejection count.
   - The database and JVM remain responsive and do not crash with `OutOfMemoryError`.
5. Click **"Resume Engine"** to allow the workers to drain the queue.

---

### Test 8: Dead Letter Queue (DLQ) & Targeted Replay
**Goal:** Verify quarantine of failed jobs and safe replay without orphan cascades.

1. In **Job Explorer**, click the **"Failed"** filter button or the **"Dead Letter Queue"** tab.
2. Locate jobs that failed from previous tests.
3. Click **"Replay"** on an individual failed job, or click **"Replay All"**:
   - Job resets its status to `QUEUED`.
   - `maxAttempts` is extended (`maxAttempts = attemptCount + 3`).
   - Error messages are cleared.
   - For DAG pipelines, only root steps or steps whose parents already succeeded are enqueued; downstream steps wait safely.

---

### Test 9: Delayed Execution (`delaySeconds`)
**Goal:** Verify scheduling tasks into the future.

1. Click **"New Job"**.
   - Name: `delayed-task-10s`
   - Type: `REPORT_GENERATION`
   - Delay (Seconds): `10`
   - Click **Dispatch**.
2. **Observations to verify:**
   - Job is created immediately with status `RETRY_WAIT`.
   - In **Job Details Modal**, `Next Scheduled Retry` shows `T + 10s`.
   - For 10 seconds, workers ignore this task.
   - At second 10, the background retry poller detects the elapsed delay, moves the task to `QUEUED`, and a worker processes it to `SUCCEEDED`.

---

### Test 10: Dynamic Worker Pool Scaling
**Goal:** Verify increasing and decreasing worker threads at runtime without restart.

1. In the top bar or **Live Workers** tab, locate the **Worker Threads** slider/selector.
2. Currently running: `4` workers (`taskforge-worker-0` .. `taskforge-worker-3`).
3. Scale workers up to `8`:
   - Click **Save** or submit.
   - Immediately, 4 new OS worker threads (`taskforge-worker-4` through `taskforge-worker-7`) appear in `IDLE` state.
4. Scale workers down to `2`:
   - System gracefully signals excess threads to stop after their current job finishes.
   - Active worker count drops cleanly to 2.

---

### Test 11: TaskForge Ops Copilot
**Goal:** Verify offline, zero-API intelligent cluster reliability copilot guidance and remediation actions.

1. Press `Ctrl + /` (or click the **Ops Copilot** button in the header) to open the copilot drawer.
2. **Test Persona Switching:**
   - Click **"SRE & Incident Triage"**:
     - Type: *"Why are jobs failing in the Dead Letter Queue?"*
     - Verify response provides forensic root cause analysis, retry backoff jitter analysis, and one-click remediation pills (e.g. "Replay DLQ").
   - Click **"Cluster Operations"**:
     - Type: *"What is the current health of the system?"*
     - Verify response provides live active workers, queue size, and actionable execution buttons (e.g. "Run 10 Smoke Jobs", "Scale Workers", "Replay DLQ").
     - Click an action pill (e.g. **"Run 10 Smoke Jobs"**):
       - Notice 10 jobs are automatically submitted and processed in the background!
   - Click **"API & Integration"**:
     - Type: *"Show me the curl command to submit an idempotent job."*
     - Verify it provides accurate cURL and JSON payloads with `idempotencyKey`.

---

### Test 12: Server Crash & Startup Recovery
**Goal:** Verify fault tolerance when the JVM process is abruptly killed mid-execution.

1. In the frontend header, click **"Pause Engine"**.
2. Click **"Submit Batch (100 Jobs)"** or create a slow compute job (`durationMs: 30000`).
3. Click **"Resume Engine"** so workers claim the job (`status = RUNNING`).
4. In the backend terminal, forcefully kill the process (`Ctrl + C` or terminate terminal).
5. Restart the backend:
   ```powershell
   mvn spring-boot:run
   ```
6. **Observations to verify:**
   - Look at the console startup logs:
     ```text
     INFO ... [main] c.t.engine.ExecutionEngine : Startup recovery initiated: scanning for interrupted jobs...
     INFO ... [main] c.t.engine.ExecutionEngine : Found 1 jobs left in RUNNING status from previous crash.
     INFO ... [main] c.t.engine.ExecutionEngine : Rescheduled interrupted job [...] for retry attempt #2
     ```
   - Refresh the frontend:
     - The interrupted job was automatically rescued from `RUNNING` limbo and requeued.
     - The attempt record logs `outcome = INTERRUPTED` with crash timestamp.

---

## 3. Invariants & Acceptance Checklist

| Feature | Expected Invariant | Status |
| :--- | :--- | :---: |
| **Worker Threads** | Only execute outside database transactions (`PlatformTransactionManager`). | Verified |
| **Optimistic Locking** | `@Version` prevents race conditions on simultaneous state transitions. | Verified |
| **DAG Pipelines** | No downstream jobs execute out of order or spin-drop during commit. | Verified |
| **Cascading Aborts** | When parent fails/cancels, 100% of downstream descendants abort. | Verified |
| **Backpressure** | In-memory queue never exceeds capacity; returns HTTP 429 when full. | Verified |
| **Heartbeat SSE** | Zero polling; disconnects cleanly remove emitters without memory leaks. | Verified |
| **Zero External AI** | Chatbot assistant operates 100% offline with zero cloud API keys or fees. | Verified |
| **Production Architecture** | Enterprise-grade, clean domain-driven design with explicit transaction decoupling. | Verified |
