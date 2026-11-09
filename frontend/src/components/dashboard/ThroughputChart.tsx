import React from 'react';
import {
  Bar,
  BarChart,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { EngineMetrics } from '../../types';

interface ThroughputChartProps {
  metrics: EngineMetrics | null;
}

export const ThroughputChart: React.FC<ThroughputChartProps> = ({ metrics }) => {
  const statusData = [
    { name: 'Succeeded', count: metrics?.succeededJobs ?? 0, color: '#10b981' },
    { name: 'Failed', count: metrics?.failedJobs ?? 0, color: '#f43f5e' },
    { name: 'Queued', count: metrics?.queuedJobs ?? 0, color: '#f59e0b' },
    { name: 'Running', count: metrics?.runningJobs ?? 0, color: '#0ea5e9' },
    { name: 'Retrying', count: metrics?.retryWaitJobs ?? 0, color: '#a855f7' },
    { name: 'Cancelled', count: metrics?.cancelledJobs ?? 0, color: '#71717a' },
  ].filter(d => d.count > 0);

  const failureData = (metrics?.topFailureReasons || []).map((f) => ({
    reason: f.reason.length > 25 ? f.reason.substring(0, 25) + '...' : f.reason,
    count: f.count,
  }));

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 font-sans">
      {/* State Distribution */}
      <div className="p-4 bg-[#121316] border border-white/[0.08] rounded-lg shadow-sm">
        <div className="flex items-center justify-between mb-3 border-b border-white/[0.08] pb-2.5">
          <span className="text-[11px] font-medium text-zinc-300 uppercase tracking-wider">Status SLA Distribution</span>
          <span className="text-[10px] text-zinc-500 font-mono">Real-time Volume</span>
        </div>

        {statusData.length === 0 ? (
          <div className="h-52 flex items-center justify-center text-zinc-500 text-xs">
            No active or historical job status data to display.
          </div>
        ) : (
          <div className="h-52">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={statusData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <XAxis dataKey="name" stroke="#71717a" fontSize={11} tickLine={false} />
                <YAxis stroke="#71717a" fontSize={11} tickLine={false} allowDecimals={false} />
                <Tooltip
                  contentStyle={{ backgroundColor: '#16171b', borderColor: 'rgba(255,255,255,0.1)', borderRadius: '6px' }}
                  labelStyle={{ color: '#f4f4f5', fontSize: '11px', fontFamily: 'monospace' }}
                  itemStyle={{ fontSize: '11px', fontFamily: 'monospace' }}
                />
                <Bar dataKey="count" radius={[3, 3, 0, 0]}>
                  {statusData.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={entry.color} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </div>

      {/* Top Failure Diagnostics */}
      <div className="p-4 bg-[#121316] border border-white/[0.08] rounded-lg shadow-sm">
        <div className="flex items-center justify-between mb-3 border-b border-white/[0.08] pb-2.5">
          <span className="text-[11px] font-medium text-zinc-300 uppercase tracking-wider">Failure Forensics (DLQ)</span>
          <span className="text-[10px] text-zinc-500 font-mono">Error Frequency</span>
        </div>

        {failureData.length === 0 ? (
          <div className="h-52 flex items-center justify-center text-zinc-500 text-xs">
            Zero execution failures recorded. All tasks healthy.
          </div>
        ) : (
          <div className="h-52">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={failureData} layout="vertical" margin={{ top: 10, right: 20, left: 10, bottom: 0 }}>
                <XAxis type="number" stroke="#71717a" fontSize={11} allowDecimals={false} />
                <YAxis type="category" dataKey="reason" stroke="#71717a" fontSize={11} width={130} />
                <Tooltip
                  contentStyle={{ backgroundColor: '#16171b', borderColor: 'rgba(255,255,255,0.1)', borderRadius: '6px' }}
                  labelStyle={{ color: '#f4f4f5', fontSize: '11px', fontFamily: 'monospace' }}
                  itemStyle={{ fontSize: '11px', fontFamily: 'monospace' }}
                />
                <Bar dataKey="count" fill="#f43f5e" radius={[0, 3, 3, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </div>
    </div>
  );
};
