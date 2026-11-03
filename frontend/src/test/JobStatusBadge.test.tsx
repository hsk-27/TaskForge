import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { StatusBadge } from '../components/common/StatusBadge';
import { JobStatus } from '../types';

describe('StatusBadge', () => {
  const statuses: JobStatus[] = ['QUEUED', 'RUNNING', 'RETRY_WAIT', 'SUCCEEDED', 'FAILED', 'CANCELLED'];

  statuses.forEach((status) => {
    it(`renders correct badge for status ${status}`, () => {
      render(<StatusBadge status={status} />);
      const badge = screen.getByTestId(`status-badge-${status.toLowerCase()}`);
      expect(badge).toBeInTheDocument();
      expect(badge).toHaveTextContent(status);
    });
  });
});
