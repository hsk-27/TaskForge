import React, { useEffect, useState, useCallback } from 'react';
import { Header } from './components/layout/Header';
import { Sidebar, TabType } from './components/layout/Sidebar';
import { DashboardView } from './components/dashboard/DashboardView';
import { JobsExplorerView } from './components/jobs/JobsExplorerView';
import { LiveWorkerView } from './components/workers/LiveWorkerView';
import { FailureLabView } from './components/laboratory/FailureLabView';
import { BenchmarkView } from './components/benchmark/BenchmarkView';
import { CreateJobModal } from './components/jobs/CreateJobModal';
import { BatchSubmitModal } from './components/jobs/BatchSubmitModal';
import { CommandPaletteModal } from './components/common/CommandPaletteModal';
import { EngineMetrics, EngineStatus, Job, JobEvent, PaginatedResponse } from './types';
import { api } from './api/client';
import { audioTelemetry } from './utils/audio';

export const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<TabType>('dashboard');
  const [status, setStatus] = useState<EngineStatus | null>(null);
  const [metrics, setMetrics] = useState<EngineMetrics | null>(null);
  const [events, setEvents] = useState<JobEvent[]>([]);
  const [jobsData, setJobsData] = useState<PaginatedResponse<Job>>({
    content: [],
    totalElements: 0,
    totalPages: 0,
    size: 15,
    number: 0,
  });
  const [sseConnected, setSseConnected] = useState(false);
  const [jobsLoading, setJobsLoading] = useState(false);

  // Filters for Jobs tab
  const [statusFilter, setStatusFilter] = useState<string | undefined>();
  const [typeFilter, setTypeFilter] = useState<string | undefined>();
  const [searchQuery, setSearchQuery] = useState('');
  const [currentPage, setCurrentPage] = useState(0);

  // Modals
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [isBatchOpen, setIsBatchOpen] = useState(false);
  const [isCommandPaletteOpen, setIsCommandPaletteOpen] = useState(false);

  // Fetch telemetry
  const refreshTelemetry = useCallback(async () => {
    try {
      const [statusRes, metricsRes] = await Promise.all([
        api.getEngineStatus(),
        api.getMetrics(),
      ]);
      setStatus(statusRes);
      setMetrics(metricsRes);
    } catch (err) {
      console.error('Failed to refresh telemetry:', err);
    }
  }, []);

  // Fetch jobs list
  const refreshJobs = useCallback(async () => {
    setJobsLoading(true);
    try {
      const res = await api.getJobs({
        status: statusFilter,
        jobType: typeFilter,
        search: searchQuery,
        page: currentPage,
        size: 15,
      });
      setJobsData(res);
    } catch (err) {
      console.error('Failed to load jobs list:', err);
    } finally {
      setJobsLoading(false);
    }
  }, [statusFilter, typeFilter, searchQuery, currentPage]);

  // Initial load
  useEffect(() => {
    refreshTelemetry();
    refreshJobs();
    api.getRecentEvents().then(setEvents).catch(console.error);

    // Periodic telemetry refresh
    const interval = setInterval(refreshTelemetry, 3000);
    return () => clearInterval(interval);
  }, [refreshTelemetry, refreshJobs]);

  // Global Keyboard Shortcuts (Ctrl+K / Cmd+K)
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setIsCommandPaletteOpen((prev) => !prev);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  // Viva Concept: Client-Side Throttling
  // When a burst of 100 jobs runs, 100 SSE events arrive in quick succession.
  // If the browser fired 100 network requests to refetch table data, it would freeze the UI thread.
  // We throttle table refetches to at most once per 350ms, keeping the UI at a responsive 60 FPS.
  const refreshTimerRef = React.useRef<ReturnType<typeof setTimeout> | null>(null);

  const scheduleThrottledRefresh = useCallback(() => {
    if (refreshTimerRef.current) return;
    refreshTimerRef.current = setTimeout(() => {
      refreshTimerRef.current = null;
      refreshTelemetry();
      refreshJobs();
    }, 350);
  }, [refreshTelemetry, refreshJobs]);

  // Subscribe to real-time SSE stream with live progress handler
  useEffect(() => {
    const unsubscribe = api.subscribeToEvents(
      (newEvent) => {
        setSseConnected(true);
        // Prepend new event immediately to buffer (UI events feed stays responsive)
        setEvents((prev) => [newEvent, ...prev.slice(0, 49)]);

        // Audio chime on milestone events
        if (newEvent.eventType === 'JOB_SUCCEEDED') {
          audioTelemetry.jobCompleted();
        } else if (newEvent.eventType === 'JOB_FAILED') {
          audioTelemetry.jobFailed();
        }

        // Schedule throttled HTTP refetch
        scheduleThrottledRefresh();
      },
      (err) => {
        setSseConnected(false);
      },
      () => {
        setSseConnected(true);
      },
      // Real-time progress updates handler
      (progress) => {
        setStatus((prev) => {
          if (!prev || !prev.workers) return prev;
          const updatedWorkers = prev.workers.map((w) => {
            if (w.workerId === progress.workerId || w.currentJobId === progress.jobId) {
              return {
                ...w,
                progressPercent: progress.progressPercent,
                currentStage: progress.stage,
              };
            }
            return w;
          });
          return { ...prev, workers: updatedWorkers };
        });
      }
    );

    return () => {
      unsubscribe();
      if (refreshTimerRef.current) {
        clearTimeout(refreshTimerRef.current);
        refreshTimerRef.current = null;
      }
    };
  }, [scheduleThrottledRefresh]);

  // Handlers
  const handleTogglePause = async () => {
    try {
      if (status?.isPaused) {
        const res = await api.resumeEngine();
        setStatus(res);
      } else {
        const res = await api.pauseEngine();
        setStatus(res);
      }
    } catch (err: any) {
      alert(`Error toggling engine: ${err.message}`);
    }
  };

  const handleToggleEcoMode = async () => {
    try {
      const current = status?.ecoMode ?? true;
      const res = await api.toggleEcoMode(!current);
      setStatus(res);
    } catch (err: any) {
      alert(`Error toggling Eco-Mode: ${err.message}`);
    }
  };

  const handleCreateJob = async (jobData: any) => {
    await api.createJob(jobData);
    audioTelemetry.burstSubmitted();
    refreshJobs();
    refreshTelemetry();
  };

  const handleBatchSubmit = async (batchData: any) => {
    await api.createBatch(batchData);
    audioTelemetry.burstSubmitted();
    refreshJobs();
    refreshTelemetry();
  };

  const handleCancelJob = async (id: string) => {
    await api.cancelJob(id);
    refreshJobs();
    refreshTelemetry();
  };

  const handleRetryJob = async (id: string) => {
    await api.retryJob(id);
    refreshJobs();
    refreshTelemetry();
  };

  const handleClearData = async () => {
    await api.clearData();
    refreshJobs();
    refreshTelemetry();
    setEvents([]);
  };

  const handleScaleWorkers = async (newCount: number) => {
    const res = await api.updateEngineSettings({ workerCount: newCount });
    setStatus(res);
  };

  const handleUpdateSettings = async (settings: any) => {
    const res = await api.updateEngineSettings(settings);
    setStatus(res);
  };

  const handleReplayDlq = async () => {
    try {
      const res = await api.replayDlq();
      audioTelemetry.burstSubmitted();
      alert(`DLQ REPLAY: ${res.replayedCount} jobs re-entered execution queue`);
      refreshJobs();
      refreshTelemetry();
    } catch (err: any) {
      alert(`Error replaying DLQ: ${err.message}`);
    }
  };

  const handleExportDiagnostics = async () => {
    try {
      const dump = await api.exportDiagnostics();
      const blob = new Blob([JSON.stringify(dump, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `taskforge-diagnostics-${new Date().toISOString().replace(/[:.]/g, '-')}.json`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (err: any) {
      alert(`Export error: ${err.message}`);
    }
  };

  const handleFilterChange = (newStatus?: string, newType?: string, search?: string, page: number = 0) => {
    setStatusFilter(newStatus);
    setTypeFilter(newType);
    setSearchQuery(search || '');
    setCurrentPage(page);
  };

  return (
    <div className="min-h-screen flex flex-col bg-[#0b0f17] text-slate-100 font-sans">
      <Header
        status={status}
        sseConnected={sseConnected}
        onTogglePause={handleTogglePause}
        onToggleEcoMode={handleToggleEcoMode}
        onClearData={handleClearData}
        onOpenCommandPalette={() => setIsCommandPaletteOpen(true)}
      />

      <div className="flex flex-1 overflow-hidden">
        <Sidebar activeTab={activeTab} onSelectTab={setActiveTab} />

        <main className="flex-1 overflow-y-auto p-5 bg-[#0b0f17]">
          <div className="max-w-7xl mx-auto">
            {activeTab === 'dashboard' && (
              <DashboardView
                metrics={metrics}
                status={status}
                events={events}
                onRefresh={() => {
                  refreshTelemetry();
                  refreshJobs();
                }}
                onOpenCreateModal={() => setIsCreateOpen(true)}
                onOpenBatchModal={() => setIsBatchOpen(true)}
                onBatchSubmit={handleBatchSubmit}
                onClearData={handleClearData}
              />
            )}

            {activeTab === 'jobs' && (
              <JobsExplorerView
                jobsData={jobsData}
                loading={jobsLoading}
                statusFilter={statusFilter}
                typeFilter={typeFilter}
                searchQuery={searchQuery}
                currentPage={currentPage}
                onFilterChange={handleFilterChange}
                onCreateJob={handleCreateJob}
                onBatchSubmit={handleBatchSubmit}
                onCancelJob={handleCancelJob}
                onRetryJob={handleRetryJob}
              />
            )}

            {activeTab === 'workers' && (
              <LiveWorkerView
                status={status}
                onScaleWorkers={handleScaleWorkers}
              />
            )}

            {activeTab === 'laboratory' && (
              <FailureLabView
                status={status}
                onLaunchScenario={handleBatchSubmit}
                onUpdateSettings={handleUpdateSettings}
                onTogglePause={handleTogglePause}
                onClearData={handleClearData}
              />
            )}

            {activeTab === 'benchmark' && <BenchmarkView />}
          </div>
        </main>
      </div>

      <CreateJobModal
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        onSubmit={handleCreateJob}
      />
      <BatchSubmitModal
        isOpen={isBatchOpen}
        onClose={() => setIsBatchOpen(false)}
        onSubmit={handleBatchSubmit}
      />
      <CommandPaletteModal
        isOpen={isCommandPaletteOpen}
        onClose={() => setIsCommandPaletteOpen(false)}
        onDispatchBatch={(count, isSequential) => {
          handleBatchSubmit({
            count,
            prefix: isSequential ? 'etl-pipeline' : 'batch-run',
            jobType: undefined,
            failureRate: 0.1,
            simulatedDurationMs: 80,
            maxAttempts: 3,
            isSequential,
          });
        }}
        onTogglePause={handleTogglePause}
        onToggleEco={handleToggleEcoMode}
        onScaleWorkers={handleScaleWorkers}
        onClearData={handleClearData}
        onReplayDlq={handleReplayDlq}
        onExport={handleExportDiagnostics}
        onNavigateTab={(tab) => setActiveTab(tab)}
        onFilterChange={(st) => handleFilterChange(st)}
        isPaused={status?.isPaused ?? false}
        isEco={status?.ecoMode ?? true}
      />
    </div>
  );
};
