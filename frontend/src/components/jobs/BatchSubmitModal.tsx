import React, { useState } from 'react';
import { GitCommit, X } from 'lucide-react';

interface BatchSubmitModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (batch: {
    count: number;
    prefix: string;
    jobType?: string;
    simulatedDurationMs: number;
    failureRate: number;
    maxAttempts: number;
    isSequential?: boolean;
  }) => Promise<void>;
}

export const BatchSubmitModal: React.FC<BatchSubmitModalProps> = ({
  isOpen,
  onClose,
  onSubmit,
}) => {
  const [count, setCount] = useState(20);
  const [prefix] = useState('batch-task');
  const [jobType, setJobType] = useState<string>('MIXED');
  const [simulatedDurationMs, setSimulatedDurationMs] = useState(100);
  const [failureRate] = useState(0.05);
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
        prefix: isSequential ? 'etl-pipeline' : prefix,
        jobType: jobType === 'MIXED' ? undefined : jobType,
        simulatedDurationMs,
        failureRate,
        maxAttempts,
        isSequential,
      });
      onClose();
    } catch (err: any) {
      setError(err.message || 'Failed to submit batch');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm font-sans select-none">
      <div className="bg-[#121316] border border-white/[0.08] rounded-xl w-full max-w-lg shadow-2xl overflow-hidden">
        <div className="p-4 border-b border-white/[0.08] bg-[#0d0e11] flex items-center justify-between">
          <div className="flex items-center gap-2">
            <h3 className="text-sm font-semibold text-zinc-100">
              {isSequential ? 'Configure Sequential Workflow DAG' : 'Configure High-Throughput Batch'}
            </h3>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-md bg-zinc-900 hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200 border border-white/[0.08] transition-colors"
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
            <div className="flex justify-between text-zinc-300 mb-1.5 font-medium">
              <span>Task Ingress Volume</span>
              <span className="font-mono text-indigo-400 font-semibold">{count} tasks</span>
            </div>
            <input
              type="range"
              min="2"
              max="200"
              step={isSequential ? '1' : '10'}
              value={count}
              onChange={(e) => setCount(Number(e.target.value))}
              className="w-full h-2 bg-zinc-800 rounded-lg accent-indigo-500 cursor-pointer"
            />
          </div>

          {/* Sequential DAG Pipeline Toggle */}
          <div className="p-3 bg-[#16171b] border border-white/[0.05] rounded-lg flex items-start gap-3">
            <input
              type="checkbox"
              id="seq-pipeline-toggle"
              checked={isSequential}
              onChange={(e) => {
                const checked = e.target.checked;
                setIsSequential(checked);
                if (checked && count > 20) setCount(5);
              }}
              className="mt-1 accent-indigo-600 w-4 h-4 rounded cursor-pointer"
            />
            <label htmlFor="seq-pipeline-toggle" className="cursor-pointer">
              <span className="text-xs font-semibold text-zinc-200 block flex items-center gap-1.5">
                <GitCommit className="w-3.5 h-3.5 text-indigo-400" />
                Execute as Sequential Workflow (DAG)
              </span>
              <span className="text-[11px] text-zinc-400 block mt-0.5">
                Each step waits for prerequisite parent completion before unblocking and entering the active queue.
              </span>
            </label>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-zinc-300 mb-1 font-medium text-xs">Workload Distribution</label>
              <select
                value={jobType}
                onChange={(e) => setJobType(e.target.value)}
                className="w-full px-3 py-1.5 bg-[#08090a] border border-white/[0.08] rounded-md text-zinc-100 text-xs focus:outline-none focus:border-indigo-500 transition-colors"
              >
                <option value="MIXED">Mixed (Balanced Pool)</option>
                <option value="COMPUTE">Compute (CPU Bound)</option>
                <option value="DATA_PROCESSING">Data Processing (Stats)</option>
                <option value="REPORT_GENERATION">Report Generation</option>
                <option value="SIMULATED_IO">Simulated I/O (Latency)</option>
                <option value="FAILURE_DEMO">Failure Demo (Fault Testing)</option>
              </select>
            </div>

            <div>
              <label className="block text-zinc-300 mb-1 font-medium text-xs">Latency (ms) Per Task</label>
              <input
                type="number"
                min="10"
                max="5000"
                step="10"
                value={simulatedDurationMs}
                onChange={(e) => setSimulatedDurationMs(Number(e.target.value))}
                className="w-full px-3 py-1.5 bg-[#08090a] border border-white/[0.08] rounded-md text-zinc-100 font-mono text-xs focus:outline-none focus:border-indigo-500 transition-colors"
              />
            </div>
          </div>

          <div className="pt-3 flex justify-end gap-2 border-t border-white/[0.08]">
            <button
              type="button"
              onClick={onClose}
              className="px-3.5 py-1.5 bg-zinc-900 hover:bg-zinc-800 text-zinc-300 border border-white/[0.08] font-medium text-xs rounded-md transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="px-4 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white font-medium text-xs rounded-md shadow-sm transition-colors disabled:opacity-50"
            >
              {submitting ? 'Dispatching...' : isSequential ? `Launch ${count}-Step Workflow` : `Dispatch ${count} Tasks`}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
