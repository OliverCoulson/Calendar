// ---- 录音记录 ----

export interface Recording {
  id: string;
  text: string;
  timestamp: number;
  duration: number;
  language: string;
}

// ---- 识别状态机 ----

export type RecognitionStatus =
  | 'idle'
  | 'listening'
  | 'processing'
  | 'error'
  | 'unsupported';

export type ErrorType =
  | 'unsupported'
  | 'no-microphone'
  | 'not-allowed'
  | 'network'
  | 'no-speech'
  | 'aborted'
  | 'audio-capture'
  | 'service-not-allowed'
  | 'storage-quota';

export interface RecognitionError {
  type: ErrorType;
  message: string;
  originalError?: unknown;
}

// ---- Hook 类型 ----

export interface UseSpeechRecognitionOptions {
  lang?: string;
  continuous?: boolean;
  interimResults?: boolean;
  maxAlternatives?: number;
  silenceTimeout?: number;
}

export interface UseSpeechRecognitionReturn {
  status: RecognitionStatus;
  transcript: string;
  interimTranscript: string;
  error: RecognitionError | null;
  isSupported: boolean;
  start: () => void;
  stop: () => void;
  abort: () => void;
  reset: () => void;
}

export interface UseAudioVisualizerOptions {
  fftSize?: 32 | 64 | 128 | 256 | 512 | 1024 | 2048;
  smoothingTimeConstant?: number;
  minDecibels?: number;
  maxDecibels?: number;
}

export interface UseAudioVisualizerReturn {
  frequencyData: Uint8Array;
  isActive: boolean;
  error: string | null;
  start: () => Promise<void>;
  stop: () => void;
}

export interface UseHistoryOptions {
  storageKey?: string;
  maxItems?: number;
}

export interface UseHistoryReturn {
  recordings: Recording[];
  filteredRecordings: Recording[];
  searchQuery: string;
  sortOrder: 'newest' | 'oldest';
  setSearchQuery: (q: string) => void;
  setSortOrder: (order: 'newest' | 'oldest') => void;
  addRecording: (recording: Omit<Recording, 'id'>) => Recording;
  deleteRecording: (id: string) => void;
  clearAll: () => void;
  exportAsJSON: () => void;
  exportAsTXT: () => void;
  search: (query: string) => Recording[];
}

export interface UseClipboardReturn {
  copy: (text: string) => Promise<boolean>;
  copied: boolean;
  error: string | null;
}
