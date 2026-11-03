import { render, screen, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { CommandPaletteModal } from '../components/common/CommandPaletteModal';

describe('CommandPaletteModal', () => {
  it('does not render when closed', () => {
    const { container } = render(
      <CommandPaletteModal
        isOpen={false}
        onClose={vi.fn()}
        onDispatchBatch={vi.fn()}
        onTogglePause={vi.fn()}
        onToggleEco={vi.fn()}
        onScaleWorkers={vi.fn()}
        onClearData={vi.fn()}
        onReplayDlq={vi.fn()}
        onExport={vi.fn()}
        onNavigateTab={vi.fn()}
        onFilterChange={vi.fn()}
        isPaused={false}
        isEco={true}
      />
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders commands list when open and responds to search filter', () => {
    const onDispatchBatch = vi.fn();
    render(
      <CommandPaletteModal
        isOpen={true}
        onClose={vi.fn()}
        onDispatchBatch={onDispatchBatch}
        onTogglePause={vi.fn()}
        onToggleEco={vi.fn()}
        onScaleWorkers={vi.fn()}
        onClearData={vi.fn()}
        onReplayDlq={vi.fn()}
        onExport={vi.fn()}
        onNavigateTab={vi.fn()}
        onFilterChange={vi.fn()}
        isPaused={false}
        isEco={true}
      />
    );

    expect(screen.getByText('Command Palette')).toBeInTheDocument();
    expect(screen.getByText('Dispatch 10 Smoke Tasks')).toBeInTheDocument();

    const input = screen.getByPlaceholderText(/Type a command/i);
    fireEvent.change(input, { target: { value: 'burst 100' } });

    expect(screen.getByText('Launch 100 Job Concurrency Burst')).toBeInTheDocument();
    expect(screen.queryByText('Launch Sequential DAG Pipeline')).not.toBeInTheDocument();
  });
});
