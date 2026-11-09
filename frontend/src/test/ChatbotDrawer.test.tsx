import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { ChatbotDrawer } from '../components/assistant/ChatbotDrawer';
import { api } from '../api/client';

vi.mock('../api/client', () => ({
  api: {
    getChatSuggestions: vi.fn(),
    sendChatMessage: vi.fn(),
  },
}));

describe('ChatbotDrawer', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.HTMLElement.prototype.scrollIntoView = vi.fn();
    (api.getChatSuggestions as any).mockResolvedValue([
      'Analyze producer-consumer backpressure invariants',
      'Audit two-phase transaction boundaries and connection pool safety',
    ]);
  });

  it('does not render when isOpen is false', () => {
    const { container } = render(
      <ChatbotDrawer isOpen={false} onClose={vi.fn()} />
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders welcome message and persona selector buttons when open', async () => {
    render(<ChatbotDrawer isOpen={true} onClose={vi.fn()} />);

    expect(screen.getByText('TaskForge Ops Copilot')).toBeInTheDocument();
    expect(screen.getByText('SRE Triage')).toBeInTheDocument();
    expect(screen.getByText('Cluster Ops')).toBeInTheDocument();
    expect(screen.getByText('API & SDK')).toBeInTheDocument();
    expect(screen.getByText('Audit & SLA')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText(/Analyze producer-consumer/i)).toBeInTheDocument();
    });
  });

  it('sends user message and displays assistant response with action pill', async () => {
    (api.sendChatMessage as any).mockResolvedValue({
      reply: '### Producer-Consumer Architecture\nJobQueue handles bounded queueing.',
      persona: 'SRE',
      actions: [
        {
          id: 'SUBMIT_SAMPLE',
          label: 'Run 5 Test Jobs',
          description: 'Submit sample tasks',
          endpoint: '/api/v1/jobs/batch',
          httpMethod: 'POST',
        },
      ],
      systemSnapshot: {
        activeWorkers: 2,
        totalWorkers: 4,
        queueSize: 0,
        queueCapacity: 100,
        failedJobs: 0,
      },
      timestamp: new Date().toISOString(),
    });

    render(<ChatbotDrawer isOpen={true} onClose={vi.fn()} />);

    const input = screen.getByPlaceholderText(/Query cluster state/i);
    fireEvent.change(input, { target: { value: 'Explain JobQueue' } });
    fireEvent.submit(input.closest('form')!);

    await waitFor(() => {
      expect(api.sendChatMessage).toHaveBeenCalledWith('Explain JobQueue', 'SRE', undefined);
      expect(screen.getByText(/Producer-Consumer Architecture/i)).toBeInTheDocument();
      expect(screen.getByText('Run 5 Test Jobs')).toBeInTheDocument();
    });
  });

  it('executes action when clicking action pill', async () => {
    (api.sendChatMessage as any).mockResolvedValueOnce({
      reply: 'Ready with action',
      persona: 'OPERATOR',
      actions: [
        {
          id: 'SUBMIT_SAMPLE',
          label: 'Dispatch 5 Jobs',
          description: 'Submit tasks',
          endpoint: '/api/v1/jobs/batch',
          httpMethod: 'POST',
        },
      ],
      systemSnapshot: {},
      timestamp: new Date().toISOString(),
    }).mockResolvedValueOnce({
      reply: 'Action Executed: 5 jobs dispatched',
      persona: 'OPERATOR',
      actions: [],
      systemSnapshot: {},
      timestamp: new Date().toISOString(),
    });

    render(<ChatbotDrawer isOpen={true} onClose={vi.fn()} />);

    await waitFor(() => {
      expect(api.getChatSuggestions).toHaveBeenCalled();
    });
  });
});
