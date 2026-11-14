/**
 * Frontend REST Client for TaskForge Enterprise Engine
 *
 * Communicates with Spring Boot backend at `/api/v1` using native browser `fetch()` and `async/await`.
 * Handles standard HTTP error status codes (400 Bad Request, 404 Not Found, 409 Conflict, 429 Backpressure)
 * by extracting the backend's GlobalExceptionHandler JSON message and throwing a descriptive Error.
 */
import {
  AssistantPersona,
  ChatResponse,
  EngineMetrics,
  EngineStatus,
  Job,
  JobAttempt,
  JobDagInfo,
  JobEvent,
  PaginatedResponse,
} from '../types';

const API_BASE = '/api/v1';

async function request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE}${endpoint}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
  });

  if (!response.ok) {
    let errorMsg = `HTTP Error ${response.status}: ${response.statusText}`;
    try {
      const errorData = await response.json();
      if (errorData.message) {
        errorMsg = errorData.message;
      }
    } catch {
      // Use fallback error message
    }
    throw new Error(errorMsg);
  }

  if (response.status === 204) {
    return {} as T;
  }

  return response.json();
}

export const api = {
  // Jobs
  async getJobs(params: {
    status?: string;
    jobType?: string;
    search?: string;
    page?: number;
    size?: number;
  }): Promise<PaginatedResponse<Job>> {
    const query = new URLSearchParams();
    if (params.status) query.append('status', params.status);
    if (params.jobType) query.append('jobType', params.jobType);
    if (params.search) query.append('search', params.search);
    query.append('page', String(params.page ?? 0));
    query.append('size', String(params.size ?? 15));
    query.append('sortBy', 'createdAt');
    query.append('direction', 'DESC');
    return request<PaginatedResponse<Job>>(`/jobs?${query.toString()}`);
  },

  async getJob(id: string): Promise<Job> {
    return request<Job>(`/jobs/${id}`);
  },

  async getJobDag(id: string): Promise<JobDagInfo> {
    return request<JobDagInfo>(`/jobs/${id}/dag`);
  },

  async getAttempts(jobId: string): Promise<JobAttempt[]> {
    return request<JobAttempt[]>(`/jobs/${jobId}/attempts`);
  },

  async getEvents(jobId: string): Promise<JobEvent[]> {
    return request<JobEvent[]>(`/jobs/${jobId}/events`);
  },

  async getRecentEvents(): Promise<JobEvent[]> {
    return request<JobEvent[]>('/events/recent');
  },

  async createJob(job: {
    name: string;
    jobType: string;
    priority?: number;
    payload?: string;
    maxAttempts?: number;
    delaySeconds?: number;
    idempotencyKey?: string;
  }): Promise<Job> {
    return request<Job>('/jobs', {
      method: 'POST',
      body: JSON.stringify(job),
    });
  },

  async createBatch(batch: {
    count: number;
    prefix?: string;
    jobType?: string;
    priority?: number;
    failureRate?: number;
    durationMs?: number;
    simulatedDurationMs?: number;
    maxAttempts?: number;
    isSequential?: boolean;
  }): Promise<{ submittedCount: number; rejectedCount: number; jobIds: string[]; message: string }> {
    const payload = {
      ...batch,
      simulatedDurationMs: batch.simulatedDurationMs ?? batch.durationMs,
    };
    return request('/jobs/batch', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  async cancelJob(id: string): Promise<Job> {
    return request<Job>(`/jobs/${id}/cancel`, { method: 'POST' });
  },

  async retryJob(id: string): Promise<Job> {
    return request<Job>(`/jobs/${id}/retry`, { method: 'POST' });
  },

  async cancelAllPending(): Promise<{ cancelledCount: number; message: string }> {
    return request<{ cancelledCount: number; message: string }>('/jobs/bulk/cancel', { method: 'POST' });
  },

  async purgeTerminalJobs(): Promise<{ purgedCount: number; message: string }> {
    return request<{ purgedCount: number; message: string }>('/jobs/bulk/terminal', { method: 'DELETE' });
  },

  async clearData(): Promise<void> {
    return request<void>('/jobs/clear', { method: 'DELETE' });
  },

  // Engine
  async getEngineStatus(): Promise<EngineStatus> {
    return request<EngineStatus>('/engine/status');
  },

  async updateEngineSettings(settings: {
    workerCount?: number;
    queueCapacity?: number;
    maxAttempts?: number;
    baseBackoffMs?: number;
  }): Promise<EngineStatus> {
    return request<EngineStatus>('/engine/settings', {
      method: 'POST',
      body: JSON.stringify(settings),
    });
  },

  async pauseEngine(): Promise<EngineStatus> {
    return request<EngineStatus>('/engine/pause', { method: 'POST' });
  },

  async resumeEngine(): Promise<EngineStatus> {
    return request<EngineStatus>('/engine/resume', { method: 'POST' });
  },

  async toggleEcoMode(enabled: boolean): Promise<EngineStatus> {
    return request<EngineStatus>(`/engine/eco?enabled=${enabled}`, { method: 'POST' });
  },

  async getDlq(page = 0, size = 15): Promise<PaginatedResponse<Job>> {
    return request<PaginatedResponse<Job>>(`/jobs/dlq?page=${page}&size=${size}`);
  },

  async replayDlq(): Promise<{ replayedCount: number; message: string }> {
    return request<{ replayedCount: number; message: string }>('/jobs/dlq/replay', { method: 'POST' });
  },

  async exportDiagnostics(): Promise<any> {
    return request<any>('/jobs/export');
  },

  // Metrics
  async getMetrics(): Promise<EngineMetrics> {
    return request<EngineMetrics>('/metrics');
  },

  // SSE Stream
  subscribeToEvents(
    onEvent: (event: any) => void,
    onError?: (err: any) => void,
    onConnect?: () => void,
    onProgress?: (progress: any) => void
  ): () => void {
    const eventSource = new EventSource(`${API_BASE}/events/stream`);

    eventSource.onopen = () => {
      if (onConnect) onConnect();
    };

    eventSource.addEventListener('CONNECTED', (e) => {
      console.log('SSE Stream Connected:', e.data);
      if (onConnect) onConnect();
    });

    eventSource.addEventListener('HEARTBEAT', () => {
      if (onConnect) onConnect();
    });

    eventSource.addEventListener('JOB_EVENT', (e) => {
      try {
        const parsed = JSON.parse(e.data);
        onEvent(parsed);
      } catch (err) {
        console.error('Failed to parse SSE payload:', err);
      }
    });

    eventSource.addEventListener('JOB_PROGRESS', (e) => {
      try {
        const parsed = JSON.parse(e.data);
        if (onProgress) onProgress(parsed);
      } catch (err) {
        console.error('Failed to parse progress SSE payload:', err);
      }
    });

    eventSource.onerror = (err) => {
      if (onError) onError(err);
    };

    return () => {
      eventSource.close();
    };
  },

  // Intelligent Assistant API
  async sendChatMessage(
    message: string,
    persona: AssistantPersona = 'DEVELOPER',
    actionId?: string
  ): Promise<ChatResponse> {
    return request<ChatResponse>('/assistant/chat', {
      method: 'POST',
      body: JSON.stringify({ message, persona, actionId }),
    });
  },

  async getChatSuggestions(persona: AssistantPersona = 'DEVELOPER'): Promise<string[]> {
    return request<string[]>(`/assistant/suggestions?persona=${persona}`);
  },
};
