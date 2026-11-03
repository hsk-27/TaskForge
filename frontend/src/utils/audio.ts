/**
 * Zero-dependency Web Audio API sound synthesizer for brutalist telemetry chime effects.
 * Remains completely silent unless explicitly enabled by the operator.
 */

let audioCtx: AudioContext | null = null;
let audioEnabled = false;

export const audioTelemetry = {
  isEnabled(): boolean {
    return audioEnabled;
  },

  setEnabled(enabled: boolean) {
    audioEnabled = enabled;
  },

  toggle(): boolean {
    audioEnabled = !audioEnabled;
    if (audioEnabled) {
      this.playChirp(880, 0.04, 'sine');
    }
    return audioEnabled;
  },

  playChirp(freq: number, durationSec: number = 0.05, type: OscillatorType = 'sine', volume: number = 0.03) {
    if (!audioEnabled) return;
    try {
      const AudioCtxClass = window.AudioContext || (window as any).webkitAudioContext;
      if (!audioCtx && AudioCtxClass) {
        audioCtx = new AudioCtxClass();
      }
      if (!audioCtx) return;
      if (audioCtx.state === 'suspended') {
        audioCtx.resume();
      }

      const osc = audioCtx.createOscillator();
      const gain = audioCtx.createGain();

      osc.type = type;
      osc.frequency.setValueAtTime(freq, audioCtx.currentTime);

      gain.gain.setValueAtTime(volume, audioCtx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.0001, audioCtx.currentTime + durationSec);

      osc.connect(gain);
      gain.connect(audioCtx.destination);

      osc.start();
      osc.stop(audioCtx.currentTime + durationSec);
    } catch {
      // Ignore audio context autoplay constraints
    }
  },

  jobCompleted() {
    this.playChirp(1046.5, 0.04, 'sine', 0.02); // C6 subtle tick
  },

  jobFailed() {
    this.playChirp(220, 0.08, 'sawtooth', 0.025); // Low alert buzz
  },

  burstSubmitted() {
    this.playChirp(587.33, 0.06, 'triangle', 0.03); // D5
  },

  commandExecuted() {
    this.playChirp(1318.51, 0.03, 'sine', 0.02); // E6
  },
};
