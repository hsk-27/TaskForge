import React, { useState } from 'react';
import { GitCommit, Layers, Play, X } from 'lucide-react';
import { JobType } from '../../types';

interface BatchSubmitModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (batch: {
    count: number;
    prefix: string;
    jobType?: JobType;
    failureRate: number;
    simulatedDurationMs: number;
    maxAttempts: number;
    isSequential?: boolean;
  }) => Promise<void>;
}

export const BatchSubmitModal: React.FC<BatchSubmitModalProps> = ({ isOpen, onClose, onSubmit }) => {
  const [count, setCount] = useState(100);
  const [prefix, setPrefix] = useState('sim-batch');
  const [jobType, setJobType] = useState<string>('MIXED');
  const [failureRate, setFailureRate] = useState(0.2); // 20% failure
  const [simulatedDurationMs, setSimulatedDurationMs] = useState(60);
  const [maxAttempts, setMaxAttempts] = useState(3);
  const [isSequential, setIsSequential] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await onSubmit({
        count,
        prefix,
        jobType: jobType === 'MIXED' ? undefined : (jobType as JobType),
        failureRate,
        simulatedDurationMs,
        maxAttempts,
        isSequential,
      });
      onClose();
    } catch (err: any) {
      setError(err.message || 'Failed to dispatch batch');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm font-sans select-none">
      <div className="bg-[#101726] border border-slate-800 rounded-xl w-full max-w-lg shadow-2xl overflow-hidden">
        <div className="p-4 border-b border-slate-800 bg-[#0d1424] flex items-center justify-between">
          <div className="flex items-center gap-2">
            <h3 className="text-sm font-semibold text-slate-100">
              {isSequential ? 'Configure Sequential DAG Pipeline' : 'Configure Concurrent Batch'}
            </h3>
          </div>
          <button
            onClick={onClose}
            className="p-1 rounded-md bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-slate-200 transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-5 space-y-4 text-xs">
          {error && (
            <div className="p-3 bg-rose-500/10 border border-rose-500/30 text-rose-400 rounded-md text-xs font-medium">
              {error}
            </div>
          )}

          <div>
            <div className="flex justify-between text-slate-300 mb-1.5 font-medium">
              <span>Batch Volume</span>
              <span className="font-mono text-indigo-400 font-semibold">{count} tasks</span>
            </div>
            <input
              type="range"
              min="2"
              max="200"
              step={isSequential ? '1' : '10'}
              value={count}
              onChange={(e) => setCount(Number(e.target.value))}
              className="w-full h-2 bg-slate-800 rounded-lg accent-indigo-500 cursor-pointer"
            />
          </div>

          {/* Sequential DAG Pipeline Toggle */}
          <div className="p-3 bg-[#0b0f17] border border-slate-800 rounded-lg flex items-start gap-3">
            <input
              type="checkbox"
              id="seq-pipeline-toggle"
              checked={isSequential}
              onChange={(e) => {
                const checked = e.target.checked;
                setIsSequential(checked);
                if (checked && count > 20) setCount(5); // Reasonable DAG default
              }}
              className="mt-1 accent-indigo-600 w-4 h-4 rounded cursor-pointer"
            />
            <label htmlFor="seq-pipeline-toggle" className="cursor-pointer">
              <span className="text-xs font-semibold text-slate-200 block flex items-center gap-1.5">
                <GitCommit className="w-3.5 h-3.5 text-indigo-400" />
                Execute as Sequential Pipeline (DAG)
              </span>
              <span className="text-[11px] text-slate-400 block mt-0.5">
                Each step waits for the prerequisite parent task to succeed before unblocking and entering the worker queue.
              </span>
            </label>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-slate-300 mb-1 font-medium text-xs">Task Name Prefix</label>
              <input
                type="text"
                value={prefix}
                onChange={(e) => setPrefix(e.target.value)}
                className="w-full px-3 py-1.5 bg-[#0b0f17] border border-slate-700 rounded-md text-slate-100 text-xs focus:outline-none focus:border-indigo-500 transition-colors"
              />
            </div>

            <div>
              <label className="block text-slate-300 mb-1 font-medium text-xs">Workload Distribution</label>
              <select
                value={jobType}
                onChange={(e) => setJobType(e.target.value)}
                className="w-full px-3 py-1.5 bg-[#0b0f17] border border-slate-700 rounded-md text-slate-100 text-xs focus:outline-none focus:border-indigo-500 transition-colors"
              >
                <option value="MIXED">Mixed (Balanced Pool)</option>
                <option value="COMPUTE">Compute (CPU Bound)</option>
                <option value="DATA_PROCESSING">Data Processing (Stats)</option>
                <option value="REPORT_GENERATION">Report Generation</option>
                <option value="SIMULATED_IO">Simulated I/O (Latency)</option>
                <option value="FAILURE_DEMO">Failure Demo (Fault Testing)</option>
              </select>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-slate-300 mb-1 font-medium text-xs">Latency (ms) Per Task</label>
              <input
                type="number"
                min="10"
                max="5000"
                step="10"
                value={simulatedDurationMs}
                onChange={(e) => setSimulatedDurationMs(Number(e.target.value))}
                className="w-full px-3 py-1.5 bg-[#0b0f17] border border-slate-700 rounded-md text-slate-100 font-mono text-xs focus:outline-none focus:border-indigo-500 transition-colors"
              />
            </div>

            <div>
              <label className="block text-slate-300 mb-1 font-medium text-xs">Max Retry Attempts</label>
              <input
                type="number"
                min="1"
                max="10"
                value={maxAttempts}
                onChange={(e) => setMaxAttempts(Number(e.target.value))}
                className="w-full px-3 py-1.5 bg-[#0b0f17] border border-slate-700 rounded-md text-slate-100 font-mono text-xs focus:outline-none focus:border-indigo-500 transition-colors"
              />
            </div>
          </div>

          <div className="pt-3 flex justify-end gap-2 border-t border-slate-800">
            <button
              type="button"
              onClick={onClose}
              className="px-3.5 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 font-medium text-xs rounded-md transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="px-4 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white font-medium text-xs rounded-md shadow-sm transition-colors disabled:opacity-50"
            >
              {submitting ? 'Dispatching...' : isSequential ? `Launch ${count}-Step Pipeline` : `Dispatch ${count} Tasks`}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
