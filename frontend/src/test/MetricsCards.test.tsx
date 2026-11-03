import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { MetricsCards } from '../components/dashboard/MetricsCards';
import { EngineMetrics, EngineStatus } from '../types';

describe('MetricsCards', () => {
  it('renders default zero states when metrics and status are null', () => {
    render(<MetricsCards metrics={null} status={null} />);
    expect(screen.getByText('Total Processed')).toBeInTheDocument();
    expect(screen.getByText('Succeeded')).toBeInTheDocument();
    expect(screen.getByText('Failed (Permanent)')).toBeInTheDocument();
    expect(screen.getByText('Worker Pool')).toBeInTheDocument();
    expect(screen.getByText('Avg Execution Time')).toBeInTheDocument();
    expect(screen.getByText('Throughput')).toBeInTheDocument();
  });

  it('renders live metrics correctly with clean format', () => {
    const mockMetrics: EngineMetrics = {
      totalJobs: 150,
      queuedJobs: 10,
      runningJobs: 4,
      succeededJobs: 120,
      failedJobs: 16,
      cancelledJobs: 0,
      retryWaitJobs: 2,
      averageExecutionDurationMs: 142.5,
      averageQueueWaitDurationMs: 22.0,
      successRatePercent: 88.2,
      failureRatePercent: 11.8,
      throughputPerMinute: 64.0,
      topFailureReasons: [],
    };

    const mockStatus: EngineStatus = {
      isRunning: true,
      isPaused: false,
      ecoMode: true,
      workerCount: 8,
      activeWorkers: 4,
      queueSize: 10,
      queueCapacity: 500,
      jvmUsedMemoryMb: 42,
      jvmMaxMemoryMb: 256,
      availableProcessors: 4,
      workers: [],
    };

    render(<MetricsCards metrics={mockMetrics} status={mockStatus} />);
    expect(screen.getByText('150')).toBeInTheDocument();
    expect(screen.getByText('120')).toBeInTheDocument();
    expect(screen.getByText('16')).toBeInTheDocument();
    expect(screen.getByText('4 / 8')).toBeInTheDocument();
    expect(screen.getByText('142.5 ms')).toBeInTheDocument();
    expect(screen.getByText('64/min')).toBeInTheDocument();
  });
});
