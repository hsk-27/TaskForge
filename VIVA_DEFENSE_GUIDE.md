# TaskForge — Final Year B.Tech Viva Defense & Capstone Guide

**Project Title:** TaskForge: High-Throughput Concurrent Background Task Orchestrator & Fault Recovery Engine  
**Degree:** Bachelor of Technology (B.Tech) in Computer Science & Engineering  
**Primary Tech Stack:** Java 21 LTS, Spring Boot 3.3.4, PostgreSQL 16 (H2 embedded persistent mode), React 18, TypeScript 5, Vite, Server-Sent Events (SSE)

---

## 1. The 90-Second Viva Elevator Pitch
> *"Good morning, esteemed examiners. My capstone project is **TaskForge**, an observable, concurrent background task orchestrator and fault recovery platform built in Java 21, Spring Boot, and React.*
>
> *In production systems like Uber, Shopify, or Netflix, heavy tasks—such as report generation, payment processing, and data ingestion—cannot run synchronously inside an HTTP request because they would block the web server and time out the client.*
>
> *TaskForge solves this by implementing an in-process, multi-threaded execution engine. Key technical highlights include:*
> 1. *A bounded, priority-aware buffer that enforces **backpressure** (HTTP 429) to prevent Out-Of-Memory crashes.*
> 2. *A **Two-Phase Transaction Decoupling** model that ensures heavy computations execute outside database transactions, preventing database connection pool starvation.*
> 3. *An **exponential retry backoff algorithm with randomized jitter** to mitigate the distributed 'thundering herd' problem.*
> 4. *An **SLA Watchdog and Crash Recovery Subsystem** that automatically reconciles interrupted jobs on system reboot.*
> 5. *A **Zero-Polling Real-Time Dashboard** powered by Server-Sent Events (SSE) that pushes execution telemetry to the browser with zero database amplification.*
>
> *All core components are accompanied by automated JUnit 5 and Vitest integration suites."*

---

## 2. Core CS Fundamentals & Theoretical Defense

### A. Operating Systems (OS) Concepts

#### 1. How does the Producer-Consumer pattern work in TaskForge?
- **Producers:** Incoming HTTP REST requests (`POST /api/v1/jobs` or `POST /api/v1/jobs/batch`) act as producers. They create task records and insert their IDs into the bounded execution queue.
- **Buffer:** `JobQueue.java` acts as the thread-safe bounded buffer. It wraps Java's `PriorityBlockingQueue` with a lock-free Compare-And-Swap (CAS) `AtomicInteger` to enforce a strict capacity limit.
- **Consumers:** A pool of dedicated OS worker threads (`taskforge-worker-0..N`) continuously poll the buffer, claim jobs, and execute their corresponding workload handlers.

#### 2. Why use dedicated threads instead of a standard `Executors.newCachedThreadPool()`?
- `newCachedThreadPool()` creates an **unbounded** number of threads. Under a burst of 10,000 tasks, the JVM would attempt to spawn 10,000 native OS threads, exhausting OS file descriptors, thrashing CPU context switches, and throwing `java.lang.OutOfMemoryError: unable to create native thread`.
- TaskForge uses a **bounded, fixed worker pool** with a configurable size (default 4). If work exceeds queue capacity, backpressure is exerted rather than spawning uncontrolled threads.

#### 3. How does cooperative cancellation work? Why not use `Thread.stop()`?
- `Thread.stop()` was deprecated and removed in Java because it unsafely terminates a thread midway, unlocking monitors and leaving objects in corrupted, inconsistent states.
- TaskForge uses **Cooperative Thread Interruption**:
  1. When an operator cancels a job or the SLA watchdog trips, `ExecutionEngine.interruptJobIfRunning(jobId)` calls `workerThread.interrupt()`.
  2. Long-running workload loops in `JobHandler` explicitly check `Thread.currentThread().isInterrupted()`.
  3. If true, the handler cleanly exits by throwing `InterruptedException`, allowing the `finally` block to record the duration, release worker state to `IDLE`, and save a `CANCELLED` audit record.

---

### B. Database Management Systems (DBMS) Concepts

#### 4. What is "Two-Phase Transaction Decoupling" and why is it critical?
- **The Problem:** In a naive implementation, a developer puts `@Transactional` on the entire `processJob()` method. If a compute task takes 10 seconds to finish, that worker thread holds a JDBC connection from HikariCP for 10 full seconds! With 10 workers running, all 10 connections are exhausted immediately. Incoming web requests cannot get a connection, and the entire REST API crashes due to `ConnectionTimeoutException`.
- **The TaskForge Solution:**
  - **Transaction 1 (~2ms):** `claimJob()` starts a short transaction, marks the job as `RUNNING` in the database, increments `attempt_count`, and commits immediately. The database connection is released back to HikariCP.
  - **Computation (e.g. 5 seconds):** The handler runs heavy computations or network I/O **outside any database transaction**. No database connection is held.
  - **Transaction 2 (~2ms):** `recordSuccess()` or `recordFailure()` opens a second short transaction to write the final status (`SUCCEEDED`, `RETRY_WAIT`, or `FAILED`) and execution duration to disk.

#### 5. How is Optimistic Locking implemented?
- In `Job.java`, we define `@Version private Long version;`.
- When Hibernate updates a job record, it generates SQL with a version check:
  ```sql
  UPDATE jobs SET status = 'RUNNING', version = version + 1 
  WHERE id = ? AND version = ?
  ```
- If another thread or worker modified the job simultaneously, the version check fails (0 rows updated), and Hibernate throws `OptimisticLockException`. This guarantees data integrity without holding heavy database row locks.

#### 6. Why use a Post-Commit Synchronization Hook (`afterCommit`)?
- In `JobService.java`:
  ```java
  TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
          executionEngine.enqueue(jobId, priority);
      }
  });
  ```
- **Reason:** If we put `jobId` into the in-memory queue *before* the `@Transactional` method finishes, a worker thread could poll that `jobId` and query the database *before* the database transaction has committed the `INSERT` to disk. The worker would find `null` and throw `JobNotFoundException`.
- Registering `afterCommit()` guarantees that the record is physically committed in the database before any worker thread is allowed to pick it up.

---

### C. Computer Networks & Distributed Systems Concepts

#### 7. Why Server-Sent Events (SSE) instead of WebSockets or Short Polling?
| Parameter | Short Polling | WebSockets | Server-Sent Events (SSE) — Used in TaskForge |
|---|---|---|---|
| **Mechanism** | Client calls `fetch()` every 1s | Full-duplex custom TCP socket | Unidirectional HTTP/1.1 or HTTP/2 stream |
| **Overhead** | Massive (repeated HTTP handshake headers) | High (requires WS protocol upgrade handshake) | Extremely lightweight (single persistent HTTP connection) |
| **Firewall & Proxy** | Works everywhere | Often blocked by corporate proxies | Standard HTTP (`text/event-stream`), passes all firewalls |
| **Reconnection** | Manual | Manual client code required | Native browser auto-reconnect via `EventSource` |
| **TaskForge Fit** | Wasteful | Overkill (telemetry is strictly server-to-client) | **Ideal fit** for real-time monitoring consoles |

#### 8. What is the "Thundering Herd Problem" and how does Jitter solve it?
- If 100 jobs fail simultaneously because an external service experienced a 1-second outage, and all 100 jobs retry using pure exponential backoff ($T = 2^n$), all 100 will wake up at the exact same millisecond ($t = 2000\text{ms}$). They will hammer the recovering service with 100 simultaneous requests, crashing it again.
- In `RetryPolicy.java`, TaskForge applies **Full Randomized Jitter**:
  $$\text{Jitter Offset} = \text{baseDelay} \times \text{jitterFactor} \times (2 \times \text{random}() - 1)$$
  This spreads the retries across a randomized window ($\pm 10\%$), smoothing out traffic spikes.

#### 9. What is Backpressure and what HTTP status code represents it?
- When the execution queue reaches its maximum capacity (e.g. 200 jobs), accepting more tasks would risk memory exhaustion.
- Instead of silently failing or crashing, TaskForge throws `QueueFullException`, which `GlobalExceptionHandler` converts to **HTTP 429 Too Many Requests** with a `Retry-After: 5` header. This explicitly tells clients to slow down.

---

## 3. Examiner Code Navigation Cheat Sheet

| When the Examiner asks... | Open this File | Key Method / Line |
|---|---|---|
| *"Show me where worker threads pick up jobs"* | [`ExecutionEngine.java`](file:///C:/Users/Dell/.gemini/antigravity/scratch/taskforge/backend/src/main/java/com/taskforge/engine/ExecutionEngine.java) | `WorkerThread.run()` & `processJob()` |
| *"Show me how you prevented DB connection starvation"* | [`ExecutionEngine.java`](file:///C:/Users/Dell/.gemini/antigravity/scratch/taskforge/backend/src/main/java/com/taskforge/engine/ExecutionEngine.java) | `claimJob()` vs `handler.execute()` |
| *"Show me the bounded buffer and priority queue"* | [`JobQueue.java`](file:///C:/Users/Dell/.gemini/antigravity/scratch/taskforge/backend/src/main/java/com/taskforge/engine/JobQueue.java) | `offer()` lock-free CAS loop |
| *"Show me the exponential backoff formula"* | [`RetryPolicy.java`](file:///C:/Users/Dell/.gemini/antigravity/scratch/taskforge/backend/src/main/java/com/taskforge/engine/RetryPolicy.java) | `calculateNextRetryTime()` |
| *"Show me state machine transitions"* | [`JobStatus.java`](file:///C:/Users/Dell/.gemini/antigravity/scratch/taskforge/backend/src/main/java/com/taskforge/domain/JobStatus.java) | `canTransitionTo()` |
| *"Show me post-commit race condition prevention"* | [`JobService.java`](file:///C:/Users/Dell/.gemini/antigravity/scratch/taskforge/backend/src/main/java/com/taskforge/service/JobService.java) | `afterCommit()` in `createJob()` |
| *"Show me crash recovery on system startup"* | [`ExecutionEngine.java`](file:///C:/Users/Dell/.gemini/antigravity/scratch/taskforge/backend/src/main/java/com/taskforge/engine/ExecutionEngine.java) | `recoverInterruptedJobs()` |
| *"Show me real-time SSE streaming"* | [`SseEventBroadcaster.java`](file:///C:/Users/Dell/.gemini/antigravity/scratch/taskforge/backend/src/main/java/com/taskforge/event/SseEventBroadcaster.java) | `broadcast()` & `registerClient()` |
| *"Show me client-side event throttling in React"* | [`App.tsx`](file:///C:/Users/Dell/.gemini/antigravity/scratch/taskforge/frontend/src/App.tsx) | `scheduleThrottledRefresh()` |

---

## 4. Live Demonstration Script for Examiners

1. **Step 1: Start the Platform**
   - Terminal 1: `cd backend && mvn spring-boot:run`
   - Terminal 2: `cd frontend && npm run dev`
   - Open browser: `http://localhost:5173`

2. **Step 2: Demonstrate Normal Concurrency**
   - Click **"Batch"** in the top navigation.
   - Submit 50 jobs with 4 worker threads.
   - Point to the **Live Workers** tab: Show all 4 worker threads transitioning between `IDLE` and `BUSY`, with real-time timers and progress bars ticking via SSE.

3. **Step 3: Demonstrate Fault Tolerance (Failure Lab)**
   - Navigate to the **Failure Lab** tab.
   - Click **"Trigger Transient Network Flap (3 Retries)"**.
   - Show the job fail on attempt 1, transition to `RETRY_WAIT`, calculate the backoff delay (e.g. 1.0s, then 2.0s), and finally succeed on attempt 3.
   - Open the **Job Details Modal** to show the examiner the complete chronological attempt history table and audit trail.

4. **Step 4: Demonstrate Backpressure Protection**
   - In Failure Lab, click **"Trigger Queue Backpressure (Capacity Flood)"**.
   - Show the system accept jobs up to capacity (200), and reject excess jobs with clean **HTTP 429 Too Many Requests**.

5. **Step 5: Demonstrate Crash Recovery**
   - Submit 20 slow jobs.
   - Press `Ctrl+C` in the backend terminal to simulate an abrupt server crash.
   - Restart with `mvn spring-boot:run`.
   - Show the console logs: `recoverInterruptedJobs()` automatically identifies the jobs that were in `RUNNING` status when the server died, marks their interrupted attempt, and reschedules them for retry!

---

## 5. Verification Commands

```powershell
# Run all 32 Backend JUnit 5 Tests
cd backend
mvn test

# Run all 10 Frontend Vitest Tests
cd frontend
npm test -- --run

# Compile Production Packages
cd backend && mvn package -DskipTests
cd frontend && npm run build
```
