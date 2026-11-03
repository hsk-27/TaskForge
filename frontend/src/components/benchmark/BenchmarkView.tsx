import React, { useState } from 'react';
import { BarChart3, Clock, Cpu, Play, Zap } from 'lucide-react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api } from '../../api/client';

interface BenchmarkResult {
  workerCount: number;
  jobCount: number;
  durationMs: number;
  throughputPerSec: number;
}

export const BenchmarkView: React.FC = () => {
  const [jobCount, setJobCount] = useState(30);
  const [workloadType, setWorkloadType] = useState<'SIMULATED_IO' | 'COMPUTE'>('SIMULATED_IO');
  const [running, setRunning] = useState(false);
  const [progressMsg, setProgressMsg] = useState<string | null>(null);
  const [results, setResults] = useState<BenchmarkResult[]>([]);

  const runComparativeBenchmark = async () => {
    setRunning(true);
    const benchmarkWorkerLevels = [2, 4, 8];
    const newResults: BenchmarkResult[] = [];

    try {
      for (const workers of benchmarkWorkerLevels) {
        setProgressMsg(`Scaling to ${workers} worker threads · executing ${jobCount} ${workloadType} tasks...`);
        // 1. Scale workers
        await api.updateEngineSettings({ workerCount: workers });
        await new Promise((r) => setTimeout(r, 400));

        // 2. Launch batch
        const start = performance.now();
        await api.createBatch({
          count: jobCount,
          prefix: `bench-w${workers}`,
          jobType: workloadType,
          simulatedDurationMs: workloadType === 'SIMULATED_IO' ? 60 : 20,
          failureRate: 0.0,
          maxAttempts: 1,
        });

        // 3. Poll until queue and active workers are drained (bounded with 30s safety deadline)
        let finished = false;
        const deadline = Date.now() + 30000;
        while (!finished && Date.now() < deadline) {
          await new Promise((r) => setTimeout(r, 200));
          const status = await api.getEngineStatus();
          if (status.queueSize === 0 && status.activeWorkers === 0) {
            finished = true;
          }
        }
        const elapsed = Math.round(performance.now() - start);
        const throughput = Math.round((jobCount / (elapsed / 1000)) * 10) / 10;
        newResults.push({
          workerCount: workers,
          jobCount,
          durationMs: elapsed,
          throughputPerSec: throughput,
        });
      }
      setResults(newResults);
      setProgressMsg('Benchmark suite completed successfully');
    } catch (err: any) {
      setProgressMsg(`Benchmark error: ${err.message}`);
    } finally {
      setRunning(false);
    }
  };

  return (
    <div className="space-y-4 font-sans select-none">
      {/* Header */}
      <div className="p-4 bg-[#101726] border border-slate-800 rounded-lg shadow-sm flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-base font-semibold text-slate-100">Scalability &amp; Concurrency Benchmark</h2>
            <span className="text-[11px] font-medium bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 px-2 py-0.5 rounded-full">
              Comparative Analysis
            </span>
          </div>
          <p className="text-xs text-slate-400 mt-1">
            Measures multi-threaded throughput and execution latency scaling across 2, 4, and 8 worker threads.
          </p>
        </div>

        <button
          disabled={running}
          onClick={runComparativeBenchmark}
          className="flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-medium rounded-md shadow-sm transition-all disabled:opacity-50"
        >
          <Play className="w-3.5 h-3.5" />
          <span>{running ? 'Running benchmark...' : 'Run Scalability Suite'}</span>
        </button>
      </div>

      {progressMsg && (
        <div className="p-3 bg-slate-800/80 border border-slate-700 text-slate-200 text-xs font-medium rounded-md shadow-sm">
          {progressMsg}
        </div>
      )}

      {/* Benchmark Workload Selector */}
      <div className="p-4 bg-[#101726] border border-slate-800 rounded-lg shadow-sm grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
        <div>
          <label className="block text-slate-300 mb-2 font-medium">Workload Type</label>
          <div className="flex gap-2">
            <button
              onClick={() => setWorkloadType('SIMULATED_IO')}
              className={`flex-1 py-2 px-3 border rounded-md text-xs font-medium transition-colors ${
                workloadType === 'SIMULATED_IO'
                  ? 'bg-indigo-600/15 text-indigo-400 border-indigo-500/40 shadow-sm'
                  : 'bg-[#0b0f17] text-slate-400 border-slate-700 hover:text-slate-200'
              }`}
            >
              Simulated I/O (I/O Bound)
            </button>
            <button
              onClick={() => setWorkloadType('COMPUTE')}
              className={`flex-1 py-2 px-3 border rounded-md text-xs font-medium transition-colors ${
                workloadType === 'COMPUTE'
                  ? 'bg-indigo-600/15 text-indigo-400 border-indigo-500/40 shadow-sm'
                  : 'bg-[#0b0f17] text-slate-400 border-slate-700 hover:text-slate-200'
              }`}
            >
              Compute (CPU Bound)
            </button>
          </div>
        </div>

        <div>
          <div className="flex justify-between text-slate-300 mb-2 text-xs font-medium">
            <span>Sample Tasks Per Worker Tier</span>
            <span className="font-mono text-indigo-400 font-semibold">{jobCount} tasks</span>
          </div>
          <input
            type="range"
            min="10"
            max="60"
            step="10"
            value={jobCount}
            onChange={(e) => setJobCount(Number(e.target.value))}
            className="w-full accent-indigo-500"
          />
        </div>
      </div>

      {/* Benchmark Comparison Charts or Empty State */}
      {results.length === 0 ? (
        <div className="p-8 bg-[#101726] border border-slate-800 rounded-lg text-center">
          <div className="text-slate-400 text-xs">
            Ready to benchmark. Click <span className="text-slate-200 font-medium">Run Scalability Suite</span> above to compare 2, 4, and 8 worker threads.
          </div>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <div className="p-4 bg-[#101726] border border-slate-800 rounded-lg shadow-sm">
            <div className="flex items-center justify-between mb-3 border-b border-slate-800/80 pb-2.5">
              <span className="text-xs font-semibold text-slate-200 uppercase tracking-wide">Elapsed Duration (ms)</span>
              <span className="text-[11px] text-slate-400 font-mono">Lower is Faster</span>
            </div>
            <div className="h-56">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={results} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="2 2" stroke="#1e293b" vertical={false} />
                  <XAxis dataKey="workerCount" stroke="#64748b" fontSize={11} tickFormatter={(v) => `${v} Workers`} />
                  <YAxis stroke="#64748b" fontSize={11} />
                  <Tooltip
                    contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: '6px' }}
                    labelStyle={{ color: '#f8fafc', fontSize: '12px' }}
                    formatter={(val) => [`${val} ms`, 'Elapsed']}
                  />
                  <Bar dataKey="durationMs" fill="#f59e0b" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>

          <div className="p-4 bg-[#101726] border border-slate-800 rounded-lg shadow-sm">
            <div className="flex items-center justify-between mb-3 border-b border-slate-800/80 pb-2.5">
              <span className="text-xs font-semibold text-slate-200 uppercase tracking-wide">Throughput (Tasks / Sec)</span>
              <span className="text-[11px] text-slate-400 font-mono">Higher is Better</span>
            </div>
            <div className="h-56">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={results} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="2 2" stroke="#1e293b" vertical={false} />
                  <XAxis dataKey="workerCount" stroke="#64748b" fontSize={11} tickFormatter={(v) => `${v} Workers`} />
                  <YAxis stroke="#64748b" fontSize={11} />
                  <Tooltip
                    contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: '6px' }}
                    labelStyle={{ color: '#f8fafc', fontSize: '12px' }}
                    formatter={(val) => [`${val} tasks/sec`, 'Throughput']}
                  />
                  <Bar dataKey="throughputPerSec" fill="#10b981" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>
        </div>
      )}

      {/* Systems Engineering Scalability Explanation */}
      <div className="p-4 bg-[#101726] border border-slate-800 rounded-lg text-xs text-slate-400 space-y-1.5">
        <div className="font-semibold text-slate-300 mb-1">Concurrency Properties</div>
        <p>
          · <strong>I/O-Bound Tasks:</strong> Asynchronous thread parking enables near-linear throughput scaling with worker thread density up to context-switching saturation.
        </p>
        <p>
          · <strong>CPU-Bound Tasks:</strong> Thread scaling beyond available physical CPU cores encounters diminishing returns governed by Amdahl's Law and cache contention.
        </p>
      </div>
    </div>
  );
};
