# TaskForge Known Limitations & Architectural Boundaries

## 1. Scope & Design Intent

TaskForge is engineered as a robust, single-process concurrent execution engine designed to illustrate distributed systems patterns and concurrency mechanics with clarity and pedagogical value.

It deliberately avoids introducing distributed consensus, multi-node clustering, or heavyweight message brokers (e.g., Kafka, Redis, RabbitMQ, ZooKeeper) where a clean in-process architecture provides superior transparency and debuggability.

Below are the explicit guarantees, non-guarantees, and known operational limitations.

---

## 2. Execution Guarantees

| Property | Guarantee Level | Explanation |
|---|---|---|
| **Delivery Semantics** | **At-least-once** | If a worker crashes or is interrupted mid-execution, the engine reconciles the job upon startup and reschedules it for execution. It does **not** guarantee exactly-once execution. |
| **Idempotency** | **Caller responsibility** | Because failed or recovered jobs may be executed multiple times across attempts, all job handlers and external side-effects must be idempotent. |
| **Ordering** | **Best-effort FIFO per priority** | Jobs enqueued within the in-memory bounded queue are dispatched in FIFO order. However, because workers execute concurrently across multiple threads with variable execution durations, completion order is non-deterministic. |
| **Mutual Exclusion** | **Guaranteed per node** | Two worker threads within the engine cannot claim or execute the same job simultaneously due to atomic queue dispatch and transactional state verification. |
| **State Persistence** | **Guaranteed (PostgreSQL/H2)** | Job records, attempt history, and event journals are synchronously committed to persistent storage. In-memory counters are never the source of truth. |

---

## 3. Known Limitations

### 1. Single-Node Architecture
- **Limitation:** The execution queue (`JobQueue`) and worker pool (`workerThreads[]`) reside entirely within a single JVM process.
- **Trade-off:** This provides microsecond queue operations and eliminates network hops, but does not support horizontal scaling across a cluster of servers without external distributed locking.

### 2. Startup Enqueueing of Legacy `QUEUED` Records
- **Limitation:** When the application starts, it immediately reconciles `RUNNING` jobs into `RETRY_WAIT`. However, if 10,000 jobs were previously submitted into the database in `QUEUED` state, loading all 10,000 into the bounded in-memory queue (capacity = 200) would exceed memory limits.
- **Current Behavior:** A scheduled queue loader periodically tops off the in-memory queue from the database table (`SELECT ... WHERE status = 'QUEUED' ORDER BY priority DESC, created_at ASC LIMIT ...`).
- **Consideration:** If there is a massive backlog, jobs will be pulled incrementally in batches.

### 3. Polling Latency for Retries
- **Limitation:** When a job enters `RETRY_WAIT`, it is persisted with a future timestamp (`next_retry_at`). The engine relies on a background scheduler (`@Scheduled(fixedDelay = 2000)`) to poll for eligible jobs.
- **Impact:** Retries may experience up to a 2,000 ms scheduling delay beyond their exact calculated backoff timestamp.

### 4. Shared CPU Resources
- **Limitation:** CPU-bound handlers (e.g., `ComputeJobHandler` calculating prime factors) run in the same JVM as the Spring Boot embedded Tomcat web server.
- **Impact:** If the worker pool is configured with high thread concurrency ($N \ge 16$) on a low-core machine, CPU starvation may increase HTTP API latency. Production architectures typically isolate worker pools into separate containers or worker nodes.

### 5. Server-Sent Events (SSE) Reconnection
- **Limitation:** The browser SSE connection delivers real-time updates while connected. If a client disconnects and reconnects later, SSE does not automatically replay missed historical events.
- **Mitigation:** The frontend automatically performs a fresh REST fetch (`GET /api/jobs` and `GET /api/metrics`) upon reconnection to synchronize its full state with the persistent database.

---

## 4. When to Upgrade to Distributed Infrastructure

TaskForge is ideal for:
- Developer toolkits, batch processors, and internal workflows.
- Single-instance background work with durable tracking and recovery.
- Learning core systems engineering, concurrency, and reliability patterns.

When to migrate to distributed architectures:
- **Throughput > 50,000 jobs/sec:** Requires distributed message brokers such as Apache Kafka or RabbitMQ.
- **Multi-region active-active clustering:** Requires distributed consensus (Raft, Paxos) or distributed task frameworks (Temporal, Cadence).
- **Hard real-time guarantees (< 5ms):** Requires low-latency streaming rather than transactional database persistence.
