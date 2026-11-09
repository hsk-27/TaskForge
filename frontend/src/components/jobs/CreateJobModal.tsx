import React, { useState } from 'react';
import { X, Play } from 'lucide-react';
import { JobType } from '../../types';

interface CreateJobModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (job: {
    name: string;
    jobType: JobType;
    priority: number;
    payload?: string;
    maxAttempts: number;
    delaySeconds?: number;
  }) => Promise<void>;
}

export const CreateJobModal: React.FC<CreateJobModalProps> = ({ isOpen, onClose, onSubmit }) => {
  const [name, setName] = useState('');
  const [jobType, setJobType] = useState<JobType>('COMPUTE');
  const [priority, setPriority] = useState(0);
  const [maxAttempts, setMaxAttempts] = useState(3);
  const [delaySeconds, setDelaySeconds] = useState(0);
  const [payload, setPayload] = useState('{"limit": 5000}');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setError('Task name is required');
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await onSubmit({ name, jobType, priority, maxAttempts, payload, delaySeconds: delaySeconds > 0 ? delaySeconds : undefined });
      onClose();
    } catch (err: any) {
      setError(err.message || 'Failed to submit job');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm font-sans select-none">
      <div className="bg-[#121316] border border-white/[0.08] rounded-xl w-full max-w-lg shadow-2xl overflow-hidden">
        <div className="p-4 border-b border-white/[0.08] bg-[#0d0e11] flex items-center justify-between">
          <div className="flex items-center gap-2">
            <h3 className="text-sm font-semibold text-zinc-100">Ingest Workload Task</h3>
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
            <label className="block text-zinc-300 mb-1 font-medium text-xs">Task Name</label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Daily Data Export"
              className="w-full px-3 py-1.5 bg-[#08090a] border border-white/[0.08] rounded-md text-zinc-100 text-xs focus:outline-none focus:border-indigo-500 transition-colors"
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-zinc-300 mb-1 font-medium text-xs">Workload Type</label>
              <select
                value={jobType}
                onChange={(e) => setJobType(e.target.value as JobType)}
                className="w-full px-3 py-1.5 bg-[#08090a] border border-white/[0.08] rounded-md text-zinc-100 text-xs focus:outline-none focus:border-indigo-500 transition-colors"
              >
                <option value="COMPUTE">Compute (CPU Bound)</option>
                <option value="DATA_PROCESSING">Data Processing (Stats)</option>
                <option value="REPORT_GENERATION">Report Generation</option>
                <option value="SIMULATED_IO">Simulated I/O (Latency)</option>
                <option value="FAILURE_DEMO">Failure Demo (Fault Testing)</option>
              </select>
            </div>

            <div>
              <label className="block text-zinc-300 mb-1 font-medium text-xs">Max Retry Attempts</label>
              <input
                type="number"
                min="1"
                max="10"
                value={maxAttempts}
                onChange={(e) => setMaxAttempts(Number(e.target.value))}
                className="w-full px-3 py-1.5 bg-[#08090a] border border-white/[0.08] rounded-md text-zinc-100 text-xs font-mono focus:outline-none focus:border-indigo-500 transition-colors"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-zinc-300 mb-1 font-medium text-xs">Execution Priority (0 - 10)</label>
              <input
                type="number"
                min="0"
                max="10"
                value={priority}
                onChange={(e) => setPriority(Number(e.target.value))}
                className="w-full px-3 py-1.5 bg-[#08090a] border border-white/[0.08] rounded-md text-zinc-100 text-xs font-mono focus:outline-none focus:border-indigo-500 transition-colors"
              />
              <span className="text-[10px] text-zinc-500 mt-0.5 block">Higher values preempt lower priority</span>
            </div>

            <div>
              <label className="block text-zinc-300 mb-1 font-medium text-xs">Delay Ingress (Seconds)</label>
              <input
                type="number"
                min="0"
                max="86400"
                value={delaySeconds}
                onChange={(e) => setDelaySeconds(Number(e.target.value))}
                className="w-full px-3 py-1.5 bg-[#08090a] border border-white/[0.08] rounded-md text-zinc-100 text-xs font-mono focus:outline-none focus:border-indigo-500 transition-colors"
              />
              <span className="text-[10px] text-zinc-500 mt-0.5 block">0 = immediate execution</span>
            </div>
          </div>

          <div>
            <label className="block text-zinc-300 mb-1 font-medium text-xs">Payload (JSON)</label>
            <textarea
              rows={3}
              value={payload}
              onChange={(e) => setPayload(e.target.value)}
              className="w-full px-3 py-2 bg-[#08090a] border border-white/[0.08] rounded-md text-zinc-100 font-mono text-[11px] focus:outline-none focus:border-indigo-500 transition-colors"
            />
          </div>

          <div className="flex items-center justify-end gap-2 pt-2 border-t border-white/[0.08]">
            <button
              type="button"
              onClick={onClose}
              className="px-3 py-1.5 bg-zinc-900 hover:bg-zinc-800 text-zinc-300 border border-white/[0.08] rounded-md font-medium text-xs transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="px-4 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-md font-medium text-xs transition-colors flex items-center gap-1.5 shadow-sm disabled:opacity-50"
            >
              <Play className="w-3.5 h-3.5" />
              <span>{submitting ? 'Dispatching...' : 'Dispatch Job'}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
