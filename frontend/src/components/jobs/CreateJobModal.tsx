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
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm font-sans select-none">
      <div className="bg-[#101726] border border-slate-800 rounded-xl w-full max-w-lg shadow-2xl overflow-hidden">
        <div className="p-4 border-b border-slate-800 bg-[#0d1424] flex items-center justify-between">
          <div className="flex items-center gap-2">
            <h3 className="text-sm font-semibold text-slate-100">Submit New Job</h3>
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
            <label className="block text-slate-300 mb-1 font-medium text-xs">Task Name</label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Daily Data Export"
              className="w-full px-3 py-1.5 bg-[#0b0f17] border border-slate-700 rounded-md text-slate-100 text-xs focus:outline-none focus:border-indigo-500 transition-colors"
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-slate-300 mb-1 font-medium text-xs">Workload Type</label>
              <select
                value={jobType}
                onChange={(e) => setJobType(e.target.value as JobType)}
                className="w-full px-3 py-1.5 bg-[#0b0f17] border border-slate-700 rounded-md text-slate-100 text-xs focus:outline-none focus:border-indigo-500 transition-colors"
              >
                <option value="COMPUTE">Compute (CPU Bound)</option>
                <option value="DATA_PROCESSING">Data Processing (Stats)</option>
                <option value="REPORT_GENERATION">Report Generation</option>
                <option value="SIMULATED_IO">Simulated I/O (Latency)</option>
                <option value="FAILURE_DEMO">Failure Demo (Fault Testing)</option>
              </select>
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

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-slate-300 mb-1 font-medium text-xs">Priority (-10 to 10)</label>
              <input
                type="number"
                min="-10"
                max="10"
                value={priority}
                onChange={(e) => setPriority(Number(e.target.value))}
                className="w-full px-3 py-1.5 bg-[#0b0f17] border border-slate-700 rounded-md text-slate-100 font-mono text-xs focus:outline-none focus:border-indigo-500 transition-colors"
              />
            </div>

            <div>
              <label className="block text-slate-300 mb-1 font-medium text-xs">Execution Delay (seconds)</label>
              <input
                type="number"
                min="0"
                max="3600"
                value={delaySeconds}
                onChange={(e) => setDelaySeconds(Number(e.target.value))}
                placeholder="0 = Immediate"
                className="w-full px-3 py-1.5 bg-[#0b0f17] border border-slate-700 rounded-md text-slate-100 font-mono text-xs focus:outline-none focus:border-indigo-500 transition-colors"
              />
            </div>
          </div>

          <div>
            <label className="block text-slate-300 mb-1 font-medium text-xs">Payload JSON</label>
            <textarea
              rows={3}
              value={payload}
              onChange={(e) => setPayload(e.target.value)}
              className="w-full px-3 py-1.5 bg-[#0b0f17] border border-slate-700 rounded-md text-slate-200 font-mono text-[11px] focus:outline-none focus:border-indigo-500 transition-colors"
            />
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
              {submitting ? 'Submitting...' : 'Submit Job'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
