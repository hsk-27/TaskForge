import React, { useState, useEffect, useRef } from 'react';
import {
  AlertCircle,
  Ban,
  Bot,
  Check,
  Code2,
  Copy,
  FileCheck,
  Layers,
  Play,
  RotateCcw,
  Send,
  ShieldCheck,
  Sparkles,
  Trash2,
  Wrench,
  X,
  Zap,
} from 'lucide-react';
import { AssistantPersona, ChatAction, ChatMessage } from '../../types';
import { api } from '../../api/client';
import { audioTelemetry } from '../../utils/audio';

interface ChatbotDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  onActionExecuted?: () => void;
}

export const ChatbotDrawer: React.FC<ChatbotDrawerProps> = ({
  isOpen,
  onClose,
  onActionExecuted,
}) => {
  const [persona, setPersona] = useState<AssistantPersona>('SRE');
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: 'welcome',
      sender: 'assistant',
      text: "### TaskForge Ops Copilot (Production SRE Assistant)\n\nI am your cluster operations and reliability assistant. Select an operational mode above:\n- 🛡️ **SRE Triage:** Root-cause diagnostics, Dead Letter Queue (DLQ) forensics, and error budgets.\n- ⚡ **Cluster Ops:** Worker fleet scaling, queue backpressure management, and remediation actions.\n- 💻 **API & SDK:** Endpoint contracts, JSON payload schemas, and cURL snippets.\n- 📋 **Audit & SLA:** Immutable execution attempts, transaction isolation, and event audit trails.\n\nQuery cluster health, triage failures, or execute remediation actions below.",
      persona: 'SRE',
      timestamp: new Date(),
    },
  ]);
  const [inputText, setInputText] = useState('');
  const [loading, setLoading] = useState(false);
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [copiedCodeId, setCopiedCodeId] = useState<string | null>(null);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // Load suggestions when persona changes
  useEffect(() => {
    if (!isOpen) return;
    let mounted = true;
    api.getChatSuggestions(persona)
      .then((s) => {
        if (mounted) setSuggestions(s);
      })
      .catch((err) => console.error('Failed to load suggestions:', err));
    return () => {
      mounted = false;
    };
  }, [persona, isOpen]);

  // Scroll to bottom on new message
  useEffect(() => {
    if (isOpen) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages, isOpen]);

  // Focus input on drawer open
  useEffect(() => {
    if (isOpen) {
      setTimeout(() => inputRef.current?.focus(), 150);
    }
  }, [isOpen]);

  const handleSendMessage = async (textToSend?: string, actionId?: string) => {
    const text = (textToSend ?? inputText).trim();
    if (!text && !actionId) return;

    if (!actionId) {
      const userMsg: ChatMessage = {
        id: `user-${Date.now()}`,
        sender: 'user',
        text,
        persona,
        timestamp: new Date(),
      };
      setMessages((prev) => [...prev, userMsg]);
      setInputText('');
    }

    setLoading(true);
    try {
      const res = await api.sendChatMessage(text || 'Execute directive', persona, actionId);
      audioTelemetry.commandExecuted();

      const botMsg: ChatMessage = {
        id: `bot-${Date.now()}`,
        sender: 'assistant',
        text: res.reply,
        persona: res.persona,
        actions: res.actions,
        systemSnapshot: res.systemSnapshot,
        timestamp: new Date(),
      };
      setMessages((prev) => [...prev, botMsg]);

      if (actionId && onActionExecuted) {
        onActionExecuted();
      }
    } catch (err: any) {
      const errorMsg: ChatMessage = {
        id: `err-${Date.now()}`,
        sender: 'assistant',
        text: `**Operational Error:** Failed to process copilot directive: ${err.message}`,
        persona,
        timestamp: new Date(),
      };
      setMessages((prev) => [...prev, errorMsg]);
    } finally {
      setLoading(false);
    }
  };

  const handleExecuteAction = async (action: ChatAction) => {
    await handleSendMessage(`Execute action: ${action.label}`, action.id);
  };

  const copyToClipboard = (code: string, id: string) => {
    navigator.clipboard.writeText(code);
    setCopiedCodeId(id);
    setTimeout(() => setCopiedCodeId(null), 2000);
  };

  const renderMessageContent = (text: string) => {
    const parts = text.split(/(```[\s\S]*?```)/g);

    return parts.map((part, index) => {
      if (part.startsWith('```') && part.endsWith('```')) {
        const firstLineEnd = part.indexOf('\n');
        const lang = part.slice(3, firstLineEnd).trim();
        const code = part.slice(firstLineEnd + 1, -3);
        const codeId = `code-${index}-${Date.now()}`;

        return (
          <div key={index} className="my-2 rounded-md overflow-hidden border border-white/[0.08] bg-[#08090a]">
            <div className="flex items-center justify-between px-3 py-1 bg-[#141518] border-b border-white/[0.06] text-[10px] text-zinc-400 font-mono">
              <span>{lang || 'code'}</span>
              <button
                onClick={() => copyToClipboard(code, codeId)}
                className="flex items-center gap-1 hover:text-zinc-200 transition-colors"
                title="Copy code snippet"
              >
                {copiedCodeId === codeId ? (
                  <>
                    <Check className="w-3 h-3 text-emerald-400" />
                    <span className="text-emerald-400 font-mono">Copied</span>
                  </>
                ) : (
                  <>
                    <Copy className="w-3 h-3" />
                    <span>Copy</span>
                  </>
                )}
              </button>
            </div>
            <pre className="p-3 text-xs font-mono text-zinc-200 overflow-x-auto whitespace-pre leading-relaxed">
              <code>{code}</code>
            </pre>
          </div>
        );
      }

      // Render markdown text lines
      const lines = part.split('\n');
      return (
        <div key={index} className="space-y-1.5 leading-relaxed">
          {lines.map((line, lIdx) => {
            if (line.startsWith('### ')) {
              return (
                <h4 key={lIdx} className="text-xs font-semibold text-indigo-300 mt-2 mb-1 flex items-center gap-1.5">
                  <Sparkles className="w-3.5 h-3.5 text-indigo-400" />
                  <span>{line.replace('### ', '')}</span>
                </h4>
              );
            }
            if (line.startsWith('- ')) {
              return (
                <div key={lIdx} className="flex items-start gap-1.5 pl-2 text-xs text-zinc-300">
                  <span className="text-zinc-500">•</span>
                  <span>{renderInlineBoldAndCode(line.replace('- ', ''))}</span>
                </div>
              );
            }
            if (line.trim() === '') {
              return <div key={lIdx} className="h-1" />;
            }
            return (
              <p key={lIdx} className="text-xs text-zinc-300">
                {renderInlineBoldAndCode(line)}
              </p>
            );
          })}
        </div>
      );
    });
  };

  const renderInlineBoldAndCode = (text: string) => {
    const tokens = text.split(/(\*\*.*?\*\*|`.*?`)/g);
    return tokens.map((token, i) => {
      if (token.startsWith('**') && token.endsWith('**')) {
        return <strong key={i} className="font-semibold text-zinc-100">{token.slice(2, -2)}</strong>;
      }
      if (token.startsWith('`') && token.endsWith('`')) {
        return (
          <code key={i} className="px-1.5 py-0.5 rounded bg-[#08090a] border border-white/[0.08] text-amber-300 font-mono text-[11px]">
            {token.slice(1, -1)}
          </code>
        );
      }
      return token;
    });
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 overflow-hidden font-sans select-none flex justify-end">
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-black/70 backdrop-blur-sm transition-opacity"
        onClick={onClose}
      />

      {/* Drawer Container */}
      <div className="relative w-full max-w-lg bg-[#0f1012] border-l border-white/[0.08] shadow-2xl flex flex-col h-full z-10 animate-in slide-in-from-right duration-200">
        {/* Header */}
        <div className="p-3.5 border-b border-white/[0.08] bg-[#08090a] flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-indigo-500/10 border border-indigo-500/30 flex items-center justify-center text-indigo-400">
              <Sparkles className="w-4 h-4" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-xs font-semibold text-zinc-100">TaskForge Ops Copilot</h3>
                <span className="text-[10px] font-mono font-medium px-1.5 py-0.2 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                  ONLINE
                </span>
              </div>
              <p className="text-[11px] text-zinc-500">Cluster SRE &amp; Reliability Copilot</p>
            </div>
          </div>

          <div className="flex items-center gap-1">
            <button
              onClick={() => setMessages([messages[0]])}
              className="p-1.5 text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800 rounded-md transition-colors"
              title="Reset conversation"
            >
              <RotateCcw className="w-4 h-4" />
            </button>
            <button
              onClick={onClose}
              className="p-1.5 text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800 rounded-md transition-colors"
              title="Close drawer"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Persona Mode Selector */}
        <div className="p-2.5 bg-[#141518] border-b border-white/[0.08]">
          <div className="text-[10px] uppercase font-mono font-semibold text-zinc-500 mb-1.5 tracking-wider px-1">
            Operational Role Mode
          </div>
          <div className="grid grid-cols-4 gap-1.5">
            <button
              onClick={() => setPersona('SRE')}
              className={`flex flex-col items-center justify-center py-2 px-1 rounded-md border text-[11px] font-medium transition-all ${
                persona === 'SRE' || persona === 'STUDENT'
                  ? 'bg-indigo-600/20 text-indigo-300 border-indigo-500/40 shadow-sm'
                  : 'bg-[#08090a] text-zinc-400 border-white/[0.06] hover:text-zinc-200 hover:bg-zinc-900'
              }`}
              title="Site Reliability & Incident Triage"
            >
              <ShieldCheck className="w-3.5 h-3.5 mb-1 text-indigo-400" />
              <span>SRE Triage</span>
            </button>

            <button
              onClick={() => setPersona('OPERATOR')}
              className={`flex flex-col items-center justify-center py-2 px-1 rounded-md border text-[11px] font-medium transition-all ${
                persona === 'OPERATOR'
                  ? 'bg-indigo-600/20 text-indigo-300 border-indigo-500/40 shadow-sm'
                  : 'bg-[#08090a] text-zinc-400 border-white/[0.06] hover:text-zinc-200 hover:bg-zinc-900'
              }`}
              title="Cluster Operations & Remediations"
            >
              <Wrench className="w-3.5 h-3.5 mb-1 text-amber-400" />
              <span>Cluster Ops</span>
            </button>

            <button
              onClick={() => setPersona('DEVELOPER')}
              className={`flex flex-col items-center justify-center py-2 px-1 rounded-md border text-[11px] font-medium transition-all ${
                persona === 'DEVELOPER'
                  ? 'bg-indigo-600/20 text-indigo-300 border-indigo-500/40 shadow-sm'
                  : 'bg-[#08090a] text-zinc-400 border-white/[0.06] hover:text-zinc-200 hover:bg-zinc-900'
              }`}
              title="REST APIs, Schemas & Webhooks"
            >
              <Code2 className="w-3.5 h-3.5 mb-1 text-sky-400" />
              <span>API &amp; SDK</span>
            </button>

            <button
              onClick={() => setPersona('AUDITOR')}
              className={`flex flex-col items-center justify-center py-2 px-1 rounded-md border text-[11px] font-medium transition-all ${
                persona === 'AUDITOR' || persona === 'NOVICE'
                  ? 'bg-indigo-600/20 text-indigo-300 border-indigo-500/40 shadow-sm'
                  : 'bg-[#08090a] text-zinc-400 border-white/[0.06] hover:text-zinc-200 hover:bg-zinc-900'
              }`}
              title="Forensic Audit Trails & SLA Compliance"
            >
              <FileCheck className="w-3.5 h-3.5 mb-1 text-emerald-400" />
              <span>Audit &amp; SLA</span>
            </button>
          </div>
        </div>

        {/* Message Thread */}
        <div className="flex-1 overflow-y-auto p-4 space-y-4">
          {messages.map((m) => (
            <div
              key={m.id}
              className={`flex flex-col ${m.sender === 'user' ? 'items-end' : 'items-start'}`}
            >
              <div
                className={`max-w-[92%] rounded-lg p-3 text-xs shadow-sm ${
                  m.sender === 'user'
                    ? 'bg-indigo-600 text-white rounded-br-none'
                    : 'bg-[#141518] text-zinc-200 border border-white/[0.06] rounded-bl-none'
                }`}
              >
                {renderMessageContent(m.text)}

                {/* Remediation Action Pills */}
                {m.actions && m.actions.length > 0 && (
                  <div className="mt-3 pt-2.5 border-t border-white/[0.08] flex flex-wrap gap-1.5">
                    {m.actions.map((act) => (
                      <button
                        key={act.id}
                        onClick={() => handleExecuteAction(act)}
                        disabled={loading}
                        className="px-2.5 py-1 bg-indigo-500/10 hover:bg-indigo-500/20 text-indigo-300 border border-indigo-500/30 rounded text-[11px] font-medium transition-colors flex items-center gap-1 disabled:opacity-50"
                        title={act.description}
                      >
                        <Zap className="w-3 h-3 text-indigo-400" />
                        <span>{act.label}</span>
                      </button>
                    ))}
                  </div>
                )}
              </div>

              <div className="text-[10px] text-zinc-600 font-mono mt-1 px-1">
                {new Date(m.timestamp).toLocaleTimeString()}
              </div>
            </div>
          ))}

          {loading && (
            <div className="flex items-center gap-2 p-3 bg-[#141518] border border-white/[0.06] rounded-lg text-xs text-zinc-400 w-fit">
              <Sparkles className="w-3.5 h-3.5 text-indigo-400 animate-spin" />
              <span>Querying cluster telemetry &amp; generating forensic directive...</span>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>

        {/* Suggested Queries */}
        {suggestions.length > 0 && (
          <div className="p-2.5 border-t border-white/[0.06] bg-[#0c0d10] flex gap-1.5 overflow-x-auto text-[11px] select-none">
            {suggestions.map((sug, i) => (
              <button
                key={i}
                onClick={() => handleSendMessage(sug)}
                disabled={loading}
                className="px-2 py-1 rounded bg-[#141518] hover:bg-zinc-800 text-zinc-300 hover:text-zinc-100 border border-white/[0.06] whitespace-nowrap text-xs transition-colors disabled:opacity-50"
              >
                {sug}
              </button>
            ))}
          </div>
        )}

        {/* Input Bar */}
        <form onSubmit={(e) => { e.preventDefault(); handleSendMessage(); }} className="p-3 border-t border-white/[0.08] bg-[#08090a] flex items-center gap-2">
          <input
            ref={inputRef}
            type="text"
            value={inputText}
            onChange={(e) => setInputText(e.target.value)}
            disabled={loading}
            placeholder="Query cluster state, triage failures, or generate cURL snippets..."
            className="flex-1 px-3 py-2 bg-[#141518] border border-white/[0.08] rounded-md text-xs text-zinc-100 placeholder-zinc-500 focus:outline-none focus:border-indigo-500 transition-colors font-sans"
          />
          <button
            type="submit"
            disabled={loading || !inputText.trim()}
            className="p-2 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-40 text-white rounded-md transition-colors"
            title="Execute query"
          >
            <Send className="w-3.5 h-3.5" />
          </button>
        </form>
      </div>
    </div>
  );
};
