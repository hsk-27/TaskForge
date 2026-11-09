import React, { useState } from 'react';
import {
  AlertOctagon,
  Ban,
  ChevronLeft,
  ChevronRight,
  Download,
  Eye,
  Plus,
  RotateCcw,
  Search,
  Trash2,
  Zap,
} from 'lucide-react';
import { Job, JobStatus, JobType, PaginatedResponse } from '../../types';
import { api } from '../../api/client';
import { StatusBadge } from '../common/StatusBadge';
import { JobDetailsModal } from './JobDetailsModal';
import { CreateJobModal } from './CreateJobModal';
import { BatchSubmitModal } from './BatchSubmitModal';

interface JobsExplorerViewProps {
  jobsData: PaginatedResponse<Job> | null;
  loading: boolean;
  statusFilter?: string;
  typeFilter?: string;
  searchQuery: string;
  currentPage: number;
  onFilterChange: (status?: string, type?: string, search?: string, page?: number) => void;
  onCreateJob: (job: any) => Promise<void>;
  onBatchSubmit: (batch: any) => Promise<void>;
  onCancelJob: (id: string) => Promise<void>;
  onRetryJob: (id: string) => Promise<void>;
}

export const JobsExplorerView: React.FC<JobsExplorerViewProps> = ({
  jobsData,
  loading,
  statusFilter,
  typeFilter,
  searchQuery,
  currentPage,
  onFilterChange,
  onCreateJob,
  onBatchSubmit,
  onCancelJob,
  onRetryJob,
}) => {
  const [selectedJob, setSelectedJob] = useState<Job | null>(null);
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [isBatchOpen, setIsBatchOpen] = useState(false);
  const [searchVal, setSearchVal] = useState(searchQuery);
  const [replayingDlq, setReplayingDlq] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [cancellingPending, setCancellingPending] = useState(false);
  const [purgingTerminal, setPurgingTerminal] = useState(false);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onFilterChange(statusFilter, typeFilter, searchVal, 0);
  };

  const handleCancelAllPending = async () => {
    if (!window.confirm('Cancel all pending queued and scheduled tasks?')) return;
    setCancellingPending(true);
    try {
      const res = await api.cancelAllPending();
      alert(`Bulk Action: ${res.message}`);
      onFilterChange(statusFilter, typeFilter, searchVal, currentPage);
    } catch (err: any) {
      alert(`Error cancelling pending jobs: ${err.message}`);
    } finally {
      setCancellingPending(false);
    }
  };

  const handlePurgeTerminal = async () => {
    if (!window.confirm('Purge all completed (succeeded, failed, cancelled) job records from database?')) return;
    setPurgingTerminal(true);
    try {
      const res = await api.purgeTerminalJobs();
      alert(`Bulk Action: ${res.message}`);
      onFilterChange(statusFilter, typeFilter, searchVal, 0);
    } catch (err: any) {
      alert(`Error purging terminal jobs: ${err.message}`);
    } finally {
      setPurgingTerminal(false);
    }
  };

  const handleReplayDlq = async () => {
    setReplayingDlq(true);
    try {
      const res = await api.replayDlq();
      alert(`Dead Letter Queue: ${res.message}`);
      onFilterChange(statusFilter, typeFilter, searchVal, currentPage);
    } catch (err: any) {
      alert(`Error replaying DLQ: ${err.message}`);
    } finally {
      setReplayingDlq(false);
    }
  };

  const handleExportDiagnostics = async () => {
    setExporting(true);
    try {
      const data = await api.exportDiagnostics();
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `taskforge-audit-${new Date().toISOString().replace(/[:.]/g, '-')}.json`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch (err: any) {
      alert(`Export error: ${err.message}`);
    } finally {
      setExporting(false);
    }
  };

  return (
    <div className="space-y-3 font-sans select-none">
      {/* Search and Filters Header */}
      <div className="p-3.5 bg-[#121316] border border-white/[0.08] rounded-lg shadow-sm flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
        <form onSubmit={handleSearchSubmit} className="flex items-center gap-2 flex-1 max-w-md">
          <div className="relative flex-1">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-zinc-500" />
            <input
              type="text"
              value={searchVal}
              onChange={(e) => setSearchVal(e.target.value)}
              placeholder="Search by ID, workflow name, or task..."
              className="w-full pl-9 pr-3 py-1.5 bg-[#08090a] border border-white/[0.08] rounded-md text-xs text-zinc-100 placeholder-zinc-500 focus:outline-none focus:border-indigo-500 transition-colors font-mono"
            />
          </div>
          <button
            type="submit"
            className="px-3 py-1.5 bg-zinc-900 hover:bg-zinc-800 text-zinc-200 border border-white/[0.08] text-xs font-medium rounded-md transition-colors"
          >
            Filter
          </button>
        </form>

        <div className="flex items-center gap-2 flex-wrap">
          {/* Status Filter */}
          <select
            value={statusFilter || ''}
            onChange={(e) => onFilterChange(e.target.value || undefined, typeFilter, searchVal, 0)}
            className="px-2.5 py-1.5 bg-[#08090a] border border-white/[0.08] rounded-md text-xs text-zinc-200 focus:outline-none focus:border-indigo-500 transition-colors"
          >
            <option value="">All Statuses</option>
            <option value="QUEUED">Queued</option>
            <option value="RUNNING">Running</option>
            <option value="RETRY_WAIT">Retrying</option>
            <option value="SUCCEEDED">Succeeded</option>
            <option value="FAILED">Failed</option>
            <option value="CANCELLED">Cancelled</option>
          </select>

          {/* Type Filter */}
          <select
            value={typeFilter || ''}
            onChange={(e) => onFilterChange(statusFilter, e.target.value || undefined, searchVal, 0)}
            className="px-2.5 py-1.5 bg-[#08090a] border border-white/[0.08] rounded-md text-xs text-zinc-200 focus:outline-none focus:border-indigo-500 transition-colors"
          >
            <option value="">All Workloads</option>
            <option value="COMPUTE">Compute</option>
            <option value="DATA_PROCESSING">Data Processing</option>
            <option value="REPORT_GENERATION">Report Generation</option>
            <option value="SIMULATED_IO">Simulated I/O</option>
            <option value="FAILURE_DEMO">Failure Demo</option>
          </select>

          {/* DLQ Filter Button */}
          <button
            onClick={() => onFilterChange(statusFilter === 'FAILED' ? undefined : 'FAILED', typeFilter, searchVal, 0)}
            className={`flex items-center gap-1.5 px-2.5 py-1.5 text-xs font-medium rounded-md transition-colors border ${
              statusFilter === 'FAILED'
                ? 'bg-rose-500/20 text-rose-300 border-rose-500/50'
                : 'bg-[#08090a] text-zinc-400 border-white/[0.08] hover:text-zinc-200'
            }`}
            title="Filter to permanently failed Dead Letter Queue (DLQ) jobs"
          >
            <AlertOctagon className="w-3.5 h-3.5 text-rose-400" />
            <span>Dead Letter Queue</span>
          </button>

          {/* Replay All DLQ Button */}
          {statusFilter === 'FAILED' && (
            <button
              onClick={handleReplayDlq}
              disabled={replayingDlq || !jobsData || jobsData.totalElements === 0}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-amber-500 hover:bg-amber-400 disabled:opacity-40 text-zinc-950 font-semibold text-xs rounded-md transition-all shadow-sm"
              title="Replay all DLQ jobs back into queue"
            >
              <RotateCcw className={`w-3.5 h-3.5 ${replayingDlq ? 'animate-spin' : ''}`} />
              <span>Replay All</span>
            </button>
          )}

          {/* Cancel Pending & Purge Finished Actions */}
          <button
            onClick={handleCancelAllPending}
            disabled={cancellingPending}
            className="flex items-center gap-1.5 px-3 py-1.5 bg-zinc-900 hover:bg-zinc-800 text-zinc-300 border border-white/[0.08] text-xs font-medium rounded-md shadow-sm transition-colors"
            title="Cancel all queued and delayed tasks currently awaiting execution"
          >
            <Ban className="w-3.5 h-3.5 text-amber-400" />
            <span>{cancellingPending ? 'Cancelling...' : 'Cancel Pending'}</span>
          </button>

          <button
            onClick={handlePurgeTerminal}
            disabled={purgingTerminal}
            className="flex items-center gap-1.5 px-3 py-1.5 bg-zinc-900 hover:bg-zinc-800 text-zinc-300 border border-white/[0.08] text-xs font-medium rounded-md shadow-sm transition-colors"
            title="Permanently remove all succeeded, failed, and cancelled jobs from the database"
          >
            <Trash2 className="w-3.5 h-3.5 text-rose-400" />
            <span>{purgingTerminal ? 'Purging...' : 'Purge Terminal'}</span>
          </button>

          {/* Export Diagnostics Button */}
          <button
            onClick={handleExportDiagnostics}
            disabled={exporting}
            className="flex items-center gap-1.5 px-3 py-1.5 bg-zinc-900 hover:bg-zinc-800 text-zinc-200 border border-white/[0.08] text-xs font-medium rounded-md shadow-sm transition-colors"
            title="Export complete system state, metrics, attempts, and event records as JSON"
          >
            <Download className="w-3.5 h-3.5 text-zinc-400" />
            <span>Export</span>
          </button>

          <button
            onClick={() => setIsCreateOpen(true)}
            className="flex items-center gap-1.5 px-3 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-medium rounded-md shadow-sm transition-colors"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Ingest Job</span>
          </button>

          <button
            onClick={() => setIsBatchOpen(true)}
            className="flex items-center gap-1.5 px-3 py-1.5 bg-zinc-900 hover:bg-zinc-800 text-zinc-300 border border-white/[0.08] text-xs font-medium rounded-md shadow-sm transition-colors"
          >
            <Zap className="w-3.5 h-3.5 text-indigo-400" />
            <span>Batch</span>
          </button>
        </div>
      </div>

      {/* Jobs Table */}
      <div className="border border-white/[0.08] bg-[#121316] rounded-lg shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-[#16171b] text-zinc-400 border-b border-white/[0.08] select-none text-[11px] font-medium tracking-wider">
              <tr>
                <th className="p-3">Job ID</th>
                <th className="p-3">Task Name</th>
                <th className="p-3">Type</th>
                <th className="p-3">Status</th>
                <th className="p-3">Attempts</th>
                <th className="p-3">Worker Node</th>
                <th className="p-3">Wait / Exec Duration</th>
                <th className="p-3">Submitted At</th>
                <th className="p-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/[0.05] text-zinc-300">
              {loading && !jobsData ? (
                <tr>
                  <td colSpan={9} className="p-8 text-center text-zinc-500 text-xs">
                    Ingesting records from cluster database...
                  </td>
                </tr>
              ) : !jobsData || jobsData.content.length === 0 ? (
                <tr>
                  <td colSpan={9} className="p-10 text-center text-zinc-500 text-xs">
                    No tasks found matching the active cluster filters.
                  </td>
                </tr>
              ) : (
                jobsData.content.map((job) => (
                  <tr key={job.id} className="hover:bg-white/[0.02] transition-colors">
                    <td className="p-3 font-mono text-[11px] text-zinc-400">
                      {job.id.substring(0, 8)}
                    </td>
                    <td className="p-3 font-medium text-zinc-100 max-w-xs truncate" title={job.name}>
                      <div className="flex items-center gap-1.5 truncate">
                        {job.payload && job.payload.includes('dependsOnJobId') && (
                          <span className="text-[10px] px-1.5 py-0.5 bg-sky-500/10 text-sky-400 border border-sky-500/20 rounded font-mono font-medium flex-shrink-0">
                            DAG
                          </span>
                        )}
                        <span className="truncate">{job.name}</span>
                      </div>
                    </td>
                    <td className="p-3 text-zinc-300 font-medium">{job.jobType}</td>
                    <td className="p-3">
                      <StatusBadge status={job.status} size="sm" />
                    </td>
                    <td className="p-3 font-mono text-zinc-400">
                      {job.attemptCount}/{job.maxAttempts}
                    </td>
                    <td className="p-3 font-mono text-zinc-400 text-[11px]">{job.workerId || '—'}</td>
                    <td className="p-3 font-mono text-[11px]">
                      <span className="text-amber-400">{job.queueWaitDurationMs ?? 0}ms</span>
                      <span className="text-zinc-600"> / </span>
                      <span className="text-emerald-400">{job.executionDurationMs ?? 0}ms</span>
                    </td>
                    <td className="p-3 font-mono text-[11px] text-zinc-400">
                      {new Date(job.createdAt).toLocaleTimeString()}
                    </td>
                    <td className="p-3 text-right">
                      <div className="flex items-center justify-end gap-1.5">
                        <button
                          onClick={() => setSelectedJob(job)}
                          className="px-2.5 py-1 bg-zinc-900 hover:bg-zinc-800 text-zinc-200 border border-white/[0.08] rounded text-[11px] font-medium transition-colors"
                          title="Inspect job audit trail and events"
                        >
                          <Eye className="w-3.5 h-3.5 inline mr-1 text-zinc-400" />
                          Inspect
                        </button>

                        {(job.status === 'QUEUED' || job.status === 'RUNNING' || job.status === 'RETRY_WAIT') && (
                          <button
                            onClick={() => onCancelJob(job.id)}
                            className="px-2.5 py-1 bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 border border-rose-500/30 rounded text-[11px] font-medium transition-colors"
                            title={job.status === 'RUNNING' ? 'Interrupt running worker thread immediately' : 'Cancel queued job'}
                          >
                            {job.status === 'RUNNING' ? 'Interrupt' : 'Cancel'}
                          </button>
                        )}

                        {(job.status === 'FAILED' || job.status === 'CANCELLED') && (
                          <button
                            onClick={() => onRetryJob(job.id)}
                            className="px-2.5 py-1 bg-amber-500/10 hover:bg-amber-500/20 text-amber-400 border border-amber-500/30 rounded text-[11px] font-medium transition-colors"
                            title="Manually retry job"
                          >
                            Retry
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination Bar */}
        {jobsData && jobsData.totalPages > 1 && (
          <div className="p-3 bg-[#16171b] border-t border-white/[0.08] flex items-center justify-between text-xs text-zinc-400 select-none">
            <span className="font-mono text-[11px]">
              Showing Page {jobsData.number + 1} of {jobsData.totalPages} ({jobsData.totalElements} total tasks)
            </span>
            <div className="flex items-center gap-1.5">
              <button
                disabled={jobsData.number === 0}
                onClick={() => onFilterChange(statusFilter, typeFilter, searchVal, jobsData.number - 1)}
                className="p-1 rounded bg-zinc-900 hover:bg-zinc-800 border border-white/[0.08] disabled:opacity-40 transition-colors"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <button
                disabled={jobsData.number >= jobsData.totalPages - 1}
                onClick={() => onFilterChange(statusFilter, typeFilter, searchVal, jobsData.number + 1)}
                className="p-1 rounded bg-zinc-900 hover:bg-zinc-800 border border-white/[0.08] disabled:opacity-40 transition-colors"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Modals */}
      {selectedJob && (
        <JobDetailsModal
          job={selectedJob}
          onClose={() => setSelectedJob(null)}
          onCancel={async (id) => {
            await onCancelJob(id);
            setSelectedJob(null);
          }}
          onRetry={async (id) => {
            await onRetryJob(id);
            setSelectedJob(null);
          }}
        />
      )}

      {isCreateOpen && (
        <CreateJobModal
          isOpen={isCreateOpen}
          onClose={() => setIsCreateOpen(false)}
          onSubmit={async (job) => {
            await onCreateJob(job);
            setIsCreateOpen(false);
          }}
        />
      )}

      {isBatchOpen && (
        <BatchSubmitModal
          isOpen={isBatchOpen}
          onClose={() => setIsBatchOpen(false)}
          onSubmit={async (batch) => {
            await onBatchSubmit(batch);
            setIsBatchOpen(false);
          }}
        />
      )}
    </div>
  );
};
