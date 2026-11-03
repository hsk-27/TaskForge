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
      'Explain the producer-consumer pattern in JobQueue',
      'Why is task execution outside @Transactional?',
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

    expect(screen.getByText('ForgeBot Assistant')).toBeInTheDocument();
    expect(screen.getByText('Viva Prep')).toBeInTheDocument();
    expect(screen.getByText('DevOps')).toBeInTheDocument();
    expect(screen.getByText('Developer')).toBeInTheDocument();
    expect(screen.getByText('Plain English')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText(/Explain the producer-consumer pattern/i)).toBeInTheDocument();
    });
  });

  it('sends user message and displays assistant response with action pill', async () => {
    (api.sendChatMessage as any).mockResolvedValue({
      reply: '### Producer-Consumer Architecture\nJobQueue handles bounded queueing.',
      persona: 'STUDENT',
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

    const input = screen.getByPlaceholderText(/Ask about Two-Phase Tx/i);
    fireEvent.change(input, { target: { value: 'Explain JobQueue' } });
    fireEvent.submit(input.closest('form')!);

    await waitFor(() => {
      expect(api.sendChatMessage).toHaveBeenCalledWith('Explain JobQueue', 'STUDENT', undefined);
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

    const onActionExecuted = vi.fn();
    render(<ChatbotDrawer isOpen={true} onClose={vi.fn()} onActionExecuted={onActionExecuted} />);

    // Switch to DevOps persona
    fireEvent.click(screen.getByText('DevOps'));

    const input = screen.getByPlaceholderText(/Ask cluster health/i);
    fireEvent.change(input, { target: { value: 'status' } });
    fireEvent.submit(input.closest('form')!);

    await waitFor(() => {
      expect(screen.getByText('Dispatch 5 Jobs')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('Dispatch 5 Jobs'));

    await waitFor(() => {
      expect(api.sendChatMessage).toHaveBeenCalledWith(
        'Run action: Dispatch 5 Jobs',
        'OPERATOR',
        'SUBMIT_SAMPLE'
      );
      expect(onActionExecuted).toHaveBeenCalled();
    });
  });
});
