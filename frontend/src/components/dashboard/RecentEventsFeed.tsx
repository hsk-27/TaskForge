import React from 'react';
import { JobEvent } from '../../types';

interface RecentEventsFeedProps {
  events: JobEvent[];
}

export const RecentEventsFeed: React.FC<RecentEventsFeedProps> = ({ events }) => {
  return (
    <div className="p-4 bg-[#121316] border border-white/[0.08] rounded-lg shadow-sm font-sans">
      <div className="flex items-center justify-between mb-3 border-b border-white/[0.08] pb-2.5">
        <div className="flex items-center gap-2">
          <span className="text-[11px] font-medium text-zinc-300 uppercase tracking-wider">Cluster Event Telemetry</span>
          <span className="text-[10px] text-zinc-500 font-mono">SSE Stream</span>
        </div>
        <div className="flex items-center gap-1.5 text-xs text-emerald-400 font-medium">
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 shadow-[0_0_6px_rgba(52,211,153,0.6)]" />
          <span className="font-mono text-[10px] tracking-wide">STREAM LIVE</span>
        </div>
      </div>

      <div className="space-y-1.5 max-h-72 overflow-y-auto text-xs pr-1">
        {events.length === 0 ? (
          <div className="py-8 text-center text-zinc-500 text-xs">
            No events recorded yet. Ingest a job or execute a workflow above to view real-time lifecycle logs.
          </div>
        ) : (
          events.map((event, idx) => {
            const time = new Date(event.createdAt).toLocaleTimeString();
            const isFailure = event.eventType.includes('FAIL') || event.eventType.includes('STALLED');
            const isSuccess = event.eventType.includes('SUCCEED');
            const isRetry = event.eventType.includes('RETRY');
            const isUnblocked = event.eventType.includes('UNBLOCKED');
            const isCancelled = event.eventType.includes('CANCEL');
            const isScheduled = event.eventType.includes('SCHEDULED');
            const isStarted = event.eventType.includes('STARTED');

            let badgeColor = 'bg-zinc-800 text-zinc-300 border-white/[0.08]';
            if (isFailure) badgeColor = 'bg-rose-500/10 text-rose-400 border-rose-500/30';
            else if (isSuccess) badgeColor = 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30';
            else if (isRetry) badgeColor = 'bg-purple-500/10 text-purple-400 border-purple-500/30';
            else if (isUnblocked) badgeColor = 'bg-sky-500/10 text-sky-400 border-sky-500/30';
            else if (isCancelled) badgeColor = 'bg-amber-500/10 text-amber-400 border-amber-500/30';
            else if (isScheduled) badgeColor = 'bg-indigo-500/10 text-indigo-400 border-indigo-500/30';
            else if (isStarted) badgeColor = 'bg-blue-500/10 text-blue-400 border-blue-500/30';

            return (
              <div
                key={event.id || idx}
                className="p-2.5 bg-[#16171b] border border-white/[0.05] rounded-md flex items-start gap-3 hover:border-white/[0.12] transition-colors"
              >
                <span className="text-zinc-500 font-mono text-[11px] shrink-0 pt-0.5">{time}</span>
                <span className={`px-2 py-0.5 border rounded text-[10px] font-mono font-medium shrink-0 ${badgeColor}`}>
                  {event.eventType}
                </span>
                <div className="flex-1 min-w-0">
                  <div className="text-zinc-200 text-xs truncate">{event.message}</div>
                  <div className="text-[11px] text-zinc-500 font-mono mt-0.5 flex gap-2">
                    <span>Job: {event.jobId.substring(0, 8)}</span>
                    {event.workerId && <span>· Worker: {event.workerId}</span>}
                  </div>
                </div>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
};
