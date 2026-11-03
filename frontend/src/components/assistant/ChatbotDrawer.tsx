import React, { useState, useEffect, useRef } from 'react';
import {
  AlertCircle,
  Ban,
  Bot,
  Check,
  Code2,
  Copy,
  GraduationCap,
  HelpCircle,
  Play,
  RotateCcw,
  Send,
  Sparkles,
  Trash2,
  User,
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
  const [persona, setPersona] = useState<AssistantPersona>('STUDENT');
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: 'welcome',
      sender: 'assistant',
      text: "### Welcome to ForgeBot Intelligent Assistant!\n\nI am your interactive engineering co-pilot for TaskForge. Select your mode above:\n- 🎓 **Student / Viva Prep:** Defend CS concepts, OS multithreading, and DBMS transaction decoupling.\n- 🛠️ **DevOps / Operator:** Inspect cluster health, queue load, and run one-click remediation actions.\n- 💻 **Developer:** API contracts, JSON request schemas, and concurrency internals.\n- 👤 **Plain English:** Simple analogies and status checks without technical jargon.\n\nHow can I assist you right now?",
      persona: 'STUDENT',
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
      const botMsg: ChatMessage = {
        id: `bot-${Date.now()}`,
        sender: 'assistant',
        text: res.reply,
        persona: res.persona,
        actions: res.actions,
        systemSnapshot: res.systemSnapshot,
        timestamp: new Date(res.timestamp),
      };
      setMessages((prev) => [...prev, botMsg]);
      audioTelemetry.jobCompleted();
      if (actionId && onActionExecuted) {
        onActionExecuted();
      }
    } catch (err: any) {
      const errorMsg: ChatMessage = {
        id: `err-${Date.now()}`,
        sender: 'assistant',
        text: `⚠️ **Error communicating with ForgeBot:** ${err.message}`,
        persona,
        timestamp: new Date(),
      };
      setMessages((prev) => [...prev, errorMsg]);
      audioTelemetry.jobFailed();
    } finally {
      setLoading(false);
    }
  };

  const handleExecuteAction = async (action: ChatAction) => {
    await handleSendMessage(`Run action: ${action.label}`, action.id);
  };

  const copyToClipboard = (code: string, id: string) => {
    navigator.clipboard.writeText(code);
    setCopiedCodeId(id);
    setTimeout(() => setCopiedCodeId(null), 2000);
  };

  // Basic markdown-to-elements renderer
  const renderFormattedText = (rawText: string, messageId: string) => {
    const parts = rawText.split(/(```[\s\S]*?```)/g);

    return parts.map((part, index) => {
      if (part.startsWith('```') && part.endsWith('```')) {
        const lines = part.slice(3, -3).trim().split('\n');
        const lang = lines[0].match(/^[a-zA-Z0-9]+$/) ? lines[0] : '';
        const code = lang ? lines.slice(1).join('\n') : lines.join('\n');
        const codeId = `${messageId}-${index}`;

        return (
          <div key={index} className="my-2 rounded-md overflow-hidden border border-slate-800 bg-[#090d16]">
            <div className="flex items-center justify-between px-3 py-1 bg-slate-900 border-b border-slate-800 text-[10px] text-slate-400 font-mono">
              <span>{lang || 'code'}</span>
              <button
                onClick={() => copyToClipboard(code, codeId)}
                className="flex items-center gap-1 hover:text-slate-200 transition-colors"
                title="Copy code snippet"
              >
                {copiedCodeId === codeId ? (
                  <>
                    <Check className="w-3 h-3 text-emerald-400" />
                    <span className="text-emerald-400">Copied</span>
                  </>
                ) : (
                  <>
                    <Copy className="w-3 h-3" />
                    <span>Copy</span>
                  </>
                )}
              </button>
            </div>
            <pre className="p-3 text-xs font-mono text-slate-200 overflow-x-auto whitespace-pre leading-relaxed">
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
                <div key={lIdx} className="flex items-start gap-1.5 pl-2 text-xs text-slate-200">
                  <span className="text-slate-400">•</span>
                  <span>{renderInlineBoldAndCode(line.replace('- ', ''))}</span>
                </div>
              );
            }
            if (line.trim() === '') {
              return <div key={lIdx} className="h-1" />;
            }
            return (
              <p key={lIdx} className="text-xs text-slate-200">
                {renderInlineBoldAndCode(line)}
              </p>
            );
          })}
        </div>
      );
    });
  };

  const renderInlineBoldAndCode = (text: string) => {
    // Replace **bold** and `code`
    const tokens = text.split(/(\*\*.*?\*\*|`.*?`)/g);
    return tokens.map((token, i) => {
      if (token.startsWith('**') && token.endsWith('**')) {
        return <strong key={i} className="font-semibold text-slate-100">{token.slice(2, -2)}</strong>;
      }
      if (token.startsWith('`') && token.endsWith('`')) {
        return (
          <code key={i} className="px-1.5 py-0.5 rounded bg-slate-900 border border-slate-800 text-amber-300 font-mono text-[11px]">
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
        className="fixed inset-0 bg-slate-950/70 backdrop-blur-sm transition-opacity"
        onClick={onClose}
      />

      {/* Drawer Container */}
      <div className="relative w-full max-w-lg bg-[#0e1422] border-l border-slate-800 shadow-2xl flex flex-col h-full z-10 animate-in slide-in-from-right duration-200">
        {/* Header */}
        <div className="p-3.5 border-b border-slate-800 bg-[#0a0f1a] flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-indigo-600/20 border border-indigo-500/40 flex items-center justify-center text-indigo-400">
              <Bot className="w-4 h-4" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-xs font-semibold text-slate-100">ForgeBot Assistant</h3>
                <span className="text-[10px] font-medium px-1.5 py-0.2 rounded bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                  Online
                </span>
              </div>
              <p className="text-[11px] text-slate-400">Interactive Co-Pilot & Viva Defense</p>
            </div>
          </div>

          <div className="flex items-center gap-1">
            <button
              onClick={() => setMessages([messages[0]])}
              className="p-1.5 text-slate-400 hover:text-slate-200 hover:bg-slate-800 rounded-md transition-colors"
              title="Clear chat history"
            >
              <RotateCcw className="w-4 h-4" />
            </button>
            <button
              onClick={onClose}
              className="p-1.5 text-slate-400 hover:text-slate-200 hover:bg-slate-800 rounded-md transition-colors"
              title="Close drawer"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Persona Mode Selector */}
        <div className="p-2.5 bg-[#101726] border-b border-slate-800/80">
          <div className="text-[10px] uppercase font-bold text-slate-400 mb-1.5 tracking-wider px-1">
            Audience Persona Mode
          </div>
          <div className="grid grid-cols-4 gap-1.5">
            <button
              onClick={() => setPersona('STUDENT')}
              className={`flex flex-col items-center justify-center py-2 px-1 rounded-md border text-[11px] font-medium transition-all ${
                persona === 'STUDENT'
                  ? 'bg-indigo-600/20 text-indigo-300 border-indigo-500/50 shadow-sm'
                  : 'bg-[#0a0f1a] text-slate-400 border-slate-800 hover:text-slate-200'
              }`}
              title="Academic Capstone & Viva Defense"
            >
              <GraduationCap className="w-3.5 h-3.5 mb-1 text-indigo-400" />
              <span>Viva Prep</span>
            </button>

            <button
              onClick={() => setPersona('OPERATOR')}
              className={`flex flex-col items-center justify-center py-2 px-1 rounded-md border text-[11px] font-medium transition-all ${
                persona === 'OPERATOR'
                  ? 'bg-indigo-600/20 text-indigo-300 border-indigo-500/50 shadow-sm'
                  : 'bg-[#0a0f1a] text-slate-400 border-slate-800 hover:text-slate-200'
              }`}
              title="DevOps & Cluster Health Actions"
            >
              <Wrench className="w-3.5 h-3.5 mb-1 text-amber-400" />
              <span>DevOps</span>
            </button>

            <button
              onClick={() => setPersona('DEVELOPER')}
              className={`flex flex-col items-center justify-center py-2 px-1 rounded-md border text-[11px] font-medium transition-all ${
                persona === 'DEVELOPER'
                  ? 'bg-indigo-600/20 text-indigo-300 border-indigo-500/50 shadow-sm'
                  : 'bg-[#0a0f1a] text-slate-400 border-slate-800 hover:text-slate-200'
              }`}
              title="REST APIs, Schemas & Concurrency"
            >
              <Code2 className="w-3.5 h-3.5 mb-1 text-sky-400" />
              <span>Developer</span>
            </button>

            <button
              onClick={() => setPersona('NOVICE')}
              className={`flex flex-col items-center justify-center py-2 px-1 rounded-md border text-[11px] font-medium transition-all ${
                persona === 'NOVICE'
                  ? 'bg-indigo-600/20 text-indigo-300 border-indigo-500/50 shadow-sm'
                  : 'bg-[#0a0f1a] text-slate-400 border-slate-800 hover:text-slate-200'
              }`}
              title="Plain English & Analogies"
            >
              <User className="w-3.5 h-3.5 mb-1 text-emerald-400" />
              <span>Plain English</span>
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
                    : 'bg-[#121a2c] text-slate-200 border border-slate-800 rounded-bl-none'
                }`}
              >
                {/* Persona Tag for Assistant */}
                {m.sender === 'assistant' && m.persona && (
                  <div className="flex items-center justify-between border-b border-slate-800/80 pb-1.5 mb-2 text-[10px] text-slate-400 font-mono">
                    <span className="flex items-center gap-1">
                      <Bot className="w-3 h-3 text-indigo-400" />
                      <span>Mode: {m.persona}</span>
                    </span>
                    <span>{m.timestamp.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                  </div>
                )}

                {/* Formatted Content */}
                <div>{renderFormattedText(m.text, m.id)}</div>

                {/* Live System Snapshot Card */}
                {m.systemSnapshot && (
                  <div className="mt-3 p-2 rounded bg-[#090d16] border border-slate-800 text-[11px] grid grid-cols-3 gap-2 text-center font-mono">
                    <div className="p-1 rounded bg-slate-900/60">
                      <div className="text-[10px] text-slate-400">Workers</div>
                      <div className="font-semibold text-indigo-300">
                        {m.systemSnapshot.activeWorkers ?? 0}/{m.systemSnapshot.totalWorkers ?? 0}
                      </div>
                    </div>
                    <div className="p-1 rounded bg-slate-900/60">
                      <div className="text-[10px] text-slate-400">Queue</div>
                      <div className="font-semibold text-sky-300">
                        {m.systemSnapshot.queueSize ?? 0}/{m.systemSnapshot.queueCapacity ?? 0}
                      </div>
                    </div>
                    <div className="p-1 rounded bg-slate-900/60">
                      <div className="text-[10px] text-slate-400">DLQ Failed</div>
                      <div className={`font-semibold ${Number(m.systemSnapshot.failedJobs) > 0 ? 'text-rose-400' : 'text-emerald-400'}`}>
                        {m.systemSnapshot.failedJobs ?? 0}
                      </div>
                    </div>
                  </div>
                )}

                {/* Interactive Action Pills */}
                {m.actions && m.actions.length > 0 && (
                  <div className="mt-3 pt-2 border-t border-slate-800/80 flex flex-wrap gap-1.5">
                    {m.actions.map((act) => (
                      <button
                        key={act.id}
                        onClick={() => handleExecuteAction(act)}
                        disabled={loading}
                        className="flex items-center gap-1.5 px-2.5 py-1 rounded bg-slate-800 hover:bg-indigo-600/30 text-indigo-300 border border-indigo-500/30 text-[11px] font-medium transition-colors shadow-sm disabled:opacity-50"
                        title={act.description}
                      >
                        <Zap className="w-3 h-3 text-indigo-400" />
                        <span>{act.label}</span>
                      </button>
                    ))}
                  </div>
                )}
              </div>
            </div>
          ))}

          {loading && (
            <div className="flex items-center gap-2 text-slate-400 text-xs py-2 px-1 font-mono">
              <Sparkles className="w-4 h-4 animate-spin text-indigo-400" />
              <span>ForgeBot is processing...</span>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>

        {/* Prompt Suggestions */}
        {suggestions.length > 0 && (
          <div className="px-3.5 py-2 border-t border-slate-800/80 bg-[#0a0f1a] overflow-x-auto">
            <div className="text-[10px] uppercase font-bold text-slate-400 mb-1 tracking-wider">
              Suggested Inquiries
            </div>
            <div className="flex gap-1.5 pb-1 flex-nowrap">
              {suggestions.map((s, idx) => (
                <button
                  key={idx}
                  onClick={() => handleSendMessage(s)}
                  disabled={loading}
                  className="whitespace-nowrap px-2.5 py-1 rounded-md bg-[#121a2c] hover:bg-slate-800 text-slate-300 border border-slate-800 text-[11px] transition-colors disabled:opacity-50 flex items-center gap-1"
                >
                  <HelpCircle className="w-3 h-3 text-indigo-400" />
                  <span>{s}</span>
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Input Bar */}
        <div className="p-3 border-t border-slate-800 bg-[#0a0f1a]">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              handleSendMessage();
            }}
            className="flex items-center gap-2"
          >
            <input
              ref={inputRef}
              type="text"
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              placeholder={
                persona === 'STUDENT'
                  ? 'Ask about Two-Phase Tx, HikariCP, backoff formula...'
                  : persona === 'OPERATOR'
                  ? 'Ask cluster health, replay DLQ, cancel pending...'
                  : persona === 'NOVICE'
                  ? 'Ask what this is in plain English...'
                  : 'Ask REST endpoints, JSON payload schemas...'
              }
              disabled={loading}
              className="flex-1 bg-[#121a2c] border border-slate-700/80 rounded-md px-3 py-2 text-xs text-slate-100 placeholder-slate-400 focus:outline-none focus:border-indigo-500 transition-colors disabled:opacity-50"
            />
            <button
              type="submit"
              disabled={loading || !inputText.trim()}
              className="px-3 py-2 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-40 text-white rounded-md text-xs font-medium transition-colors shadow-sm flex items-center gap-1"
            >
              <Send className="w-3.5 h-3.5" />
            </button>
          </form>
          <div className="mt-1.5 flex items-center justify-between text-[10px] text-slate-400 px-1 font-mono">
            <span>Shortcut: Ctrl + / toggles assistant</span>
            <span>Zero AI APIs • 100% Embedded</span>
          </div>
        </div>
      </div>
    </div>
  );
};
