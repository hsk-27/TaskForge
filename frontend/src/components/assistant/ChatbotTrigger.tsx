import React from 'react';
import { Sparkles } from 'lucide-react';

interface ChatbotTriggerProps {
  isOpen: boolean;
  onClick: () => void;
}

export const ChatbotTrigger: React.FC<ChatbotTriggerProps> = ({ isOpen, onClick }) => {
  if (isOpen) return null;

  return (
    <button
      onClick={onClick}
      className="fixed bottom-5 right-5 z-40 flex items-center gap-2 px-3 py-2 rounded-lg bg-[#141518]/95 hover:bg-zinc-800 text-zinc-100 shadow-2xl border border-white/[0.12] hover:border-white/[0.2] transition-all transform hover:-translate-y-0.5 active:translate-y-0 font-sans select-none backdrop-blur-md"
      title="Open TaskForge Ops Copilot (Ctrl+/)"
    >
      <div className="relative flex items-center justify-center">
        <Sparkles className="w-3.5 h-3.5 text-indigo-400" />
        <span className="absolute -top-1 -right-1 w-1.5 h-1.5 rounded-full bg-emerald-400 shadow-[0_0_6px_rgba(52,211,153,0.8)]" />
      </div>
      <span className="text-xs font-medium tracking-tight">Ops Copilot</span>
      <span className="hidden sm:inline-block px-1.5 py-0.5 rounded bg-white/[0.06] text-[10px] font-mono text-zinc-400 border border-white/[0.08]">
        Ctrl + /
      </span>
    </button>
  );
};
