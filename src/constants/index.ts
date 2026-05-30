import type { ErrorType } from '../types';

export const STORAGE_KEY = 'voice-input-history';
export const MAX_HISTORY_ITEMS = 100;
export const DEFAULT_LANG = 'zh-CN';
export const SILENCE_TIMEOUT_MS = 3000;

export const ERROR_MESSAGES: Record<ErrorType, string> = {
  unsupported: '您的浏览器不支持语音识别功能，请使用 Chrome 或 Edge 浏览器。',
  'no-microphone': '未检测到麦克风设备，请检查麦克风连接。',
  'not-allowed': '麦克风权限已被拒绝，请在浏览器设置中允许麦克风访问。',
  network: '语音识别网络连接失败，请检查网络后重试。',
  'no-speech': '未检测到语音输入，请确认麦克风正常工作。',
  aborted: '',
  'audio-capture': '音频采集失败，请检查音频设备驱动。',
  'service-not-allowed': '语音识别服务不可用，请检查浏览器设置。',
  'storage-quota': '存储空间不足，请清理部分历史记录后重试。',
};

export const STATUS_LABELS: Record<string, string> = {
  idle: '点击开始录音',
  listening: '正在聆听...',
  processing: '正在处理...',
  error: '发生错误',
  unsupported: '浏览器不支持',
};
