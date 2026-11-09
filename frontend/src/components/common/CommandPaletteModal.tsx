import React, { useState, useEffect, useRef } from 'react';
import {
  Terminal,
  Zap,
  Cpu,
  Layers,
  Pause,
  Play,
  RotateCcw,
  Download,
  Filter,
  Navigation,
  CheckCircle2,
  AlertTriangle,
  GitCommit,
  Leaf,
} from 'lucide-react';
import { audioTelemetry } from '../../utils/audio';

export interface CommandItem {
  id: string;
  category: 'OPERATIONS' | 'ENGINE' | 'WORKFLOW' | 'NAVIGATION' | 'FILTER';
  name: string;
  syntax: string;
  description: string;
  icon: React.ComponentType<{ className?: string }>;
  action: () => void | Promise<void>;
}

interface CommandPaletteModalProps {
  isOpen: boolean;
  onClose: () => void;
  onDispatchBatch: (count: number, isSequential?: boolean) => void;
  onTogglePause: () => void;
  onToggleEco: () => void;
  onScaleWorkers: (count: number) => void;
  onClearData: () => void;
  onReplayDlq: () => void;
  onExport: () => void;
  onNavigateTab: (tab: any) => void;
  onFilterChange: (status?: string) => void;
  isPaused: boolean;
  isEco: boolean;
}

export const CommandPaletteModal: React.FC<CommandPaletteModalProps> = ({
  isOpen,
  onClose,
  onDispatchBatch,
  onTogglePause,
  onToggleEco,
  onScaleWorkers,
  onClearData,
  onReplayDlq,
  onExport,
  onNavigateTab,
  onFilterChange,
  isPaused,
  isEco,
}) => {
  const [query, setQuery] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

  const commands: CommandItem[] = [
    {
      id: 'cmd-burst-10',
      category: 'OPERATIONS',
      name: 'Dispatch 10 Smoke Tasks',
      syntax: '/burst 10',
      description: 'Submit 10 fast simulated smoke test jobs across worker pool',
      icon: Zap,
      action: () => onDispatchBatch(10, false),
    },
    {
      id: 'cmd-burst-100',
      category: 'OPERATIONS',
      name: 'Launch 100 Job Concurrency Burst',
      syntax: '/burst 100',
      description: 'Stress test bounded queue and concurrent worker throughput',
      icon: Zap,
      action: () => onDispatchBatch(100, false),
    },
    {
      id: 'cmd-dag-pipeline',
      category: 'WORKFLOW',
      name: 'Launch Sequential DAG Pipeline',
      syntax: '/pipeline 5',
      description: 'Submit 5-stage sequential ETL workflow (Step 1 -> Step 2 -> Step 3...)',
      icon: GitCommit,
      action: () => onDispatchBatch(5, true),
    },
    {
      id: 'cmd-pause-toggle',
      category: 'ENGINE',
      name: isPaused ? 'Resume Job Dispatching' : 'Pause Job Dispatching',
      syntax: isPaused ? '/resume' : '/pause',
      description: isPaused ? 'Resume claiming queued work from buffer' : 'Halt worker intake without terminating active jobs',
      icon: isPaused ? Play : Pause,
      action: onTogglePause,
    },
    {
      id: 'cmd-eco-toggle',
      category: 'ENGINE',
      name: isEco ? 'Disable PC-Safe Eco Mode' : 'Enable PC-Safe Eco Mode',
      syntax: '/eco',
      description: isEco ? 'Switch to aggressive high-throughput processing' : 'Lower thread priority and throttle cooperative sleep',
      icon: Leaf,
      action: onToggleEco,
    },
    {
      id: 'cmd-workers-4',
      category: 'ENGINE',
      name: 'Scale Worker Pool to 4 Threads',
      syntax: '/workers 4',
      description: 'Set concurrent background worker threads to 4',
      icon: Cpu,
      action: () => onScaleWorkers(4),
    },
    {
      id: 'cmd-workers-8',
      category: 'ENGINE',
      name: 'Scale Worker Pool to 8 Threads',
      syntax: '/workers 8',
      description: 'Set concurrent background worker threads to 8',
      icon: Cpu,
      action: () => onScaleWorkers(8),
    },
    {
      id: 'cmd-replay-dlq',
      category: 'OPERATIONS',
      name: 'Replay Dead Letter Queue',
      syntax: '/replay-dlq',
      description: 'Reset all permanently failed jobs and requeue for immediate retry',
      icon: RotateCcw,
      action: onReplayDlq,
    },
    {
      id: 'cmd-export-diag',
      category: 'OPERATIONS',
      name: 'Export Complete Diagnostics JSON',
      syntax: '/export',
      description: 'Download full system audit with JVM memory, metrics, and attempts',
      icon: Download,
      action: onExport,
    },
    {
      id: 'cmd-purge-db',
      category: 'ENGINE',
      name: 'Purge Database and Queue',
      syntax: '/clear',
      description: 'Wipe all database records, attempts, events and execution buffer',
      icon: AlertTriangle,
      action: onClearData,
    },
    {
      id: 'cmd-nav-dash',
      category: 'NAVIGATION',
      name: 'Navigate to Dashboard',
      syntax: '/goto dashboard',
      description: 'Switch to operations console and real-time throughput metrics',
      icon: Navigation,
      action: () => onNavigateTab('dashboard'),
    },
    {
      id: 'cmd-nav-jobs',
      category: 'NAVIGATION',
      name: 'Navigate to Jobs Explorer',
      syntax: '/goto jobs',
      description: 'Switch to database records grid, audit trail and details inspector',
      icon: Navigation,
      action: () => onNavigateTab('jobs'),
    },
    {
      id: 'cmd-nav-workers',
      category: 'NAVIGATION',
      name: 'Navigate to Worker Pool',
      syntax: '/goto workers',
      description: 'Inspect OS thread pool hardware allocation and concurrency state',
      icon: Navigation,
      action: () => onNavigateTab('workers'),
    },
    {
      id: 'cmd-filter-failed',
      category: 'FILTER',
      name: 'Filter to Failed Jobs (DLQ)',
      syntax: '/filter failed',
      description: 'Filter explorer table to show only FAILED status records',
      icon: Filter,
      action: () => {
        onNavigateTab('jobs');
        onFilterChange('FAILED');
      },
    },
    {
      id: 'cmd-filter-running',
      category: 'FILTER',
      name: 'Filter to Active Running Jobs',
      syntax: '/filter running',
      description: 'Filter explorer table to show only active RUNNING records',
      icon: Filter,
      action: () => {
        onNavigateTab('jobs');
        onFilterChange('RUNNING');
      },
    },
  ];

  const filteredCommands = commands.filter((cmd) => {
    const q = query.toLowerCase().trim();
    if (!q) return true;
    return (
      cmd.name.toLowerCase().includes(q) ||
      cmd.syntax.toLowerCase().includes(q) ||
      cmd.description.toLowerCase().includes(q) ||
      cmd.category.toLowerCase().includes(q)
    );
  });

  useEffect(() => {
    if (isOpen) {
      setQuery('');
      setSelectedIndex(0);
      setTimeout(() => inputRef.current?.focus(), 50);
    }
  }, [isOpen]);

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setSelectedIndex((prev) => (prev < filteredCommands.length - 1 ? prev + 1 : 0));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setSelectedIndex((prev) => (prev > 0 ? prev - 1 : filteredCommands.length - 1));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      if (filteredCommands[selectedIndex]) {
        executeCommand(filteredCommands[selectedIndex]);
      }
    } else if (e.key === 'Escape') {
      onClose();
    }
  };

  const executeCommand = (cmd: CommandItem) => {
    audioTelemetry.commandExecuted();
    cmd.action();
    onClose();
  };

  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-start justify-center pt-20 px-4 font-sans select-none"
      onClick={onClose}
    >
      <div
        className="w-full max-w-2xl bg-[#121316] border border-white/[0.08] rounded-xl shadow-2xl flex flex-col overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header bar */}
        <div className="flex items-center gap-2 px-4 py-3 bg-[#0d0e11] border-b border-white/[0.08] text-xs text-zinc-300">
          <Terminal className="w-4 h-4 text-indigo-400" />
          <span className="font-semibold text-zinc-100">Command Palette</span>
          <span className="ml-auto text-[11px] font-mono text-zinc-500">Esc to close</span>
        </div>

        {/* Search input */}
        <div className="p-3 bg-[#08090a] border-b border-white/[0.08]">
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Type a command or keyword (e.g. /burst, /pipeline, /eco, /workers)..."
            className="w-full bg-[#121316] border border-white/[0.08] rounded-lg px-3.5 py-2.5 text-xs text-zinc-100 placeholder-zinc-500 focus:outline-none focus:border-indigo-500 transition-colors font-sans"
          />
        </div>

        {/* Command list */}
        <div className="max-h-96 overflow-y-auto divide-y divide-white/[0.05] bg-[#121316]">
          {filteredCommands.length === 0 ? (
            <div className="p-6 text-center text-xs text-zinc-500">
              No commands matching &quot;{query}&quot;
            </div>
          ) : (
            filteredCommands.map((cmd, idx) => {
              const Icon = cmd.icon;
              const isSelected = idx === selectedIndex;
              return (
                <div
                  key={cmd.id}
                  onClick={() => executeCommand(cmd)}
                  onMouseEnter={() => setSelectedIndex(idx)}
                  className={`px-4 py-3 cursor-pointer flex items-center justify-between gap-3 transition-colors ${
                    isSelected ? 'bg-indigo-600/15 text-indigo-300' : 'hover:bg-white/[0.02]'
                  }`}
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <div
                      className={`p-2 rounded-md ${
                        isSelected ? 'bg-indigo-600/30 text-indigo-300' : 'bg-zinc-900 text-zinc-400 border border-white/[0.05]'
                      }`}
                    >
                      <Icon className="w-4 h-4" />
                    </div>
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <span className={`text-xs font-semibold ${isSelected ? 'text-indigo-300' : 'text-zinc-100'}`}>
                          {cmd.name}
                        </span>
                        <span className="font-mono text-[10px] px-1.5 py-0.5 rounded bg-zinc-900 text-zinc-400 border border-white/[0.08]">
                          {cmd.syntax}
                        </span>
                      </div>
                      <p className="text-[11px] text-zinc-400 truncate mt-0.5">{cmd.description}</p>
                    </div>
                  </div>
                  <span className="text-[10px] font-mono text-zinc-500 px-1.5 py-0.5 rounded bg-zinc-900 border border-white/[0.05] shrink-0">
                    {cmd.category}
                  </span>
                </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
};
