import React from 'react';
import { JobStatus } from '../../types';

interface StatusBadgeProps {
  status: JobStatus;
  size?: 'sm' | 'md';
}

export const StatusBadge: React.FC<StatusBadgeProps> = ({ status, size = 'md' }) => {
  const sizeClasses = size === 'sm' ? 'px-2 py-0.5 text-[11px]' : 'px-2.5 py-1 text-xs';

  const config: Record<JobStatus, { dot: string; text: string; bg: string; border: string }> = {
    QUEUED: {
      dot: 'bg-amber-400',
      text: 'text-amber-300',
      bg: 'bg-amber-500/10',
      border: 'border-amber-500/30',
    },
    RUNNING: {
      dot: 'bg-sky-400',
      text: 'text-sky-300',
      bg: 'bg-sky-500/10',
      border: 'border-sky-500/30',
    },
    RETRY_WAIT: {
      dot: 'bg-purple-400',
      text: 'text-purple-300',
      bg: 'bg-purple-500/10',
      border: 'border-purple-500/30',
    },
    SUCCEEDED: {
      dot: 'bg-emerald-400',
      text: 'text-emerald-300',
      bg: 'bg-emerald-500/10',
      border: 'border-emerald-500/30',
    },
    FAILED: {
      dot: 'bg-rose-400',
      text: 'text-rose-300',
      bg: 'bg-rose-500/10',
      border: 'border-rose-500/30',
    },
    CANCELLED: {
      dot: 'bg-slate-400',
      text: 'text-slate-400',
      bg: 'bg-slate-500/10',
      border: 'border-slate-500/20',
    },
  };

  const style = config[status] || {
    dot: 'bg-slate-400',
    text: 'text-slate-300',
    bg: 'bg-slate-800',
    border: 'border-slate-700',
  };

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border ${style.border} ${style.bg} ${style.text} ${sizeClasses} font-mono font-medium tracking-wide select-none`}
      data-testid={`status-badge-${status.toLowerCase()}`}
    >
      <span className={`w-1.5 h-1.5 rounded-full ${style.dot} shrink-0`} />
      <span>{status}</span>
    </span>
  );
};
