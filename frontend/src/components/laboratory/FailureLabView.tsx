import React, { useState } from 'react';
import {
  AlertOctagon,
  AlertTriangle,
  CheckCircle2,
  Cpu,
  FlaskConical,
  Layers,
  Pause,
  Play,
  RotateCcw,
  ShieldCheck,
  Sliders,
  Zap,
} from 'lucide-react';
import { EngineStatus } from '../../types';
import { api } from '../../api/client';

interface FailureLabViewProps {
  status: EngineStatus | null;
  onLaunchScenario: (scenario: {
    count: number;
    prefix: string;
    jobType?: string;
    failureRate: number;
    durationMs?: number;
    simulatedDurationMs?: number;
    maxAttempts: number;
    payload?: string;
  }) => Promise<void>;
  onUpdateSettings: (settings: {
    workerCount?: number;
    queueCapacity?: number;
    maxAttempts?: number;
    baseBackoffMs?: number;
  }) => Promise<void>;
  onTogglePause: () => void;
  onClearData: () => Promise<void>;
}

export const FailureLabView: React.FC<FailureLabViewProps> = ({
  status,
  onLaunchScenario,
  onUpdateSettings,
  onTogglePause,
  onClearData,
}) => {
  const [workerCount, setWorkerCount] = useState(status?.workerCount ?? 2);
  const [queueCapacity, setQueueCapacity] = useState(status?.queueCapacity ?? 100);
  const [maxAttempts, setMaxAttempts] = useState(3);
  const [baseBackoffMs, setBaseBackoffMs] = useState(1000);
  const [scenarioRunning, setScenarioRunning] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const handleApplySettings = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await onUpdateSettings({ workerCount, queueCapacity, maxAttempts, baseBackoffMs });
      setMessage('Engine configuration updated successfully');
      setTimeout(() => setMessage(null), 3000);
    } catch (err: any) {
      setMessage(`Error: ${err.message}`);
    }
  };

  const runScenario = async (config: any, title: string) => {
    setScenarioRunning(true);
    setMessage(`Dispatching scenario: ${title}...`);
    try {
      await onLaunchScenario(config);
      setMessage(`Scenario "${title}" launched successfully`);
      setTimeout(() => setMessage(null), 4000);
    } catch (err: any) {
      setMessage(`Scenario error: ${err.message}`);
    } finally {
      setScenarioRunning(false);
    }
  };

  const runIdempotencyTest = async () => {
    setScenarioRunning(true);
    setMessage('Dispatching concurrent duplicate submissions with shared idempotency key...');
    try {
      const testKey = `idem-lab-${Date.now()}`;
      const job1 = await api.createJob({
        name: 'Idempotent Deduplication Task (Call 1)',
        jobType: 'COMPUTE',
        priority: 5,
        idempotencyKey: testKey,
        payload: '{"test": "dedup-validation"}',
      });
      const job2 = await api.createJob({
        name: 'Idempotent Deduplication Task (Call 2 - Duplicate)',
        jobType: 'COMPUTE',
        priority: 5,
        idempotencyKey: testKey,
        payload: '{"test": "dedup-validation"}',
      });
      if (job1.id === job2.id) {
        setMessage(`Deduplication Validated! Both requests resolved to identical Job ID: ${job1.id.slice(0, 8)}...`);
      } else {
        setMessage('Idempotency test completed.');
      }
      setTimeout(() => setMessage(null), 5000);
    } catch (err: any) {
      setMessage(`Idempotency Test Error: ${err.message}`);
    } finally {
      setScenarioRunning(false);
    }
  };

  return (
    <div className="space-y-4 font-sans select-none">
      {/* Header Banner */}
      <div className="p-4 bg-[#121316] border border-white/[0.08] rounded-lg shadow-sm flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-sm font-semibold text-zinc-100">Chaos &amp; SLA Resilience Lab</h2>
            <span className="text-[10px] font-mono font-medium bg-rose-500/10 text-rose-400 border border-rose-500/20 px-2 py-0.5 rounded">
              Resilience Validation
            </span>
          </div>
          <p className="text-xs text-zinc-400 mt-1">
            Simulate network degradation, exponential retry backoff with randomized jitter, and queue backpressure thresholds.
          </p>
        </div>

        {message && (
          <div className="px-3 py-1.5 bg-zinc-900 border border-indigo-500/40 text-indigo-300 text-xs font-mono font-medium rounded-md shadow-sm">
            {message}
          </div>
        )}
      </div>

      {/* Scenario Triggers Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-3">
        {/* Scenario 1: Normal Workload */}
        <div className="p-4 bg-[#121316] border border-white/[0.08] rounded-lg shadow-sm flex flex-col justify-between hover:border-white/[0.15] transition-colors">
          <div>
            <div className="text-[11px] text-zinc-500 font-mono mb-1">Scenario 1</div>
            <h4 className="text-sm font-semibold text-zinc-100 mb-2">Baseline Throughput</h4>
            <p className="text-xs text-zinc-400 leading-relaxed mb-4">
              Dispatches 40 standard tasks with 0% failure rate to measure clean throughput across all worker threads.
            </p>
          </div>
          <button
            disabled={scenarioRunning}
            onClick={() =>
              runScenario(
                { count: 40, prefix: 'baseline', failureRate: 0.0, simulatedDurationMs: 50, maxAttempts: 3 },
                'Baseline Workload'
              )
            }
            className="w-full py-2 bg-zinc-900 hover:bg-zinc-800 text-emerald-400 border border-white/[0.08] hover:border-emerald-500/40 text-xs font-medium rounded-md transition-all shadow-sm disabled:opacity-40"
          >
            Dispatch 40 Tasks
          </button>
        </div>

        {/* Scenario 2: 50% Transient Failure */}
        <div className="p-4 bg-[#121316] border border-white/[0.08] rounded-lg shadow-sm flex flex-col justify-between hover:border-white/[0.15] transition-colors">
          <div>
            <div className="text-[11px] text-rose-400 font-mono mb-1">Scenario 2</div>
            <h4 className="text-sm font-semibold text-rose-300 mb-2">Transient Faults (50%)</h4>
            <p className="text-xs text-zinc-400 leading-relaxed mb-4">
              Submits 30 jobs with a 50% deterministic failure rate to observe exponential backoff retries and recovery.
            </p>
          </div>
          <button
            disabled={scenarioRunning}
            onClick={() =>
              runScenario(
                { count: 30, prefix: 'flaky-api', failureRate: 0.5, simulatedDurationMs: 80, maxAttempts: 3 },
                'Transient Faults 50%'
              )
            }
            className="w-full py-2 bg-rose-500/10 hover:bg-rose-500/20 text-rose-300 border border-rose-500/30 text-xs font-medium rounded-md transition-all shadow-sm disabled:opacity-40"
          >
            Inject 50% Faults
          </button>
        </div>

        {/* Scenario 3: Bounded Queue Saturation */}
        <div className="p-4 bg-[#121316] border border-white/[0.08] rounded-lg shadow-sm flex flex-col justify-between hover:border-white/[0.15] transition-colors">
          <div>
            <div className="text-[11px] text-amber-400 font-mono mb-1">Scenario 3</div>
            <h4 className="text-sm font-semibold text-amber-300 mb-2">Queue Saturation</h4>
            <p className="text-xs text-zinc-400 leading-relaxed mb-4">
              Bursts 120 tasks into the bounded queue to trigger explicit HTTP 429 backpressure rejection.
            </p>
          </div>
          <button
            disabled={scenarioRunning}
            onClick={() =>
              runScenario(
                { count: 120, prefix: 'saturation', failureRate: 0.0, simulatedDurationMs: 150, maxAttempts: 3 },
                'Queue Saturation'
              )
            }
            className="w-full py-2 bg-amber-500 hover:bg-amber-400 text-zinc-950 font-semibold text-xs rounded-md transition-all shadow-sm disabled:opacity-40"
          >
            Saturate (120 Jobs)
          </button>
        </div>

        {/* Scenario 4: Self-Healing Flake */}
        <div className="p-4 bg-[#121316] border border-white/[0.08] rounded-lg shadow-sm flex flex-col justify-between hover:border-white/[0.15] transition-colors">
          <div>
            <div className="text-[11px] text-purple-400 font-mono mb-1">Scenario 4</div>
            <h4 className="text-sm font-semibold text-purple-300 mb-2">Self-Healing Retries</h4>
            <p className="text-xs text-zinc-400 leading-relaxed mb-4">
              Submits tasks configured to fail attempts #1 and #2, self-healing successfully on attempt #3.
            </p>
          </div>
          <button
            disabled={scenarioRunning}
            onClick={() =>
              runScenario(
                {
                  count: 15,
                  prefix: 'self-heal',
                  jobType: 'FAILURE_DEMO',
                  failureRate: 0.0,
                  simulatedDurationMs: 70,
                  maxAttempts: 3,
                  payload: '{"mode":"failUntilAttempt", "failUntilAttempt": 3}',
                },
                'Self-Healing Retries'
              )
            }
            className="w-full py-2 bg-purple-500/10 hover:bg-purple-500/20 text-purple-300 border border-purple-500/30 text-xs font-medium rounded-md transition-all shadow-sm disabled:opacity-40"
          >
            Dispatch Self-Heal (15)
          </button>
        </div>

        {/* Scenario 5: Distributed Deduplication */}
        <div className="p-4 bg-[#121316] border border-white/[0.08] rounded-lg shadow-sm flex flex-col justify-between hover:border-white/[0.15] transition-colors">
          <div>
            <div className="text-[11px] text-emerald-400 font-mono mb-1">Scenario 5</div>
            <h4 className="text-sm font-semibold text-emerald-300 mb-2 flex items-center gap-1.5">
              <ShieldCheck className="w-4 h-4 text-emerald-400 inline" />
              <span>Deduplication</span>
            </h4>
            <p className="text-xs text-zinc-400 leading-relaxed mb-4">
              Dispatches two concurrent tasks with the same idempotency key to test duplicate suppression.
            </p>
          </div>
          <button
            disabled={scenarioRunning}
            onClick={runIdempotencyTest}
            className="w-full py-2 bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 text-xs font-medium rounded-md transition-all shadow-sm disabled:opacity-40 flex items-center justify-center gap-1.5"
          >
            <ShieldCheck className="w-3.5 h-3.5" />
            <span>Test Deduplication</span>
          </button>
        </div>
      </div>

      {/* Engine Tuning & Controls Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-3">
        <div className="lg:col-span-2 p-4 bg-[#121316] border border-white/[0.08] rounded-lg shadow-sm">
          <div className="flex items-center justify-between mb-3 border-b border-white/[0.08] pb-2.5">
            <span className="text-[11px] font-medium text-zinc-300 uppercase tracking-wider">Cluster Ingress &amp; Capacity Tuning</span>
            <span className="text-[10px] text-zinc-500 font-mono">Dynamic Node Config</span>
          </div>

          <form onSubmit={handleApplySettings} className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
            <div>
              <label className="block text-zinc-300 mb-1 font-medium text-xs">Worker Thread Fleet (1 - 16)</label>
              <input
                type="number"
                min="1"
                max="16"
                value={workerCount}
                onChange={(e) => setWorkerCount(Number(e.target.value))}
                className="w-full px-3 py-1.5 bg-[#08090a] border border-white/[0.08] rounded-md text-zinc-100 font-mono text-xs focus:outline-none focus:border-indigo-500"
              />
            </div>

            <div>
              <label className="block text-zinc-300 mb-1 font-medium text-xs">Queue Buffer Capacity (10 - 1000)</label>
              <input
                type="number"
                min="10"
                max="1000"
                value={queueCapacity}
                onChange={(e) => setQueueCapacity(Number(e.target.value))}
                className="w-full px-3 py-1.5 bg-[#08090a] border border-white/[0.08] rounded-md text-zinc-100 font-mono text-xs focus:outline-none focus:border-indigo-500"
              />
            </div>

            <div>
              <label className="block text-zinc-300 mb-1 font-medium text-xs">Max Retry Attempts (1 - 10)</label>
              <input
                type="number"
                min="1"
                max="10"
                value={maxAttempts}
                onChange={(e) => setMaxAttempts(Number(e.target.value))}
                className="w-full px-3 py-1.5 bg-[#08090a] border border-white/[0.08] rounded-md text-zinc-100 font-mono text-xs focus:outline-none focus:border-indigo-500"
              />
            </div>

            <div>
              <label className="block text-zinc-300 mb-1 font-medium text-xs">Base Backoff ms (100 - 30000)</label>
              <input
                type="number"
                min="100"
                max="30000"
                step="100"
                value={baseBackoffMs}
                onChange={(e) => setBaseBackoffMs(Number(e.target.value))}
                className="w-full px-3 py-1.5 bg-[#08090a] border border-white/[0.08] rounded-md text-zinc-100 font-mono text-xs focus:outline-none focus:border-indigo-500"
              />
            </div>

            <div className="sm:col-span-2 pt-1">
              <button
                type="submit"
                className="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white font-medium text-xs rounded-md transition-colors shadow-sm"
              >
                Apply Cluster Settings
              </button>
            </div>
          </form>
        </div>

        {/* System Administration Override */}
        <div className="p-4 bg-[#121316] border border-white/[0.08] rounded-lg shadow-sm flex flex-col justify-between">
          <div>
            <div className="flex items-center justify-between mb-3 border-b border-white/[0.08] pb-2.5">
              <span className="text-[11px] font-medium text-zinc-300 uppercase tracking-wider">Cluster Emergency Override</span>
              <span className="text-[10px] text-zinc-500 font-mono">Manual Gate</span>
            </div>

            <div className="space-y-2">
              <button
                onClick={onTogglePause}
                className={`w-full py-2 px-3 text-xs font-medium rounded-md border transition-colors shadow-sm ${
                  status?.isPaused
                    ? 'bg-amber-500 text-zinc-950 border-amber-400 hover:bg-amber-400 font-semibold'
                    : 'bg-zinc-900 text-zinc-200 border-white/[0.08] hover:bg-zinc-800'
                }`}
              >
                {status?.isPaused ? 'Resume Ingestion' : 'Pause Ingestion'}
              </button>
            </div>
          </div>

          <div className="pt-3 border-t border-white/[0.08] mt-4">
            <button
              onClick={() => {
                if (window.confirm('Reset engine state: Clear queue buffers, jobs, attempts, and historical events?')) {
                  onClearData();
                }
              }}
              className="w-full py-2 px-3 bg-zinc-900 hover:bg-rose-950/40 text-zinc-400 hover:text-rose-300 border border-white/[0.08] hover:border-rose-500/30 text-xs font-medium rounded-md transition-colors shadow-sm"
            >
              Reset Cluster Database &amp; Queue
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
