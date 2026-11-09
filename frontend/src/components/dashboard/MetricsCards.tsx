import React from 'react';
import {
  CheckCircle2,
  Clock,
  Cpu,
  Layers,
  XCircle,
  Zap,
} from 'lucide-react';
import { EngineMetrics, EngineStatus } from '../../types';

interface MetricsCardsProps {
  metrics: EngineMetrics | null;
  status: EngineStatus | null;
}

export const MetricsCards: React.FC<MetricsCardsProps> = ({ metrics, status }) => {
  const cards = [
    {
      label: 'Total Processed',
      value: metrics?.totalJobs ?? 0,
      icon: Layers,
      color: 'text-zinc-400',
      badgeColor: 'bg-zinc-800 text-zinc-300',
      sub: `${metrics?.queuedJobs ?? 0} queued · ${metrics?.runningJobs ?? 0} active`,
    },
    {
      label: 'Succeeded',
      value: metrics?.succeededJobs ?? 0,
      icon: CheckCircle2,
      color: 'text-emerald-400',
      badgeColor: 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20',
      sub: `${metrics?.successRatePercent ?? 0}% success SLA`,
    },
    {
      label: 'Failed (Permanent)',
      value: metrics?.failedJobs ?? 0,
      icon: XCircle,
      color: 'text-rose-400',
      badgeColor: 'bg-rose-500/10 text-rose-400 border border-rose-500/20',
      sub: `${metrics?.retryWaitJobs ?? 0} in retry backoff`,
    },
    {
      label: 'Worker Pool',
      value: `${status?.activeWorkers ?? 0} / ${status?.workerCount ?? 0}`,
      icon: Cpu,
      color: 'text-indigo-400',
      badgeColor: 'bg-indigo-500/10 text-indigo-400 border border-indigo-500/20',
      sub: `${status?.activeWorkers ? Math.round((status.activeWorkers / (status.workerCount || 1)) * 100) : 0}% fleet load`,
    },
    {
      label: 'Avg Execution Time',
      value: `${metrics?.averageExecutionDurationMs ?? 0} ms`,
      icon: Clock,
      color: 'text-sky-400',
      badgeColor: 'bg-sky-500/10 text-sky-400 border border-sky-500/20',
      sub: `Queue latency: ${metrics?.averageQueueWaitDurationMs ?? 0} ms`,
    },
    {
      label: 'Throughput',
      value: `${metrics?.throughputPerMinute ?? 0}/min`,
      icon: Zap,
      color: 'text-amber-400',
      badgeColor: 'bg-amber-500/10 text-amber-400 border border-amber-500/20',
      sub: 'Trailing 60s window',
    },
  ];

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6 gap-3">
      {cards.map((card, idx) => {
        const Icon = card.icon;
        return (
          <div
            key={idx}
            className="p-3.5 bg-[#121316] border border-white/[0.08] hover:border-white/[0.15] rounded-lg shadow-sm flex flex-col justify-between transition-colors"
          >
            <div className="flex items-center justify-between mb-1.5">
              <span className="text-[11px] font-medium text-zinc-400 uppercase tracking-wider">{card.label}</span>
              <Icon className={`w-3.5 h-3.5 ${card.color}`} />
            </div>
            <div className="text-xl font-semibold font-mono text-zinc-100 tracking-tight my-1">
              {card.value}
            </div>
            <div className="text-[11px] text-zinc-500 font-mono truncate">{card.sub}</div>
          </div>
        );
      })}
    </div>
  );
};
