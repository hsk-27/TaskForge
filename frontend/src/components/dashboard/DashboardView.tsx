import React, { useState } from 'react';
import { EngineMetrics, EngineStatus, JobEvent } from '../../types';
import { MetricsCards } from './MetricsCards';
import { ThroughputChart } from './ThroughputChart';
import { RecentEventsFeed } from './RecentEventsFeed';
import {
  GitCommit,
  Layers,
  PlayCircle,
  Plus,
  RefreshCw,
  RotateCcw,
  ShieldCheck,
  Zap,
} from 'lucide-react';

interface DashboardViewProps {
  metrics: EngineMetrics | null;
  status: EngineStatus | null;
  events: JobEvent[];
  onRefresh: () => void;
  onOpenCreateModal: () => void;
  onOpenBatchModal: () => void;
  onBatchSubmit?: (batch: any) => Promise<void>;
  onClearData?: () => void;
}

export const DashboardView: React.FC<DashboardViewProps> = ({
  metrics,
  status,
  events,
  onRefresh,
  onOpenCreateModal,
  onOpenBatchModal,
  onBatchSubmit,
  onClearData,
}) => {
  const [dispatching, setDispatching] = useState<string | null>(null);

  const handleQuickDispatch = async (scenario: 'SMOKE_10' | 'BURST_100' | 'FAULT_8' | 'PIPELINE_5') => {
    if (!onBatchSubmit) return;
    setDispatching(scenario);
    try {
      if (scenario === 'SMOKE_10') {
        await onBatchSubmit({
          count: 10,
          prefix: 'smoke-job',
          jobType: 'DATA_PROCESSING',
          simulatedDurationMs: 60,
          failureRate: 0.0,
          maxAttempts: 3,
        });
      } else if (scenario === 'BURST_100') {
        await onBatchSubmit({
          count: 100,
          prefix: 'burst-job',
          jobType: 'COMPUTE',
          simulatedDurationMs: 100,
          failureRate: 0.1,
          maxAttempts: 3,
        });
      } else if (scenario === 'PIPELINE_5') {
        await onBatchSubmit({
          count: 5,
          prefix: 'etl-step',
          jobType: undefined,
          simulatedDurationMs: 90,
          failureRate: 0.0,
          maxAttempts: 3,
          isSequential: true,
        });
      } else if (scenario === 'FAULT_8') {
        await onBatchSubmit({
          count: 8,
          prefix: 'flaky-job',
          jobType: 'FAILURE_DEMO',
          simulatedDurationMs: 80,
          failureRate: 0.0,
          maxAttempts: 3,
          payload: '{"mode":"failUntilAttempt", "failUntilAttempt": 3}',
        });
      }
    } catch (err: any) {
      console.error('Dispatch error:', err);
    } finally {
      setDispatching(null);
    }
  };

  return (
    <div className="space-y-4 font-sans">
      {/* Cluster Control & Dispatch Bar */}
      <div className="p-4 bg-[#121316] border border-white/[0.08] rounded-lg shadow-sm flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2.5">
            <h2 className="text-sm font-semibold text-zinc-100">Cluster Operations Console</h2>
            <span className="text-[10px] font-mono font-medium bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 px-2 py-0.5 rounded">
              SLA NORMAL
            </span>
          </div>
          <p className="text-xs text-zinc-400 mt-1">
            Real-time telemetry, worker concurrency, and automated fault recovery.
          </p>
        </div>

        {/* Action Controls */}
        <div className="flex items-center gap-2 flex-wrap">
          <button
            disabled={dispatching !== null}
            onClick={() => handleQuickDispatch('SMOKE_10')}
            className="flex items-center gap-1.5 px-3 py-1.5 bg-zinc-900 hover:bg-zinc-800 text-zinc-200 border border-white/[0.08] hover:border-white/[0.15] text-xs font-medium rounded-md transition-all shadow-sm disabled:opacity-50"
            title="Dispatch 10 smoke-test jobs"
          >
            <Zap className="w-3.5 h-3.5 text-emerald-400" />
            <span>{dispatching === 'SMOKE_10' ? 'Dispatching...' : '10 Smoke Jobs'}</span>
          </button>

          <button
            disabled={dispatching !== null}
            onClick={() => handleQuickDispatch('BURST_100')}
            className="flex items-center gap-1.5 px-3 py-1.5 bg-zinc-900 hover:bg-zinc-800 text-zinc-200 border border-white/[0.08] hover:border-white/[0.15] text-xs font-medium rounded-md transition-all shadow-sm disabled:opacity-50"
            title="Dispatch 100-job stress burst"
          >
            <Layers className="w-3.5 h-3.5 text-indigo-400" />
            <span>{dispatching === 'BURST_100' ? 'Dispatching...' : '100 Job Burst'}</span>
          </button>

          <button
            disabled={dispatching !== null}
            onClick={() => handleQuickDispatch('PIPELINE_5')}
            className="flex items-center gap-1.5 px-3 py-1.5 bg-zinc-900 hover:bg-zinc-800 text-zinc-200 border border-white/[0.08] hover:border-white/[0.15] text-xs font-medium rounded-md transition-all shadow-sm disabled:opacity-50"
            title="Dispatch 5-stage sequential workflow pipeline DAG"
          >
            <GitCommit className="w-3.5 h-3.5 text-sky-400" />
            <span>{dispatching === 'PIPELINE_5' ? 'Dispatching...' : 'ETL Pipeline (5)'}</span>
          </button>

          <button
            onClick={onOpenCreateModal}
            className="flex items-center gap-1.5 px-3 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-medium rounded-md transition-colors shadow-sm"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Ingest Job</span>
          </button>

          <button
            onClick={onOpenBatchModal}
            className="flex items-center gap-1.5 px-3 py-1.5 bg-zinc-900 hover:bg-zinc-800 text-zinc-300 border border-white/[0.08] hover:border-white/[0.15] text-xs font-medium rounded-md transition-colors shadow-sm"
          >
            <PlayCircle className="w-3.5 h-3.5" />
            <span>Batch Dispatch</span>
          </button>

          <button
            onClick={onRefresh}
            className="flex items-center gap-1 px-2.5 py-1.5 bg-zinc-900 hover:bg-zinc-800 text-zinc-300 border border-white/[0.08] hover:border-white/[0.15] text-xs font-medium rounded-md transition-colors shadow-sm"
            title="Refresh metrics and events"
          >
            <RefreshCw className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* Metrics Cards */}
      <MetricsCards metrics={metrics} status={status} />

      {/* Visual Analytics */}
      <ThroughputChart metrics={metrics} />

      {/* Live Event Stream */}
      <RecentEventsFeed events={events} />
    </div>
  );
};
