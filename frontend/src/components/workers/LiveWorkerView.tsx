import React from 'react';
import { EngineStatus } from '../../types';
import { Cpu, Layers, Server, ShieldCheck, Zap } from 'lucide-react';

interface LiveWorkerViewProps {
  status: EngineStatus | null;
  onScaleWorkers: (newCount: number) => void;
}

export const LiveWorkerView: React.FC<LiveWorkerViewProps> = ({ status, onScaleWorkers }) => {
  const workers = status?.workers || [];
  const currentCount = status?.workerCount || 2;
  const queueSize = status?.queueSize ?? 0;
  const queueCap = status?.queueCapacity ?? 100;
  const activeWorkers = status?.activeWorkers ?? 0;

  return (
    <div className="space-y-4 font-sans select-none">
      {/* Worker Pool Header & Scaling Control */}
      <div className="p-4 bg-[#121316] border border-white/[0.08] rounded-lg shadow-sm flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-sm font-semibold text-zinc-100">Worker Fleet &amp; OS Thread Pool</h2>
            <span className="text-[10px] font-mono font-medium bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 px-2 py-0.5 rounded">
              JVM Threads
            </span>
            <span className="text-[10px] font-mono font-medium bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 px-2 py-0.5 rounded flex items-center gap-1">
              <ShieldCheck className="w-3 h-3" />
              SLA Watchdog Active
            </span>
          </div>
          <p className="text-xs text-zinc-400 mt-1">
            Dedicated execution threads claiming tasks from the prioritized queue buffer outside database transactions.
          </p>
        </div>

        {/* Dynamic Scaling Controls */}
        <div className="flex items-center gap-2 p-1.5 bg-[#08090a] border border-white/[0.08] rounded-lg text-xs">
          <span className="text-zinc-400 px-2 text-[11px] font-medium">Fleet Size:</span>
          <button
            disabled={currentCount <= 1}
            onClick={() => onScaleWorkers(currentCount - 1)}
            className="w-7 h-7 flex items-center justify-center bg-zinc-900 hover:bg-zinc-800 text-zinc-200 border border-white/[0.08] rounded-md disabled:opacity-40 font-bold transition-colors"
            title="Scale down 1 worker"
          >
            -
          </button>
          <span className="text-zinc-100 font-mono font-semibold px-2 text-xs">{currentCount} Nodes</span>
          <button
            disabled={currentCount >= 16}
            onClick={() => onScaleWorkers(currentCount + 1)}
            className="w-7 h-7 flex items-center justify-center bg-zinc-900 hover:bg-zinc-800 text-zinc-200 border border-white/[0.08] rounded-md disabled:opacity-40 font-bold transition-colors"
            title="Scale up 1 worker"
          >
            +
          </button>
        </div>
      </div>

      {/* Concurrency Architecture Pipeline */}
      <div className="p-4 bg-[#121316] border border-white/[0.08] rounded-lg shadow-sm">
        <div className="flex items-center justify-between mb-3 border-b border-white/[0.08] pb-2.5">
          <span className="text-[11px] font-medium text-zinc-300 uppercase tracking-wider">Execution Pipeline Architecture</span>
          <span className="text-[10px] text-zinc-500 font-mono">Thread Lifecycle</span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-4 gap-3 items-stretch">
          {/* Step 1: Ingestion */}
          <div className="p-3 bg-[#16171b] border border-white/[0.05] rounded-lg">
            <span className="text-[10px] text-zinc-500 font-mono uppercase block mb-1">Stage 1: Ingestion</span>
            <span className="text-xs font-semibold text-zinc-200 block">REST &amp; Workflow DAG</span>
            <span className="text-[11px] text-zinc-400 mt-1 block">Transactional persistence</span>
          </div>

          {/* Step 2: Queue */}
          <div className="p-3 bg-[#16171b] border border-white/[0.05] rounded-lg">
            <span className="text-[10px] text-zinc-500 font-mono uppercase block mb-1">Stage 2: Priority Queue</span>
            <div className="flex items-center justify-between text-xs font-mono font-semibold text-amber-400">
              <span>Queue: {queueSize} / {queueCap}</span>
              <span className="text-[10px] text-zinc-500 font-normal">{Math.round((queueSize / queueCap) * 100)}%</span>
            </div>
            <div className="w-full bg-zinc-800 rounded-full h-1.5 mt-2 overflow-hidden">
              <div
                className="bg-amber-400 h-full rounded-full transition-all duration-300"
                style={{ width: `${Math.min(100, (queueSize / queueCap) * 100)}%` }}
              />
            </div>
            <span className="text-[11px] text-zinc-400 mt-1 block">Bounded FIFO preemption</span>
          </div>

          {/* Step 3: Workers */}
          <div className="p-3 bg-[#16171b] border border-white/[0.05] rounded-lg">
            <span className="text-[10px] text-zinc-500 font-mono uppercase block mb-1">Stage 3: Worker Threads</span>
            <div className="flex items-center justify-between text-xs font-mono font-semibold text-sky-400">
              <span>Threads: {activeWorkers} / {currentCount} Busy</span>
              <span className="text-[10px] text-zinc-500 font-normal">{Math.round((activeWorkers / (currentCount || 1)) * 100)}%</span>
            </div>
            <div className="w-full bg-zinc-800 rounded-full h-1.5 mt-2 overflow-hidden">
              <div
                className="bg-sky-400 h-full rounded-full transition-all duration-300"
                style={{ width: `${Math.min(100, (activeWorkers / (currentCount || 1)) * 100)}%` }}
              />
            </div>
            <span className="text-[11px] text-zinc-400 mt-1 block">Executed outside DB transactions</span>
          </div>

          {/* Step 4: Storage */}
          <div className="p-3 bg-[#16171b] border border-white/[0.05] rounded-lg">
            <span className="text-[10px] text-zinc-500 font-mono uppercase block mb-1">Stage 4: State &amp; Audit</span>
            <span className="text-xs font-semibold text-zinc-200 block">PostgreSQL / H2 Store</span>
            <span className="text-[11px] text-zinc-400 mt-1 block">Attempts, Events &amp; DLQ</span>
          </div>
        </div>
      </div>

      {/* Workers Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3">
        {workers.map((worker) => {
          const isBusy = worker.state === 'BUSY';
          const pct = worker.progressPercent ?? (isBusy ? 30 : 0);
          return (
            <div
              key={worker.workerId}
              className={`p-4 rounded-lg border transition-all flex flex-col justify-between ${
                isBusy
                  ? 'bg-[#141822] border-sky-500/40 text-zinc-100 shadow-sm'
                  : 'bg-[#121316] border-white/[0.08] text-zinc-400'
              }`}
            >
              <div>
                {/* Header */}
                <div className="flex items-center justify-between mb-3 border-b border-white/[0.08] pb-2">
                  <div className="flex items-center gap-2">
                    <span className={`w-2 h-2 rounded-full ${isBusy ? 'bg-sky-400 shadow-[0_0_6px_rgba(56,189,248,0.6)]' : 'bg-zinc-600'}`} />
                    <span className="font-mono font-semibold text-xs text-zinc-200">{worker.workerId}</span>
                  </div>
                  <span
                    className={`text-[10px] font-mono px-2 py-0.5 rounded font-medium ${
                      isBusy
                        ? 'bg-sky-500/10 text-sky-400 border border-sky-500/20'
                        : 'bg-zinc-800 text-zinc-400 border border-white/[0.08]'
                    }`}
                  >
                    {isBusy ? 'BUSY' : 'IDLE'}
                  </span>
                </div>

                {/* Workload Telemetry */}
                {isBusy ? (
                  <div className="space-y-2 text-xs">
                    <div>
                      <span className="text-[11px] text-zinc-500 block">Current Task:</span>
                      <span className="text-zinc-100 font-medium truncate block mt-0.5 font-mono text-[11px]" title={worker.currentJobName}>
                        {worker.currentJobName || 'Processing...'}
                      </span>
                    </div>

                    <div className="flex justify-between items-center text-xs font-mono">
                      <span className="text-zinc-500">Attempt:</span>
                      <span className="text-amber-400 font-semibold">#{worker.attemptNumber || 1}</span>
                    </div>

                    <div className="flex justify-between items-center text-xs font-mono">
                      <span className="text-zinc-500">Elapsed:</span>
                      <span className="text-emerald-400 font-semibold">
                        {worker.elapsedMs ? `${worker.elapsedMs} ms` : 'Measuring...'}
                      </span>
                    </div>

                    {/* Real-time Progress Bar */}
                    <div className="pt-2 border-t border-white/[0.08] mt-2">
                      <div className="flex justify-between items-center text-[11px] text-zinc-300 font-mono mb-1">
                        <span>Progress: {pct}%</span>
                      </div>
                      <div className="w-full bg-zinc-800 rounded-full h-1.5 overflow-hidden">
                        <div
                          className="bg-sky-400 h-full rounded-full transition-all duration-300"
                          style={{ width: `${Math.min(100, Math.max(5, pct))}%` }}
                        />
                      </div>
                      <div className="text-[11px] text-zinc-400 truncate mt-1.5 font-mono" title={worker.currentStage}>
                        {worker.currentStage || 'Executing handler...'}
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="py-7 text-center text-zinc-500 text-xs font-mono">
                    Node idle · ready for queue ingress
                  </div>
                )}
              </div>

              {/* Lifetime Worker Metrics Footer */}
              <div className="mt-4 pt-2.5 border-t border-white/[0.08] flex items-center justify-between text-[11px] font-mono text-zinc-500">
                <span>Completed: {worker.totalProcessed ?? 0}</span>
                <span>Failed: {worker.totalFailed ?? 0}</span>
                <span>Avg: {worker.avgDurationMs ? `${worker.avgDurationMs}ms` : '0ms'}</span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
