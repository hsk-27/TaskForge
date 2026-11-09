import React from 'react';
import {
  Activity,
  BarChart3,
  Cpu,
  FlaskConical,
  GitFork,
  Layers,
  LayoutDashboard,
  ListOrdered,
  Server,
  ShieldCheck,
} from 'lucide-react';

export type TabType = 'dashboard' | 'jobs' | 'workers' | 'laboratory' | 'benchmark';

interface SidebarProps {
  activeTab: TabType;
  onSelectTab: (tab: TabType) => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ activeTab, onSelectTab }) => {
  const tabs = [
    { id: 'dashboard' as TabType, label: 'Cluster Overview', icon: LayoutDashboard },
    { id: 'jobs' as TabType, label: 'Job Queues & DLQ', icon: ListOrdered },
    { id: 'workers' as TabType, label: 'Worker Fleet', icon: Cpu },
    { id: 'laboratory' as TabType, label: 'Chaos & SLA Lab', icon: FlaskConical },
    { id: 'benchmark' as TabType, label: 'Throughput Benchmarks', icon: BarChart3 },
  ];

  return (
    <aside className="w-60 border-r border-white/[0.08] bg-[#0d0e11] flex flex-col p-3 shrink-0 font-sans select-none">
      <div className="text-[11px] font-medium text-zinc-500 uppercase tracking-wider px-2 mb-2.5">
        Platform Operations
      </div>

      <nav className="space-y-1">
        {tabs.map((tab) => {
          const Icon = tab.icon;
          const isActive = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              onClick={() => onSelectTab(tab.id)}
              className={`w-full flex items-center gap-2.5 px-3 py-2 text-xs font-medium rounded-md transition-all ${
                isActive
                  ? 'bg-zinc-800/80 text-zinc-100 border border-white/[0.12] shadow-sm'
                  : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-900/60 border border-transparent'
              }`}
            >
              <Icon className={`w-4 h-4 ${isActive ? 'text-indigo-400' : 'text-zinc-500'}`} />
              <span className="truncate">{tab.label}</span>
            </button>
          );
        })}
      </nav>

      {/* Cluster Invariants & SLA Status */}
      <div className="mt-auto border-t border-white/[0.08] pt-3 px-2 text-[11px] space-y-2">
        <div className="flex items-center gap-1.5 text-zinc-300 font-medium">
          <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
          <span>Cluster SLA Invariants</span>
        </div>
        <div className="space-y-1 font-mono text-[10px] text-zinc-400">
          <div className="flex items-center justify-between">
            <span>Concurrency Guard:</span>
            <span className="text-zinc-300 font-semibold">2-Phase Tx</span>
          </div>
          <div className="flex items-center justify-between">
            <span>Buffer Backpressure:</span>
            <span className="text-zinc-300 font-semibold">HTTP 429</span>
          </div>
          <div className="flex items-center justify-between">
            <span>DAG Abort Cascade:</span>
            <span className="text-emerald-400 font-semibold">Recursive</span>
          </div>
          <div className="flex items-center justify-between">
            <span>Retry Distribution:</span>
            <span className="text-zinc-300 font-semibold">Jittered</span>
          </div>
        </div>
      </div>
    </aside>
  );
};
