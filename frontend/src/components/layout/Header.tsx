import React, { useState } from 'react';
import {
  Activity,
  Cpu,
  HardDrive,
  Layers,
  Leaf,
  Pause,
  Play,
  RotateCcw,
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
}

export const Header: React.FC<HeaderProps> = ({
  status,
  sseConnected,
  onTogglePause,
  onToggleEcoMode,
  onClearData,
  onOpenCommandPalette,
}) => {
  const isRunning = status?.isRunning ?? false;
  const isPaused = status?.isPaused ?? false;
  const ecoMode = status?.ecoMode ?? true;
  const [audioActive, setAudioActive] = useState(audioTelemetry.isEnabled());

  const handleToggleAudio = () => {
    const next = audioTelemetry.toggle();
    setAudioActive(next);
  };

  return (
    <header className="h-14 border-b border-slate-800 bg-[#0d1424] px-4 flex items-center justify-between sticky top-0 z-30 font-sans select-none shadow-sm">
      {/* Brand & Subsystem Identity */}
      <div className="flex items-center gap-3">
        <div className="w-8 h-8 rounded-lg bg-indigo-600/20 border border-indigo-500/30 flex items-center justify-center text-indigo-400 font-bold text-sm">
          TF
        </div>
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-sm font-semibold text-slate-100 tracking-tight">
              TaskForge
            </h1>
            <span className="text-[11px] font-medium px-2 py-0.5 rounded-full bg-slate-800 text-slate-300 border border-slate-700">
              v1.1
            </span>
          </div>
          <p className="text-[11px] text-slate-400 hidden sm:block">
            Asynchronous Job Execution &amp; Fault Recovery Engine
          </p>
        </div>
      </div>

      {/* Telemetry Readouts & System Controls */}
      <div className="flex items-center gap-2 flex-wrap">
        {/* Command Palette Trigger */}
        {onOpenCommandPalette && (
          <button
            onClick={onOpenCommandPalette}
            className="flex items-center gap-1.5 px-2.5 py-1 text-xs font-medium bg-slate-800/80 hover:bg-slate-700 text-slate-200 border border-slate-700 rounded-md transition-colors shadow-sm"
            title="Open Command Palette (Ctrl+K / Cmd+K)"
          >
            <Terminal className="w-3.5 h-3.5 text-indigo-400" />
            <span className="hidden sm:inline font-mono text-[11px] text-slate-400">Ctrl+K</span>
          </button>
        )}

        {/* Audio Telemetry Toggle */}
        <button
          onClick={handleToggleAudio}
          className={`flex items-center gap-1.5 px-2.5 py-1 text-xs font-medium border rounded-md transition-colors ${
            audioActive
              ? 'bg-indigo-500/10 text-indigo-300 border-indigo-500/30 hover:bg-indigo-500/20'
              : 'bg-slate-900/60 text-slate-400 border-slate-800 hover:text-slate-200 hover:bg-slate-800'
          }`}
          title={audioActive ? 'Audio telemetry active (click to mute)' : 'Enable audio chimes for state changes'}
        >
          {audioActive ? <Volume2 className="w-3.5 h-3.5" /> : <VolumeX className="w-3.5 h-3.5" />}
          <span className="hidden md:inline text-[11px]">{audioActive ? 'Chimes On' : 'Muted'}</span>
        </button>

        {/* SSE Liveness */}
        <div
          className={`flex items-center gap-1.5 px-2.5 py-1 text-xs rounded-md border font-medium ${
            sseConnected
              ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20'
              : 'bg-slate-900 text-slate-400 border-slate-800'
          }`}
          title={sseConnected ? 'Connected to live event stream' : 'Event stream disconnected'}
        >
          <span className={`w-2 h-2 rounded-full ${sseConnected ? 'bg-emerald-400' : 'bg-slate-500'}`} />
          <span className="text-[11px]">{sseConnected ? 'Live' : 'Offline'}</span>
        </div>

        {/* RAM Footprint */}
        <div
          className="hidden md:flex items-center gap-1.5 px-2.5 py-1 text-xs bg-slate-900/60 border border-slate-800 text-slate-300 rounded-md font-mono text-[11px]"
          title={`JVM Heap: ${status?.jvmUsedMemoryMb ?? 0} MB used / ${status?.jvmMaxMemoryMb ?? 0} MB maximum`}
        >
          <HardDrive className="w-3.5 h-3.5 text-slate-400" />
          <span>RAM: {status?.jvmUsedMemoryMb ?? 0} / {status?.jvmMaxMemoryMb ?? 0} MB</span>
        </div>

        {/* Worker Allocation */}
        <div className="flex items-center gap-1.5 px-2.5 py-1 text-xs bg-slate-900/60 border border-slate-800 text-slate-300 rounded-md font-mono text-[11px]">
          <Cpu className="w-3.5 h-3.5 text-amber-400" />
          <span>Workers: {status?.activeWorkers ?? 0}/{status?.workerCount ?? 0}</span>
        </div>

        {/* Queue Depth */}
        <div className="flex items-center gap-1.5 px-2.5 py-1 text-xs bg-slate-900/60 border border-slate-800 text-slate-300 rounded-md font-mono text-[11px]">
          <Layers className="w-3.5 h-3.5 text-indigo-400" />
          <span>Queue: {status?.queueSize ?? 0}/{status?.queueCapacity ?? 0}</span>
        </div>

        {/* Eco-Safe Throttle Switch */}
        {onToggleEcoMode && (
          <button
            onClick={onToggleEcoMode}
            className={`flex items-center gap-1.5 px-2.5 py-1 text-xs font-medium border rounded-md transition-colors ${
              ecoMode
                ? 'bg-emerald-500/10 text-emerald-300 border-emerald-500/30 hover:bg-emerald-500/20'
                : 'bg-slate-900 text-slate-400 border-slate-800 hover:text-slate-200'
            }`}
            title="Toggle PC-Safe low-resource CPU throttling"
          >
            <Leaf className="w-3.5 h-3.5" />
            <span className="hidden sm:inline text-[11px]">Eco: {ecoMode ? 'On' : 'Off'}</span>
          </button>
        )}

        {/* Database Purge / Reset */}
        {onClearData && (
          <button
            onClick={() => {
              if (window.confirm('Reset engine state: Clear queue buffers, attempts, and historical events?')) {
                onClearData();
              }
            }}
            className="flex items-center gap-1.5 px-2.5 py-1 text-xs font-medium bg-slate-900/60 hover:bg-rose-950/40 hover:text-rose-300 hover:border-rose-500/30 text-slate-400 border border-slate-800 rounded-md transition-colors"
            title="Clear all job records and reset execution queue"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span className="hidden lg:inline text-[11px]">Reset</span>
          </button>
        )}

        {/* Engine Dispatch Control (Pause/Resume) */}
        <button
          onClick={onTogglePause}
          disabled={!isRunning}
          className={`flex items-center gap-1.5 px-3 py-1 text-xs font-medium rounded-md border transition-colors shadow-sm ${
            isPaused
              ? 'bg-amber-500 text-slate-950 border-amber-400 hover:bg-amber-400 font-semibold'
              : 'bg-slate-800 hover:bg-slate-700 text-slate-200 border-slate-700'
          }`}
          title={isPaused ? 'Resume job dispatching' : 'Pause job dispatching'}
        >
          {isPaused ? <Play className="w-3.5 h-3.5" /> : <Pause className="w-3.5 h-3.5" />}
          <span>{isPaused ? 'Resume' : 'Pause'}</span>
        </button>
      </div>
    </header>
  );
};
