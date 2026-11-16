import React, { useEffect, useRef, useState } from 'react';
import {
  Play,
  Pause,
  SkipBack,
  SkipForward,
  Rewind,
  FastForward,
  Repeat,
  Repeat1,
  Shuffle,
  Volume2,
  VolumeX,
  ListMusic,
  Maximize2,
  Sliders,
  Music2,
} from 'lucide-react';
import { AudioItem, PlaybackMode, ThemeColors } from '../types';

interface MiniPlayerProps {
  currentSong: AudioItem | null;
  isPlaying: boolean;
  currentTimeMs: number;
  durationMs: number;
  playbackMode: PlaybackMode;
  volume: number;
  crossfadeDurationSeconds: number;
  themeColors: ThemeColors;
  onCrossfadeDurationChange: (seconds: number) => void;
  onPlayPause: () => void;
  onNext: () => void;
  onPrev: () => void;
  onSeek: (ms: number) => void;
  onSeekBy: (deltaMs: number) => void;
  onVolumeChange: (vol: number) => void;
  onTogglePlaybackMode: () => void;
  onOpenQueue: () => void;
  onOpenNowPlaying: () => void;
  onOpenEqualizer: () => void;
  isArabic: boolean;
}

export const MiniPlayer: React.FC<MiniPlayerProps> = ({
  currentSong,
  isPlaying,
  currentTimeMs,
  durationMs,
  playbackMode,
  volume,
  crossfadeDurationSeconds,
  themeColors,
  onCrossfadeDurationChange,
  onPlayPause,
  onNext,
  onPrev,
  onSeek,
  onSeekBy,
  onVolumeChange,
  onTogglePlaybackMode,
  onOpenQueue,
  onOpenNowPlaying,
  onOpenEqualizer,
  isArabic,
}) => {
  const formatTime = (ms: number) => {
    if (!ms || isNaN(ms)) return '0:00';
    const totalSec = Math.floor(ms / 1000);
    const m = Math.floor(totalSec / 60);
    const s = totalSec % 60;
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  };

  const [actionFeedback, setActionFeedback] = useState('');
  const feedbackTimerRef = useRef<number | null>(null);
  const lastObservedStateRef = useRef({
    songId: currentSong?.id ?? null,
    isPlaying,
    playbackMode,
  });

  const primaryTextColor = (() => {
    const match = /^#?([0-9a-f]{6})$/i.exec(themeColors.primary);
    if (!match) return '#FFFFFF';
    const hex = match[1];
    const channels = [0, 2, 4].map((offset) => parseInt(hex.slice(offset, offset + 2), 16) / 255);
    const linear = channels.map((channel) => channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4);
    const luminance = 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2];
    return luminance > 0.48 ? '#111827' : '#FFFFFF';
  })();
  const seekHoldDelayRef = useRef<number | null>(null);
  const seekHoldIntervalRef = useRef<number | null>(null);
  const announceAction = (message: string) => {
    setActionFeedback(message);
    if (feedbackTimerRef.current !== null) window.clearTimeout(feedbackTimerRef.current);
    feedbackTimerRef.current = window.setTimeout(() => setActionFeedback(''), 1900);
  };
  const stopSeekHold = () => {
    if (seekHoldDelayRef.current !== null) { window.clearTimeout(seekHoldDelayRef.current); seekHoldDelayRef.current = null; }
    if (seekHoldIntervalRef.current !== null) { window.clearInterval(seekHoldIntervalRef.current); seekHoldIntervalRef.current = null; }
  };
  const startSeekHold = (deltaMs: number, event: React.PointerEvent<HTMLButtonElement>) => {
    if (event.button !== 0) return;
    event.preventDefault();
    stopSeekHold();
    try { event.currentTarget.setPointerCapture(event.pointerId); } catch { /* Older WebViews may not support pointer capture. */ }
    onSeekBy(deltaMs);
    announceAction(deltaMs < 0 ? (isArabic ? 'تم الرجوع 10 ثوانٍ' : 'Skipped back 10 seconds') : (isArabic ? 'تم التقديم 10 ثوانٍ' : 'Skipped forward 10 seconds'));
    seekHoldDelayRef.current = window.setTimeout(() => { seekHoldIntervalRef.current = window.setInterval(() => onSeekBy(deltaMs), 250); }, 380);
  };
  useEffect(() => {
    const previous = lastObservedStateRef.current;
    const nextSongId = currentSong?.id ?? null;

    if (previous.songId !== nextSongId && currentSong) {
      announceAction((isArabic ? 'الأغنية الحالية: ' : 'Now playing: ') + currentSong.title);
    } else if (previous.isPlaying !== isPlaying) {
      announceAction(isPlaying
        ? (isArabic ? 'تم تأكيد بدء التشغيل' : 'Playback started')
        : (isArabic ? 'تم تأكيد الإيقاف المؤقت' : 'Playback paused'));
    } else if (previous.playbackMode !== playbackMode) {
      const modeLabel = playbackMode === 'SHUFFLE'
        ? (isArabic ? 'التشغيل العشوائي' : 'Shuffle')
        : playbackMode === 'REPEAT_ONE'
          ? (isArabic ? 'تكرار الأغنية' : 'Repeat one')
          : playbackMode === 'REPEAT_ALL'
            ? (isArabic ? 'تكرار القائمة' : 'Repeat all')
            : (isArabic ? 'التشغيل العادي' : 'Normal playback');
      announceAction((isArabic ? 'الوضع الحالي: ' : 'Mode confirmed: ') + modeLabel);
    }

    lastObservedStateRef.current = {
      songId: nextSongId,
      isPlaying,
      playbackMode,
    };
  }, [currentSong?.id, currentSong?.title, isPlaying, playbackMode, isArabic]);

  useEffect(() => () => {
    if (feedbackTimerRef.current !== null) window.clearTimeout(feedbackTimerRef.current);
    stopSeekHold();
  }, []);

  return (
    <footer
      id="dj-mini-player"
      className="border-t px-4 py-2 select-none flex flex-col gap-1 z-40 shrink-0"
      style={{
        backgroundColor: themeColors.surface,
        borderColor: themeColors.border,
        color: themeColors.textPrimary,
        '--dj-ui-primary': themeColors.primary,
        '--dj-ui-border': themeColors.border,
        '--dj-ui-secondary': themeColors.textSecondary,
        '--dj-ui-muted': themeColors.textMuted,
        '--dj-ui-surface': themeColors.surface,
        '--dj-ui-surface-variant': themeColors.surfaceVariant,
      } as React.CSSProperties}
    >
      {/* Top Scrubber */}
      <div className="flex items-center gap-2 w-full text-[11px]">
        <span style={{ color: themeColors.textMuted }} className="w-10 text-right">
          {formatTime(currentTimeMs)}
        </span>
        <div className="relative flex-1 flex items-center group">
          <input
            type="range"
            min={0}
            max={durationMs || 100}
            value={currentTimeMs || 0}
            onChange={(e) => { onSeek(Number(e.target.value)); announceAction(isArabic ? 'تم تغيير موضع التشغيل' : 'Playback position changed'); }}
            className="w-full h-1.5 bg-neutral-700 rounded-lg appearance-none cursor-pointer group-hover:h-2 transition-all"
            style={{
              accentColor: themeColors.primary,
            }}
          />
        </div>
        <span style={{ color: themeColors.textMuted }} className="w-10">
          {formatTime(durationMs)}
        </span>
      </div>

      {/* Automatic overlap duration */}
      <div className="flex items-center gap-2.5 rounded-lg border px-2.5 py-1" style={{ backgroundColor: themeColors.surfaceVariant, borderColor: themeColors.border }}>
        <div className="text-[10px] font-black tracking-wider shrink-0" style={{ color: themeColors.primary }}>{isArabic ? 'تداخل الأغاني' : 'CROSSFADE'}</div>
        <span className="text-[11px] font-mono font-bold tabular-nums w-6 text-right">{crossfadeDurationSeconds}s</span>
        <input
          type="range" min={0} max={15} step={1} value={crossfadeDurationSeconds}
          onChange={(event) => { const seconds = Number(event.target.value); onCrossfadeDurationChange(seconds); announceAction(isArabic ? `مدة التداخل: ${seconds} ثانية` : `Crossfade duration: ${seconds}s`); }}
          aria-label={isArabic ? 'مدة التداخل بين الأغاني من صفر إلى 15 ثانية' : 'Automatic song crossfade duration, zero to 15 seconds'}
          title={isArabic ? 'صفر = بدون تداخل، 15 = أطول تداخل' : '0 = no overlap; 15 = longest overlap'}
          className="flex-1 h-1.5 rounded-full appearance-none cursor-ew-resize"
          style={{ accentColor: themeColors.primary, backgroundColor: themeColors.border }}
        />
        <div className="flex items-center justify-between gap-1.5 text-[9px] opacity-60 w-8"><span>0</span><span>15</span></div>
      </div>

      {/* Controls Row */}
      <div className="flex items-center justify-between gap-4">
        {/* Track Details (Left) */}
        <div
          className="flex items-center gap-3 min-w-0 w-1/4 cursor-pointer"
          onClick={onOpenNowPlaying}
          title="Click to expand Now Playing"
        >
          <div
            className="w-11 h-11 rounded-lg overflow-hidden flex items-center justify-center shrink-0 border"
            style={{
              backgroundColor: themeColors.surfaceVariant,
              borderColor: themeColors.border,
            }}
          >
            {currentSong?.coverUri ? (
              <img
                src={currentSong.coverUri}
                alt={currentSong.title}
                className="w-full h-full object-cover"
              />
            ) : (
              <Music2 className="w-5 h-5" style={{ color: themeColors.primary }} />
            )}
          </div>
          <div className="min-w-0">
            <h4 className="text-xs font-bold truncate leading-tight">
              {currentSong ? currentSong.title : isArabic ? 'لا يوجد تشغيل' : 'No track selected'}
            </h4>
            <p className="text-[11px] truncate leading-tight flex items-center gap-2" style={{ color: themeColors.textMuted }}>
              <span className="truncate">{currentSong ? currentSong.artist : isArabic ? 'اختر أغنية للبدء' : 'Select a song to start'}</span>
              {currentSong && <span className="dj-now-playing-badge shrink-0" data-playing={isPlaying ? 'true' : 'false'} style={{ '--dj-track-accent': isPlaying ? themeColors.tertiary : themeColors.textMuted } as React.CSSProperties}>{isPlaying && <span className="dj-playing-indicator mr-1"><i /><i /><i /></span>}{isPlaying ? (isArabic ? 'يعمل الآن' : 'PLAYING') : (isArabic ? 'متوقف مؤقتًا' : 'PAUSED')}</span>}
            </p>
          </div>
        </div>

        {/* Playback controls with distinct hit targets and visible feedback. */}
        <div className="flex items-center gap-1.5 sm:gap-2" aria-label={isArabic ? 'أدوات تشغيل الأغاني' : 'Playback controls'}>
          <button type="button" onClick={() => { onTogglePlaybackMode(); announceAction(isArabic ? 'جارٍ تحديث وضع التشغيل…' : 'Updating playback mode…'); }} className="player-control-button player-mode-button p-2 rounded-lg" title={playbackMode === 'SHUFFLE' ? (isArabic ? 'العشوائي مفعّل' : 'Shuffle active') : playbackMode === 'REPEAT_ONE' ? (isArabic ? 'تكرار الأغنية' : 'Repeat one') : playbackMode === 'REPEAT_ALL' ? (isArabic ? 'تكرار القائمة' : 'Repeat all') : (isArabic ? 'تشغيل عادي' : 'Normal playback')} aria-label={isArabic ? 'تغيير وضع التشغيل' : 'Change playback mode'} aria-pressed={playbackMode !== 'NORMAL'} data-active={playbackMode !== 'NORMAL' ? 'true' : 'false'} style={{ color: playbackMode !== 'NORMAL' ? themeColors.primary : themeColors.textMuted }}>
            {playbackMode === 'REPEAT_ONE' ? <Repeat1 className="w-4 h-4" /> : playbackMode === 'SHUFFLE' ? <Shuffle className="w-4 h-4" /> : <Repeat className="w-4 h-4" />}
          </button>
          <button type="button" onPointerDown={(event) => startSeekHold(-10000, event)} onPointerUp={stopSeekHold} onPointerCancel={stopSeekHold} onLostPointerCapture={stopSeekHold} onClick={(event) => { if (event.detail === 0) { onSeekBy(-10000); announceAction(isArabic ? 'تم الرجوع 10 ثوانٍ' : 'Skipped back 10 seconds'); } }} onContextMenu={(event) => event.preventDefault()} disabled={!currentSong} className="player-control-button relative p-2 rounded-lg" title={isArabic ? 'الرجوع 10 ثوانٍ — اضغط مطولًا للاستمرار' : 'Back 10 seconds — hold to keep seeking'} aria-label={isArabic ? 'الرجوع 10 ثوانٍ، اضغط مطولًا للاستمرار' : 'Seek backward 10 seconds; hold to continue'} style={{ color: themeColors.textSecondary }}>
            <Rewind className="w-4 h-4" /><span className="absolute -bottom-0.5 right-0.5 text-[8px] font-black leading-none">10</span>
          </button>
          <button type="button" onClick={() => { onPrev(); announceAction(isArabic ? 'جارٍ الانتقال للأغنية السابقة…' : 'Switching to previous track…'); }} disabled={!currentSong} className="player-control-button p-2 rounded-lg" title={isArabic ? 'الأغنية السابقة' : 'Previous track'} aria-label={isArabic ? 'الأغنية السابقة' : 'Previous track'} style={{ color: themeColors.textSecondary }}><SkipBack className="w-4 h-4" /></button>
          <button type="button" onClick={() => { onPlayPause(); announceAction(isPlaying ? (isArabic ? 'جارٍ إيقاف التشغيل مؤقتًا…' : 'Pausing playback…') : (isArabic ? 'جارٍ بدء التشغيل…' : 'Starting playback…')); }} disabled={!currentSong} data-playing={isPlaying ? 'true' : 'false'} className="player-control-button player-main-play-button p-2.5 rounded-xl" style={{ backgroundColor: themeColors.primary, borderColor: themeColors.primary, color: primaryTextColor }} title={isPlaying ? (isArabic ? 'إيقاف مؤقت' : 'Pause') : (isArabic ? 'تشغيل' : 'Play')} aria-label={isPlaying ? (isArabic ? 'إيقاف مؤقت' : 'Pause') : (isArabic ? 'تشغيل' : 'Play')} aria-pressed={isPlaying}>
            {isPlaying ? <Pause className="w-5 h-5 fill-current" /> : <Play className="w-5 h-5 fill-current ml-0.5" />}
          </button>
          <button type="button" onClick={() => { onNext(); announceAction(isArabic ? 'جارٍ الانتقال للأغنية التالية…' : 'Switching to next track…'); }} disabled={!currentSong} className="player-control-button p-2 rounded-lg" title={isArabic ? 'الأغنية التالية' : 'Next track'} aria-label={isArabic ? 'الأغنية التالية' : 'Next track'} style={{ color: themeColors.textSecondary }}><SkipForward className="w-4 h-4" /></button>
          <button type="button" onPointerDown={(event) => startSeekHold(10000, event)} onPointerUp={stopSeekHold} onPointerCancel={stopSeekHold} onLostPointerCapture={stopSeekHold} onClick={(event) => { if (event.detail === 0) { onSeekBy(10000); announceAction(isArabic ? 'تم التقديم 10 ثوانٍ' : 'Skipped forward 10 seconds'); } }} onContextMenu={(event) => event.preventDefault()} disabled={!currentSong} className="player-control-button relative p-2 rounded-lg" title={isArabic ? 'التقديم 10 ثوانٍ — اضغط مطولًا للاستمرار' : 'Forward 10 seconds — hold to keep seeking'} aria-label={isArabic ? 'التقديم 10 ثوانٍ، اضغط مطولًا للاستمرار' : 'Seek forward 10 seconds; hold to continue'} style={{ color: themeColors.textSecondary }}>
            <FastForward className="w-4 h-4" /><span className="absolute -bottom-0.5 right-0.5 text-[8px] font-black leading-none">10</span>
          </button>
        </div>

        {/* Right Tools: EQ, Queue, Volume, Fullscreen Expand */}
        <div className="flex items-center gap-2.5 w-1/4 justify-end">
          <button
            onClick={() => { onOpenEqualizer(); announceAction(isArabic ? 'تم فتح موازن الصوت' : 'Equalizer opened'); }}
            className="player-control-button p-1.5 rounded-lg"
            title="Master Equalizer"
          >
            <Sliders className="w-4 h-4" style={{ color: themeColors.textSecondary }} />
          </button>

          <button
            onClick={() => { onOpenQueue(); announceAction(isArabic ? 'تم فتح قائمة التشغيل' : 'Play queue opened'); }}
            className="player-control-button p-1.5 rounded-lg relative"
            title="Play Queue"
          >
            <ListMusic className="w-4 h-4" style={{ color: themeColors.textSecondary }} />
          </button>

          {/* Volume slider */}
          <div className="hidden sm:flex items-center gap-1.5">
            <button
              onClick={() => { onVolumeChange(volume === 0 ? 0.8 : 0); announceAction(volume === 0 ? (isArabic ? 'تم إلغاء كتم الصوت' : 'Sound unmuted') : (isArabic ? 'تم كتم الصوت' : 'Sound muted')); }}
              className="player-control-button p-1 rounded-lg"
              title={volume === 0 ? 'Unmute' : 'Mute'}
            >
              {volume === 0 ? (
                <VolumeX className="w-4 h-4" style={{ color: themeColors.textMuted }} />
              ) : (
                <Volume2 className="w-4 h-4" style={{ color: themeColors.textSecondary }} />
              )}
            </button>
            <input
              type="range"
              min={0}
              max={1}
              step={0.01}
              value={volume}
              onChange={(e) => { onVolumeChange(Number(e.target.value)); announceAction(isArabic ? 'تم تغيير مستوى الصوت' : 'Volume changed'); }}
              className="w-18 h-1 bg-neutral-700 rounded-lg appearance-none cursor-pointer"
              style={{
                accentColor: themeColors.primary,
              }}
            />
          </div>

          <button
            onClick={() => { onOpenNowPlaying(); announceAction(isArabic ? 'تم فتح المشغل الموسع' : 'Expanded player opened'); }}
            className="player-control-button p-1.5 rounded-lg"
            title="Expand Full View"
          >
            <Maximize2 className="w-4 h-4" style={{ color: themeColors.textSecondary }} />
          </button>
        </div>
      </div>
      <div className="player-action-feedback" data-has-feedback={actionFeedback ? 'true' : 'false'} role="status" aria-live="polite" aria-atomic="true" style={{ color: themeColors.textSecondary, borderColor: actionFeedback ? themeColors.primary : themeColors.border, backgroundColor: actionFeedback ? themeColors.surfaceVariant : 'transparent' }}>
        <span className="player-action-status-dot" data-playing={isPlaying ? 'true' : 'false'} aria-hidden="true" />
        <span className="truncate">{actionFeedback || (currentSong
          ? (isPlaying ? (isArabic ? 'يعمل الآن' : 'PLAYING') : (isArabic ? 'متوقف مؤقتًا' : 'PAUSED')) + ' · ' + currentSong.title
          : (isArabic ? 'جاهز — اختر أغنية للتشغيل' : 'Ready — select a track'))}</span>
      </div>
    </footer>
  );
};
