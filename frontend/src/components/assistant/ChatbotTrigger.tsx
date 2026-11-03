import React from 'react';
import { Bot, Sparkles } from 'lucide-react';

interface ChatbotTriggerProps {
  isOpen: boolean;
  onClick: () => void;
}

export const ChatbotTrigger: React.FC<ChatbotTriggerProps> = ({ isOpen, onClick }) => {
  if (isOpen) return null;

  return (
    <button
      onClick={onClick}
      className="fixed bottom-5 right-5 z-40 flex items-center gap-2 px-3.5 py-2.5 rounded-full bg-indigo-600 hover:bg-indigo-500 text-white shadow-lg border border-indigo-400/40 transition-all transform hover:scale-105 active:scale-95 group font-sans select-none"
      title="Open ForgeBot Intelligent Assistant (Ctrl+/)"
    >
      <div className="relative flex items-center justify-center">
        <Bot className="w-4 h-4 text-white group-hover:rotate-12 transition-transform" />
        <span className="absolute -top-1 -right-1 w-2 h-2 rounded-full bg-emerald-400 ring-2 ring-indigo-600" />
      </div>
      <span className="text-xs font-semibold tracking-tight">Ask ForgeBot</span>
      <span className="hidden sm:inline-block px-1.5 py-0.5 rounded bg-indigo-700/80 text-[10px] font-mono text-indigo-200 border border-indigo-500/40">
        Ctrl + /
      </span>
    </button>
  );
};
