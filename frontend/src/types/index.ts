export type JobStatus = 'QUEUED' | 'RUNNING' | 'RETRY_WAIT' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED';

export type JobType = 'COMPUTE' | 'DATA_PROCESSING' | 'REPORT_GENERATION' | 'SIMULATED_IO' | 'FAILURE_DEMO';

export interface Job {
  id: string;
  name: string;
  jobType: JobType;
  status: JobStatus;
  priority: number;
  payload?: string;
  createdAt: string;
  scheduledExecutionAt?: string;
  startedAt?: string;
  completedAt?: string;
  attemptCount: number;
  maxAttempts: number;
  lastErrorMessage?: string;
  workerId?: string;
  executionDurationMs?: number;
  queueWaitDurationMs?: number;
  version: number;
}

export interface JobAttempt {
  id: string;
  jobId: string;
  attemptNumber: number;
  workerId: string;
  startedAt: string;
  completedAt?: string;
  outcome: string;
  errorMessage?: string;
  durationMs?: number;
}

export interface JobEvent {
  id: number;
  jobId: string;
  eventType: string;
  message: string;
  workerId?: string;
  createdAt: string;
}

export interface WorkerInfo {
  workerId: string;
  state: 'IDLE' | 'BUSY';
  currentJobId?: string;
  currentJobName?: string;
  attemptNumber?: number;
  elapsedMs?: number;
  progressPercent?: number;
  currentStage?: string;
  totalProcessed?: number;
  totalFailed?: number;
  avgDurationMs?: number;
}

export interface JobProgressPayload {
  jobId: string;
  workerId: string;
  progressPercent: number;
  stage: string;
  timestamp: number;
}

export interface EngineStatus {
  isRunning: boolean;
  isPaused: boolean;
  ecoMode: boolean;
  workerCount: number;
  activeWorkers: number;
  queueSize: number;
  queueCapacity: number;
  jvmUsedMemoryMb: number;
  jvmMaxMemoryMb: number;
  availableProcessors: number;
  workers: WorkerInfo[];
}

export interface TopFailureReason {
  reason: string;
  count: number;
}

export interface EngineMetrics {
  totalJobs: number;
  queuedJobs: number;
  runningJobs: number;
  retryWaitJobs: number;
  succeededJobs: number;
  failedJobs: number;
  cancelledJobs: number;
  successRatePercent: number;
  failureRatePercent: number;
  averageExecutionDurationMs: number;
  averageQueueWaitDurationMs: number;
  throughputPerMinute: number;
  topFailureReasons: TopFailureReason[];
}

export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
