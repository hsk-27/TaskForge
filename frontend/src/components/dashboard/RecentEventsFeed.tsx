import React from 'react';
import { JobEvent } from '../../types';

interface RecentEventsFeedProps {
  events: JobEvent[];
}

export const RecentEventsFeed: React.FC<RecentEventsFeedProps> = ({ events }) => {
  return (
    <div className="p-4 bg-[#101726] border border-slate-800 rounded-lg shadow-sm font-sans">
      <div className="flex items-center justify-between mb-3 border-b border-slate-800/80 pb-2.5">
        <div className="flex items-center gap-2">
          <span className="text-xs font-semibold text-slate-200 uppercase tracking-wide">Event Stream</span>
          <span className="text-[11px] text-slate-400 font-mono">SSE v1</span>
        </div>
        <div className="flex items-center gap-1.5 text-xs text-emerald-400 font-medium">
          <span className="w-2 h-2 rounded-full bg-emerald-400" />
          <span>Active</span>
        </div>
      </div>

      <div className="space-y-1.5 max-h-72 overflow-y-auto text-xs pr-1">
        {events.length === 0 ? (
          <div className="py-8 text-center text-slate-400 text-xs">
            No events recorded yet. Submit a task or launch a batch above to view real-time lifecycle logs.
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

            let badgeColor = 'bg-slate-800 text-slate-300 border-slate-700';
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
                className="p-2.5 bg-[#0b0f17] border border-slate-800/80 rounded-md flex items-start gap-3 hover:border-slate-700 transition-colors"
              >
                <span className="text-slate-400 font-mono text-[11px] shrink-0 pt-0.5">{time}</span>
                <span className={`px-2 py-0.5 border rounded text-[10px] font-mono font-medium shrink-0 ${badgeColor}`}>
                  {event.eventType}
                </span>
                <div className="flex-1 min-w-0">
                  <div className="text-slate-200 text-xs truncate">{event.message}</div>
                  <div className="text-[11px] text-slate-400 font-mono mt-0.5 flex gap-2">
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
