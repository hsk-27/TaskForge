import React from 'react';
import {
  BarChart3,
  Cpu,
  FlaskConical,
  LayoutDashboard,
  ListOrdered,
  Server,
} from 'lucide-react';

export type TabType = 'dashboard' | 'jobs' | 'workers' | 'laboratory' | 'benchmark';

interface SidebarProps {
  activeTab: TabType;
  onSelectTab: (tab: TabType) => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ activeTab, onSelectTab }) => {
  const tabs = [
    { id: 'dashboard' as TabType, label: 'Dashboard', icon: LayoutDashboard },
    { id: 'jobs' as TabType, label: 'Jobs Explorer', icon: ListOrdered },
    { id: 'workers' as TabType, label: 'Worker Pool', icon: Cpu },
    { id: 'laboratory' as TabType, label: 'Failure Lab', icon: FlaskConical },
    { id: 'benchmark' as TabType, label: 'Benchmark', icon: BarChart3 },
  ];

  return (
    <aside className="w-56 border-r border-slate-800 bg-[#0d1424] flex flex-col p-3 shrink-0 font-sans select-none">
      <div className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider px-2 mb-2">
        Navigation
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
                  ? 'bg-indigo-600/15 text-indigo-400 border border-indigo-500/30'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60 border border-transparent'
              }`}
            >
              <Icon className={`w-4 h-4 ${isActive ? 'text-indigo-400' : 'text-slate-400'}`} />
              <span>{tab.label}</span>
            </button>
          );
        })}
      </nav>

      {/* Subsystem Architecture Summary */}
      <div className="mt-auto border-t border-slate-800/80 pt-3 px-2 text-[11px] text-slate-400 space-y-1.5">
        <div className="flex items-center gap-1.5 text-slate-300 font-medium">
          <Server className="w-3.5 h-3.5 text-emerald-400" />
          <span>Engine Subsystems</span>
        </div>
        <p className="text-slate-400 font-mono text-[10px]">Bounded FIFO Queue</p>
        <p className="text-slate-400 font-mono text-[10px]">Optimistic Concurrency</p>
        <p className="text-slate-400 font-mono text-[10px]">Exponential Backoff</p>
      </div>
    </aside>
  );
};
