import React, { useState } from 'react';
import {
  Activity,
  Cpu,
  Layers,
  Pause,
  Play,
  RotateCcw,
  Sparkles,
  Terminal,
  Volume2,
  VolumeX,
} from 'lucide-react';
import { EngineStatus } from '../../types';
import { audioTelemetry } from '../../utils/audio';

interface HeaderProps {
  status: EngineStatus | null;
  sseConnected: boolean;
  onTogglePause: () => void;
  onToggleEcoMode?: () => void;
  onClearData?: () => void;
  onOpenCommandPalette?: () => void;
  onOpenChat?: () => void;
}

export const Header: React.FC<HeaderProps> = ({
  status,
  sseConnected,
  onTogglePause,
  onClearData,
  onOpenCommandPalette,
  onOpenChat,
}) => {
  const isRunning = status?.isRunning ?? false;
  const isPaused = status?.isPaused ?? false;
  const [audioActive, setAudioActive] = useState(audioTelemetry.isEnabled());

  const handleToggleAudio = () => {
    const next = audioTelemetry.toggle();
    setAudioActive(next);
  };

  return (
    <header className="h-14 border-b border-white/[0.08] bg-[#0d0e11] px-4 flex items-center justify-between sticky top-0 z-30 font-sans select-none">
      {/* Enterprise Organization & Cluster Switcher */}
      <div className="flex items-center gap-3">
        <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-indigo-500/20 to-indigo-600/10 border border-indigo-500/30 flex items-center justify-center text-indigo-400 font-semibold text-xs tracking-wider shadow-inner">
          TF
        </div>
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-sm font-semibold text-zinc-100 tracking-tight">
              TaskForge
            </h1>
            <span className="text-[10px] font-mono font-medium px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
              PROD
            </span>
            <span className="text-[11px] text-zinc-500 hidden md:inline">
              /
            </span>
            <span className="text-[11px] text-zinc-400 font-mono hidden md:inline">
              cluster-us-east-1a
            </span>
          </div>
          <p className="text-[11px] text-zinc-500 hidden sm:block">
            Distributed Task Orchestration &amp; Reliable Workflows
          </p>
        </div>
      </div>

      {/* Real-time Telemetry & Global Controls */}
      <div className="flex items-center gap-2">
        {/* Command Palette Trigger */}
        {onOpenCommandPalette && (
          <button
            onClick={onOpenCommandPalette}
            className="flex items-center gap-1.5 px-2.5 py-1.5 text-xs font-medium bg-zinc-900/80 hover:bg-zinc-800 text-zinc-300 border border-white/[0.08] hover:border-white/[0.15] rounded-md transition-colors shadow-sm"
            title="Open Command Palette (Ctrl+K / Cmd+K)"
          >
            <Terminal className="w-3.5 h-3.5 text-zinc-400" />
            <span className="hidden sm:inline font-mono text-[11px] text-zinc-400">Ctrl+K</span>
          </button>
        )}

        {/* Enterprise Ops Copilot Trigger */}
        {onOpenChat && (
          <button
            onClick={onOpenChat}
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium bg-indigo-500/10 hover:bg-indigo-500/20 text-indigo-300 border border-indigo-500/30 hover:border-indigo-500/50 rounded-md transition-colors shadow-sm"
            title="Open TaskForge Ops Copilot (Ctrl+/)"
          >
            <Sparkles className="w-3.5 h-3.5 text-indigo-400" />
            <span className="font-medium text-xs">Ops Copilot</span>
            <span className="hidden sm:inline font-mono text-[10px] text-indigo-400/80 bg-indigo-500/20 px-1 py-0.2 rounded">Ctrl+/</span>
          </button>
        )}

        {/* Audio Telemetry Toggle */}
        <button
          onClick={handleToggleAudio}
          className={`flex items-center gap-1.5 px-2.5 py-1.5 text-xs font-medium border rounded-md transition-colors ${
            audioActive
              ? 'bg-zinc-900 text-zinc-200 border-white/[0.12] hover:bg-zinc-800'
              : 'bg-zinc-900/60 text-zinc-500 border-white/[0.06] hover:text-zinc-300 hover:bg-zinc-800'
          }`}
          title={audioActive ? 'Audio telemetry active (click to mute)' : 'Enable sound telemetry for state changes'}
        >
          {audioActive ? <Volume2 className="w-3.5 h-3.5 text-zinc-300" /> : <VolumeX className="w-3.5 h-3.5" />}
          <span className="hidden md:inline text-[11px]">{audioActive ? 'Chimes' : 'Muted'}</span>
        </button>

        {/* SSE Telemetry Link Status */}
        <div
          className={`flex items-center gap-1.5 px-2.5 py-1.5 text-xs rounded-md border font-medium ${
            sseConnected
              ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20'
              : 'bg-zinc-900 text-zinc-500 border-white/[0.06]'
          }`}
          title={sseConnected ? 'Connected to cluster event telemetry' : 'Event telemetry stream offline'}
        >
          <span className={`w-1.5 h-1.5 rounded-full ${sseConnected ? 'bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.5)]' : 'bg-zinc-600'}`} />
          <span className="text-[11px] font-mono">{sseConnected ? 'STREAM ACTIVE' : 'DISCONNECTED'}</span>
        </div>

        {/* Worker Pool Count Pill */}
        <div className="hidden lg:flex items-center gap-1.5 px-2.5 py-1.5 text-xs bg-zinc-900/80 text-zinc-300 border border-white/[0.08] rounded-md font-mono text-[11px]">
          <Cpu className="w-3.5 h-3.5 text-zinc-400" />
          <span>Workers:</span>
          <span className="text-zinc-100 font-semibold">{status?.activeWorkers ?? 0}/{status?.workerCount ?? 0}</span>
        </div>

        {/* Bounded Queue Depth Pill */}
        <div className="hidden lg:flex items-center gap-1.5 px-2.5 py-1.5 text-xs bg-zinc-900/80 text-zinc-300 border border-white/[0.08] rounded-md font-mono text-[11px]">
          <Layers className="w-3.5 h-3.5 text-zinc-400" />
          <span>Queue:</span>
          <span className="text-zinc-100 font-semibold">{status?.queueSize ?? 0}/{status?.queueCapacity ?? 0}</span>
        </div>

        {/* Pause/Resume Cluster Processing */}
        <button
          onClick={onTogglePause}
          disabled={!isRunning}
          className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-md border transition-colors shadow-sm ${
            isPaused
              ? 'bg-amber-500/10 text-amber-400 border-amber-500/30 hover:bg-amber-500/20'
              : 'bg-zinc-900 hover:bg-zinc-800 text-zinc-300 border-white/[0.08] hover:border-white/[0.15]'
          }`}
          title={isPaused ? 'Resume worker queue ingestion' : 'Pause worker queue ingestion'}
        >
          {isPaused ? <Play className="w-3.5 h-3.5" /> : <Pause className="w-3.5 h-3.5" />}
          <span>{isPaused ? 'Resume Ingestion' : 'Pause Engine'}</span>
        </button>

        {/* Clear Data / Reset */}
        {onClearData && (
          <button
            onClick={onClearData}
            className="flex items-center gap-1 px-2.5 py-1.5 text-xs font-medium bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 border border-rose-500/30 rounded-md transition-colors shadow-sm"
            title="Purge all jobs from database and clear queue"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span className="hidden sm:inline">Reset Cluster</span>
          </button>
        )}
      </div>
    </header>
  );
};
