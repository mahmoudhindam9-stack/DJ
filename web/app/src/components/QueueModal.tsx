import React from 'react';
import { X, Trash2, Music, Download } from 'lucide-react';
import { AudioItem, Playlist, ThemeColors } from '../types';
import { getOnlineDownloadEndpoint } from '../services/OnlineMusicService';

interface QueueModalProps {
  queue: AudioItem[];
  currentSong: AudioItem | null;
  isPlaying: boolean;
  themeColors: ThemeColors;
  isArabic: boolean;
  onClose: () => void;
  onSelectSong: (song: AudioItem) => void;
  onRemoveFromQueue: (index: number) => void;
  onClearQueue: () => void;
  playlists: Playlist[];
  onAddToPlaylist: (playlistId: string, song: AudioItem) => void;
  onDownloadOnlineTracks: (songs: AudioItem[]) => void;
  downloadProgress: string | null;
  isDownloadingOnlineQueue: boolean;
}

export const QueueModal: React.FC<QueueModalProps> = ({
  queue,
  currentSong,
  isPlaying,
  themeColors,
  isArabic,
  onClose,
  onSelectSong,
  onRemoveFromQueue,
  onClearQueue,
  playlists,
  onAddToPlaylist,
  onDownloadOnlineTracks,
  downloadProgress,
  isDownloadingOnlineQueue,
}) => {
  return (
    <div
      id="queue-modal-overlay"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm select-none"
    >
      <div
        className="w-full max-w-lg max-h-[80vh] flex flex-col rounded-2xl border shadow-2xl overflow-hidden"
        style={{
          backgroundColor: themeColors.surface,
          borderColor: themeColors.border,
          color: themeColors.textPrimary,
        }}
      >
        {/* Header */}
        <div className="flex items-center justify-between p-4 border-b" style={{ borderColor: themeColors.border }}>
          <div className="flex items-center gap-2 min-w-0">
            <Music className="w-5 h-5 shrink-0" style={{ color: themeColors.primary }} />
            <h3 className="font-bold text-base whitespace-nowrap">{isArabic ? 'قائمة التشغيل الحالية' : 'Play Queue'} ({queue.length})</h3>
          </div>
          <div className="flex items-center justify-end gap-1.5 flex-wrap">
            {(() => {
              const onlineTracks = queue.filter((song) => Boolean(song.onlineSource || getOnlineDownloadEndpoint(song)));
              if (onlineTracks.length === 0) return null;
              return (
                <button
                  type="button"
                  disabled={isDownloadingOnlineQueue}
                  onClick={() => onDownloadOnlineTracks(onlineTracks)}
                  className="player-control-button px-2.5 py-1.5 rounded-lg flex items-center gap-1.5 text-xs font-bold disabled:opacity-50 transition-all shadow-sm"
                  title={isArabic ? 'تنزيل أغاني الأونلاين فقط في قائمة التشغيل تباعاً بضغطة تنزيل واحدة' : 'Download only online songs in the queue sequentially with a single click'}
                  aria-label={isArabic ? 'تنزيل أغاني الأونلاين في قائمة التشغيل' : 'Download online songs in play queue'}
                  style={{ color: '#ffffff', backgroundColor: themeColors.primary, borderColor: themeColors.primary }}
                >
                  <Download className="w-3.5 h-3.5" />
                  <span>
                    {isDownloadingOnlineQueue
                      ? (isArabic ? 'جاري التنزيل…' : 'Downloading…')
                      : (isArabic ? `تنزيل الأونلاين (${onlineTracks.length})` : `Download Online (${onlineTracks.length})`)}
                  </span>
                </button>
              );
            })()}
            {queue.length > 0 && (
              <button
                onClick={onClearQueue}
                className="p-2 rounded-lg hover:bg-red-500/20 text-red-400 transition-colors flex items-center gap-1 text-xs"
                title="Clear Queue"
              >
                <Trash2 className="w-4 h-4" />
                <span>{isArabic ? 'مسح' : 'Clear'}</span>
              </button>
            )}
            <button onClick={onClose} className="p-2 rounded-lg hover:bg-white/10 transition-colors">
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {downloadProgress && <div className="px-4 py-2 text-xs border-b" role="status" aria-live="polite" style={{ color: themeColors.primary, borderColor: themeColors.border, backgroundColor: themeColors.surfaceVariant }}>{downloadProgress}</div>}

        {/* Queue List */}
        <div className="flex-1 overflow-y-auto p-3 space-y-1.5">
          {queue.length === 0 ? (
            <div className="py-16 text-center text-sm" style={{ color: themeColors.textMuted }}>
              {isArabic ? 'قائمة التشغيل فارغة' : 'The queue is currently empty.'}
            </div>
          ) : (
            queue.map((song, index) => {
              const isCurrent = currentSong?.id === song.id;
              return (
                <div
                  key={`${song.id}_${index}`}
                  className={`dj-track-row flex items-center justify-between p-2.5 rounded-xl border transition-all ${
                    isCurrent ? 'font-bold' : 'hover:bg-white/5'
                  }`}
                  aria-current={isCurrent ? 'true' : undefined}
                  style={{
                    backgroundColor: isCurrent ? `${themeColors.primary}20` : themeColors.surfaceVariant,
                    borderColor: isCurrent ? themeColors.primary : 'transparent',
                    '--dj-track-accent': themeColors.primary,
                  } as React.CSSProperties}
                >
                  <div
                    className="flex items-center gap-3 min-w-0 flex-1 cursor-pointer"
                    onClick={() => onSelectSong(song)}
                  >
                    <span className="text-xs w-5 text-center font-mono opacity-60">
                      {index + 1}
                    </span>
                    <div className="min-w-0">
                      <h4 className="text-xs font-semibold truncate leading-tight">{song.title}</h4>
                      <p className="text-[11px] truncate leading-tight opacity-70 flex items-center gap-1.5">
                        <span className="truncate">{song.artist}</span>
                        {song.onlineSource && <span className="shrink-0 rounded border px-1 text-[9px]" style={{ color: themeColors.primary, borderColor: themeColors.border }}>{song.onlineSource}</span>}
                        {isCurrent && <span className="dj-now-playing-badge shrink-0">{isPlaying ? (isArabic ? 'يعمل الآن' : 'PLAYING') : (isArabic ? 'متوقف مؤقتًا' : 'PAUSED')}</span>}
                      </p>
                    </div>
                  </div>

                  <div className="flex items-center gap-1.5 ml-2 shrink-0">
                    {getOnlineDownloadEndpoint(song) && (
                      <button type="button" disabled={isDownloadingOnlineQueue} onClick={() => onDownloadOnlineTracks([song])} className="player-control-button p-1.5 rounded-lg disabled:opacity-50" title={isArabic ? 'تنزيل هذه الأغنية' : 'Download this song'} aria-label={isArabic ? `تنزيل ${song.title}` : `Download ${song.title}`} style={{ color: themeColors.primary, borderColor: themeColors.border }}>
                        <Download className="w-4 h-4" />
                      </button>
                    )}
                    <button type="button"
                      onClick={() => onRemoveFromQueue(index)}
                      className="p-1.5 rounded hover:bg-red-500/20 text-neutral-400 hover:text-red-400"
                      title="Remove"
                    >
                      <X className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
};
