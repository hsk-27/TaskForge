import React, { useEffect, useState } from 'react';
import { AlertTriangle, ArrowRight, Clock, Download, GitFork, History, RefreshCw, ShieldCheck, X } from 'lucide-react';
import { Job, JobAttempt, JobDagInfo, JobEvent } from '../../types';
import { api } from '../../api/client';
import { StatusBadge } from '../common/StatusBadge';

interface JobDetailsModalProps {
  job: Job | null;
  onClose: () => void;
  onRetry: (id: string) => void;
  onCancel: (id: string) => void;
}

export const JobDetailsModal: React.FC<JobDetailsModalProps> = ({
  job,
  onClose,
  onRetry,
  onCancel,
}) => {
  const [attempts, setAttempts] = useState<JobAttempt[]>([]);
  const [events, setEvents] = useState<JobEvent[]>([]);
  const [dagInfo, setDagInfo] = useState<JobDagInfo | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (job) {
      setLoading(true);
      Promise.all([
        api.getAttempts(job.id),
        api.getEvents(job.id),
        api.getJobDag(job.id).catch(() => null),
      ])
        .then(([attemptsData, eventsData, dagData]) => {
          setAttempts(attemptsData);
          setEvents(eventsData);
          setDagInfo(dagData);
        })
        .catch((err) => console.error('Failed to load job details:', err))
        .finally(() => setLoading(false));
    }
  }, [job]);

  if (!job) return null;

  const handleExportTrace = () => {
    const tracePayload = {
      job,
      dag: dagInfo,
      attempts,
      events,
      exportedAt: new Date().toISOString(),
      engineVersion: 'TaskForge Enterprise 1.0.0',
    };
    const blob = new Blob([JSON.stringify(tracePayload, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `trace-${job.name.toLowerCase().replace(/[^a-z0-9]/g, '-')}-${job.id.slice(0, 8)}.json`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  let parsedPayload: any = null;
  if (job.payload) {
    try {
      parsedPayload = JSON.parse(job.payload);
    } catch {
      parsedPayload = null;
    }
  }

  const queueWaitMs = job.queueWaitDurationMs ?? 0;
  const executionMs = job.executionDurationMs ?? 0;
  const totalDurationMs = queueWaitMs + executionMs;
  const queuePercent = totalDurationMs > 0 ? Math.max(4, Math.round((queueWaitMs / totalDurationMs) * 100)) : 0;
  const execPercent = totalDurationMs > 0 ? 100 - queuePercent : 0;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm font-sans select-none">
      <div className="bg-[#121316] border border-white/[0.08] rounded-xl w-full max-w-4xl max-h-[90vh] flex flex-col shadow-2xl overflow-hidden">
        {/* Modal Header */}
        <div className="p-4 border-b border-white/[0.08] bg-[#0d0e11] flex items-center justify-between">
          <div className="flex items-center gap-3">
            <h3 className="text-sm font-semibold text-zinc-100 truncate max-w-md">{job.name}</h3>
            <StatusBadge status={job.status} size="sm" />
            {job.idempotencyKey && (
              <span className="hidden sm:inline-flex items-center gap-1 px-2 py-0.5 rounded bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 font-mono text-[10px]">
                <ShieldCheck className="w-3 h-3" />
                Idempotent
              </span>
            )}
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={handleExportTrace}
              title="Download diagnostic trace JSON"
              className="p-1.5 rounded-md bg-zinc-900 hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200 border border-white/[0.08] transition-colors flex items-center gap-1.5 text-xs px-2"
            >
              <Download className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">Export Trace</span>
            </button>
            <button
              onClick={onClose}
              className="p-1.5 rounded-md bg-zinc-900 hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200 border border-white/[0.08] transition-colors"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Modal Body */}
        <div className="p-5 overflow-y-auto space-y-5 flex-1 text-xs">
          {/* Metadata Grid */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3 p-3.5 bg-[#16171b] border border-white/[0.05] rounded-lg text-xs">
            <div>
              <span className="text-[11px] text-zinc-500 block">Job Identifier (UUID)</span>
              <span className="text-zinc-200 font-mono text-[11px] font-medium">{job.id}</span>
            </div>
            <div>
              <span className="text-[11px] text-zinc-500 block">Workload Type</span>
              <span className="text-zinc-200 font-medium">{job.jobType}</span>
            </div>
            <div>
              <span className="text-[11px] text-zinc-500 block">Execution Attempts</span>
              <span className="text-zinc-200 font-mono font-medium">{job.attemptCount} / {job.maxAttempts}</span>
            </div>
            <div>
              <span className="text-[11px] text-zinc-500 block">Assigned Worker Node</span>
              <span className="text-zinc-200 font-mono text-[11px]">{job.workerId || 'None'}</span>
            </div>
            <div>
              <span className="text-[11px] text-zinc-500 block">Queue Ingress Latency</span>
              <span className="text-amber-400 font-mono font-medium">{job.queueWaitDurationMs ?? 0} ms</span>
            </div>
            <div>
              <span className="text-[11px] text-zinc-500 block">Execution Duration</span>
              <span className="text-emerald-400 font-mono font-medium">{job.executionDurationMs ?? 0} ms</span>
            </div>
            <div>
              <span className="text-[11px] text-zinc-500 block">Ingested Timestamp</span>
              <span className="text-zinc-300 font-mono text-[11px]">{new Date(job.createdAt).toLocaleTimeString()}</span>
            </div>
            <div>
              <span className="text-[11px] text-zinc-500 block">Scheduled Retry Window</span>
              <span className="text-purple-400 font-mono text-[11px]">
                {job.scheduledExecutionAt ? new Date(job.scheduledExecutionAt).toLocaleTimeString() : 'N/A'}
              </span>
            </div>
            {job.idempotencyKey && (
              <div className="col-span-2 md:col-span-4 pt-1 border-t border-white/[0.05]">
                <span className="text-[11px] text-zinc-500 block">Distributed Idempotency Key</span>
                <span className="text-emerald-400 font-mono text-[11px] font-medium">{job.idempotencyKey}</span>
              </div>
            )}
          </div>

          {/* Execution Timeline & Latency Trace Bar */}
          {totalDurationMs > 0 && (
            <div className="p-3.5 bg-[#16171b] border border-white/[0.05] rounded-lg">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs font-semibold text-zinc-300 flex items-center gap-1.5">
                  <Clock className="w-3.5 h-3.5 text-zinc-400" />
                  Execution Latency Breakdown
                </span>
                <span className="text-[11px] font-mono text-zinc-400">
                  Total: <strong className="text-zinc-200">{totalDurationMs} ms</strong>
                </span>
              </div>
              <div className="h-3 w-full bg-[#08090a] rounded-full overflow-hidden flex border border-white/[0.05]">
                <div
                  style={{ width: `${queuePercent}%` }}
                  className="bg-amber-500/80 hover:bg-amber-500 transition-all cursor-pointer"
                  title={`Queue Ingress: ${queueWaitMs} ms (${queuePercent}%)`}
                />
                <div
                  style={{ width: `${execPercent}%` }}
                  className="bg-emerald-500/80 hover:bg-emerald-500 transition-all cursor-pointer"
                  title={`Worker Execution: ${executionMs} ms (${execPercent}%)`}
                />
              </div>
              <div className="flex items-center justify-between text-[10px] text-zinc-400 font-mono mt-1.5">
                <span className="flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-amber-500 inline-block" />
                  Queue Ingress: {queueWaitMs} ms ({queuePercent}%)
                </span>
                <span className="flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-emerald-500 inline-block" />
                  Worker Execution: {executionMs} ms ({execPercent}%)
                </span>
              </div>
            </div>
          )}

          {/* Interactive DAG Pipeline Graph */}
          {dagInfo && (dagInfo.parentJob || (dagInfo.downstreamJobs && dagInfo.downstreamJobs.length > 0) || dagInfo.pipelineStep) && (
            <div className="p-3.5 bg-[#16171b] border border-white/[0.05] rounded-lg">
              <div className="flex items-center justify-between mb-3">
                <div className="flex items-center gap-1.5 font-semibold text-zinc-200">
                  <GitFork className="w-3.5 h-3.5 text-indigo-400" />
                  <span>Workflow Pipeline DAG Hierarchy</span>
                  {dagInfo.pipelineStep && (
                    <span className="text-[10px] px-1.5 py-0.5 bg-indigo-500/20 text-indigo-300 rounded border border-indigo-500/30 font-mono">
                      Step #{dagInfo.pipelineStep}
                    </span>
                  )}
                </div>
                <span className="text-[10px] text-zinc-500 font-mono">
                  {dagInfo.downstreamJobs?.length ?? 0} downstream dependent(s)
                </span>
              </div>
              <div className="flex flex-wrap items-center gap-2.5 overflow-x-auto py-1">
                {dagInfo.parentJob && (
                  <>
                    <div className="p-2.5 bg-[#0d0e11] border border-white/[0.08] rounded-md min-w-[160px]">
                      <span className="text-[10px] text-zinc-500 block uppercase font-mono tracking-wider">Prerequisite Upstream</span>
                      <span className="text-zinc-200 font-medium truncate block max-w-[180px] text-xs">{dagInfo.parentJob.name}</span>
                      <div className="mt-1 flex items-center gap-1.5">
                        <StatusBadge status={dagInfo.parentJob.status} size="sm" />
                        <span className="text-[10px] text-zinc-500 font-mono">{dagInfo.parentJob.id.slice(0, 8)}...</span>
                      </div>
                    </div>
                    <ArrowRight className="w-4 h-4 text-zinc-600 flex-shrink-0" />
                  </>
                )}
                <div className="p-2.5 bg-indigo-950/40 border border-indigo-500/40 rounded-md ring-1 ring-indigo-500/20 min-w-[180px]">
                  <span className="text-[10px] text-indigo-400 block uppercase font-mono tracking-wider font-semibold">Current Pipeline Node</span>
                  <span className="text-zinc-100 font-semibold truncate block max-w-[200px] text-xs">{job.name}</span>
                  <div className="mt-1 flex items-center gap-1.5">
                    <StatusBadge status={job.status} size="sm" />
                    <span className="text-[10px] text-zinc-400 font-mono">{job.id.slice(0, 8)}...</span>
                  </div>
                </div>
                {dagInfo.downstreamJobs && dagInfo.downstreamJobs.length > 0 && (
                  <>
                    <ArrowRight className="w-4 h-4 text-zinc-600 flex-shrink-0" />
                    <div className="flex flex-col gap-1.5">
                      {dagInfo.downstreamJobs.map((child) => (
                        <div key={child.id} className="p-2 bg-[#0d0e11] border border-white/[0.08] rounded-md flex items-center gap-2.5">
                          <div>
                            <span className="text-zinc-300 font-medium text-xs block">{child.name}</span>
                            <span className="text-[10px] text-zinc-500 font-mono">{child.id.slice(0, 8)}...</span>
                          </div>
                          <StatusBadge status={child.status} size="sm" />
                        </div>
                      ))}
                    </div>
                  </>
                )}
              </div>
            </div>
          )}

          {/* Last Error Diagnostic */}
          {job.lastErrorMessage && (
            <div className="p-3.5 bg-rose-500/10 border border-rose-500/30 rounded-lg text-rose-300 text-xs">
              <div className="font-semibold text-rose-400 mb-1 flex items-center gap-1.5">
                <AlertTriangle className="w-3.5 h-3.5" />
                <span>Error Diagnostic &amp; Root Cause:</span>
              </div>
              <p className="font-mono text-[11px]">{job.lastErrorMessage}</p>
            </div>
          )}

          {/* Payload Inspection */}
          {job.payload && (
            <div>
              <span className="text-xs font-semibold text-zinc-300 block mb-1.5">Task Payload Specification</span>
              <pre className="p-3 bg-[#16171b] border border-white/[0.05] rounded-lg text-zinc-300 font-mono text-[11px] overflow-x-auto">
                {job.payload}
              </pre>
            </div>
          )}

          {/* Execution Attempts Log */}
          <div>
            <div className="flex items-center gap-1.5 mb-2 border-b border-white/[0.08] pb-2">
              <History className="w-4 h-4 text-zinc-400" />
              <span className="text-xs font-semibold text-zinc-200">Execution Attempt History ({attempts.length})</span>
            </div>

            {loading ? (
              <div className="py-4 text-center text-zinc-500">Querying attempt records from cluster DB...</div>
            ) : attempts.length === 0 ? (
              <div className="py-4 text-center text-zinc-500">No attempts recorded yet.</div>
            ) : (
              <div className="border border-white/[0.08] rounded-lg divide-y divide-white/[0.05] overflow-hidden">
                {attempts.map((att) => (
                  <div key={att.id} className="p-3 bg-[#16171b] flex items-center justify-between gap-3 text-xs">
                    <div className="flex items-center gap-3">
                      <span className="font-semibold text-zinc-200">Attempt #{att.attemptNumber}</span>
                      <span className="text-zinc-400 font-mono text-[11px]">Worker: {att.workerId}</span>
                      <span className={`text-[10px] font-mono font-medium px-2 py-0.5 rounded border ${
                        att.outcome === 'SUCCEEDED'
                          ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30'
                          : att.outcome === 'FAILED'
                          ? 'bg-rose-500/10 text-rose-400 border-rose-500/30'
                          : 'bg-zinc-800 text-zinc-400 border-zinc-700'
                      }`}>
                        {att.outcome}
                      </span>
                    </div>
                    <div className="flex items-center gap-4 text-zinc-400 font-mono text-[11px]">
                      <span>Duration: {att.durationMs ?? 0} ms</span>
                      <span>Timestamp: {new Date(att.startedAt).toLocaleTimeString()}</span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Lifecycle Audit Trail */}
          <div>
            <div className="text-xs font-semibold text-zinc-200 mb-2 border-b border-white/[0.08] pb-2">
              Lifecycle Event Audit Trail ({events.length})
            </div>

            {loading ? (
              <div className="py-4 text-center text-zinc-500">Loading lifecycle events...</div>
            ) : events.length === 0 ? (
              <div className="py-4 text-center text-zinc-500">No lifecycle events recorded.</div>
            ) : (
              <div className="space-y-1.5 max-h-48 overflow-y-auto pr-1">
                {events.map((evt) => (
                  <div key={evt.id} className="p-2.5 bg-[#16171b] border border-white/[0.05] rounded-md text-xs flex items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <span className="text-zinc-500 font-mono text-[11px]">{new Date(evt.createdAt).toLocaleTimeString()}</span>
                      <span className="font-mono text-[10px] px-1.5 py-0.5 bg-zinc-800 text-zinc-300 rounded border border-white/[0.08]">{evt.eventType}</span>
                      <span className="text-zinc-300">{evt.message}</span>
                    </div>
                    {evt.workerId && <span className="text-zinc-500 font-mono text-[11px]">{evt.workerId}</span>}
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Modal Footer */}
        <div className="p-3.5 bg-[#0d0e11] border-t border-white/[0.08] flex items-center justify-between">
          <div className="text-xs text-zinc-500 font-mono">
            Optimistic Version: {job.version}
          </div>
          <div className="flex items-center gap-2">
            {(job.status === 'QUEUED' || job.status === 'RUNNING' || job.status === 'RETRY_WAIT') && (
              <button
                onClick={() => onCancel(job.id)}
                className="px-3 py-1.5 bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 border border-rose-500/30 font-medium text-xs rounded-md transition-colors shadow-sm"
              >
                {job.status === 'RUNNING' ? 'Interrupt Worker' : 'Cancel Task'}
              </button>
            )}
            {(job.status === 'FAILED' || job.status === 'CANCELLED') && (
              <button
                onClick={() => onRetry(job.id)}
                className="px-3 py-1.5 bg-amber-500/10 hover:bg-amber-500/20 text-amber-400 border border-amber-500/30 font-medium text-xs rounded-md transition-colors shadow-sm"
              >
                Retry Task
              </button>
            )}
            <button
              onClick={onClose}
              className="px-3 py-1.5 bg-zinc-900 hover:bg-zinc-800 text-zinc-200 border border-white/[0.08] font-medium text-xs rounded-md transition-colors shadow-sm"
            >
              Close
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
