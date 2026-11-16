import React, { useEffect, useRef, useState } from 'react';
import {
  Film,
  Upload,
  Link2,
  Play,
  Pause,
  Volume2,
  VolumeX,
  Maximize2,
  RotateCcw,
  AlertCircle
} from 'lucide-react';
import { cloudSyncService } from '../../services/CloudSyncService';

interface Props {
  isAr: boolean;
  onPauseAllAudio: () => void;
}

export const VideoPlayerScreen: React.FC<Props> = ({ isAr, onPauseAllAudio }) => {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const containerRef = useRef<HTMLDivElement | null>(null);

  const [videoUrl, setVideoUrl] = useState<string>('');
  const [urlInput, setUrlInput] = useState<string>('');
  const [videoTitle, setVideoTitle] = useState<string>(
    isAr ? 'اختر ملف فيديو أو أدخل رابط البث' : 'Select a video file or enter a stream URL'
  );
  const [isPlaying, setIsPlaying] = useState(false);
  const [isMuted, setIsMuted] = useState(false);
  const [volume, setVolume] = useState(0.9);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [playbackRate, setPlaybackRate] = useState(1);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  // Pause video immediately if a remote device (Android) claims active audio output
  useEffect(() => {
    const handleRemoteClaim = () => {
      if (videoRef.current && !videoRef.current.paused) {
        videoRef.current.pause();
        setIsPlaying(false);
      }
    };
    window.addEventListener('dj-remote-audio-claimed', handleRemoteClaim);
    return () => {
      window.removeEventListener('dj-remote-audio-claimed', handleRemoteClaim);
    };
  }, []);

  const handlePickFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setErrorMsg(null);
    const objectUrl = URL.createObjectURL(file);
    setVideoUrl(objectUrl);
    setVideoTitle(file.name.replace(/\.[^/.]+$/, ''));
  };

  const handleLoadUrl = () => {
    const trimmed = urlInput.trim();
    if (!trimmed) return;
    setErrorMsg(null);
    setVideoUrl(trimmed);
    setVideoTitle(trimmed.split('/').pop() || 'Video Stream');
  };

  const togglePlay = () => {
    const el = videoRef.current;
    if (!el || !videoUrl) return;
    if (el.paused) {
      onPauseAllAudio();
      el.play()
        .then(() => {
          setIsPlaying(true);
          cloudSyncService.claimWebAudioOutput({
            isPlaying: true,
            currentTrack: {
              id: `video_${videoTitle}`,
              title: videoTitle,
              artist: 'Video Player',
              album: 'Video',
              duration: duration * 1000,
              uri: videoUrl,
              addedDate: Date.now()
            },
            positionMs: currentTime * 1000
          });
        })
        .catch(() => {
          setErrorMsg(
            isAr
              ? 'تعذر تشغيل هذا الفيديو. تأكد من صيغة الملف أو الرابط.'
              : 'Unable to play this video source. Verify the format or URL.'
          );
        });
    } else {
      el.pause();
      setIsPlaying(false);
      cloudSyncService.claimWebAudioOutput({
        isPlaying: false
      });
    }
  };

  const formatSec = (sec: number) => {
    if (!Number.isFinite(sec) || sec < 0) return '00:00';
    const m = Math.floor(sec / 60);
    const s = Math.floor(sec % 60);
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  };

  return (
    <div
      ref={containerRef}
      className="flex-1 h-full flex flex-col bg-slate-950/95 p-4 gap-3 overflow-hidden"
    >
      {/* Top Toolbar */}
      <div className="flex flex-wrap items-center justify-between gap-3 bg-slate-900/90 border border-slate-800/80 rounded-2xl px-4 py-2.5 shrink-0">
        <div className="flex items-center gap-2.5">
          <div className="w-9 h-9 rounded-xl bg-blue-600/20 border border-blue-500/30 flex items-center justify-center text-blue-400">
            <Film className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-sm font-bold text-white leading-tight">
              {isAr ? 'مشغل الفيديو المتكامل' : 'Studio Video Player'}
            </h2>
            <p className="text-[11px] text-slate-400 truncate max-w-md">{videoTitle}</p>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <input
            ref={fileInputRef}
            type="file"
            accept="video/*,.mp4,.mkv,.webm,.mov,.avi,.m4v"
            className="hidden"
            onChange={handlePickFile}
          />
          <button
            onClick={() => fileInputRef.current?.click()}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold transition"
          >
            <Upload className="w-3.5 h-3.5" />
            <span>{isAr ? 'فتح ملف فيديو' : 'Open Video File'}</span>
          </button>

          <div className="flex items-center bg-slate-950 border border-slate-800 rounded-xl px-2.5 py-1 gap-1.5">
            <Link2 className="w-3.5 h-3.5 text-slate-400" />
            <input
              type="url"
              value={urlInput}
              onChange={(e) => setUrlInput(e.target.value)}
              placeholder={isAr ? 'رابط فيديو MP4 / WebM / HLS...' : 'Direct MP4 / WebM URL...'}
              className="bg-transparent text-xs text-white focus:outline-none w-44"
            />
            <button
              onClick={handleLoadUrl}
              className="px-2 py-0.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-[11px] text-blue-300 font-semibold"
            >
              {isAr ? 'تشغيل' : 'Load'}
            </button>
          </div>
        </div>
      </div>

      {/* Video Stage */}
      <div className="flex-1 min-h-0 relative rounded-2xl bg-black border border-slate-800/80 overflow-hidden flex items-center justify-center">
        {videoUrl ? (
          <video
            ref={videoRef}
            src={videoUrl}
            className="w-full h-full object-contain"
            onTimeUpdate={(e) => setCurrentTime(e.currentTarget.currentTime)}
            onLoadedMetadata={(e) => {
              setDuration(e.currentTarget.duration || 0);
              onPauseAllAudio();
              e.currentTarget
                .play()
                .then(() => setIsPlaying(true))
                .catch(() => {});
            }}
            onPlay={() => {
              onPauseAllAudio();
              setIsPlaying(true);
            }}
            onPause={() => setIsPlaying(false)}
            onEnded={() => setIsPlaying(false)}
            onError={() =>
              setErrorMsg(
                isAr
                  ? 'حدث خطأ أثناء قراءة ملف الفيديو أو الرابط.'
                  : 'Playback error: could not decode or load the selected video.'
              )
            }
            onClick={togglePlay}
          />
        ) : (
          <div className="flex flex-col items-center gap-3 text-center p-6">
            <div className="w-16 h-16 rounded-2xl bg-slate-900 border border-slate-800 flex items-center justify-center text-blue-400">
              <Film className="w-8 h-8" />
            </div>
            <p className="text-sm font-semibold text-slate-200">
              {isAr
                ? 'اختر ملف فيديو من جهازك أو الصق رابط مباشر لبدء العرض'
                : 'Choose a local video file or paste a stream URL to begin playback'}
            </p>
            <p className="text-xs text-slate-400 max-w-md">
              {isAr
                ? 'يقوم مشغل الفيديو بإيقاف مشغل الموسيقى والـ DJ تلقائياً لضمان مخرج صوتي واحد نشط.'
                : 'Starting video playback automatically pauses the music player and DJ decks so only one audio output is active.'}
            </p>
          </div>
        )}

        {errorMsg && (
          <div className="absolute bottom-4 left-4 right-4 bg-red-950/90 border border-red-500/50 text-red-200 rounded-xl px-4 py-2.5 flex items-center gap-2 text-xs">
            <AlertCircle className="w-4 h-4 text-red-400 shrink-0" />
            <span>{errorMsg}</span>
          </div>
        )}
      </div>

      {/* Transport & Controls Bar */}
      <div className="bg-slate-900/90 border border-slate-800/80 rounded-2xl px-4 py-2.5 flex flex-col gap-2 shrink-0">
        <div className="flex items-center gap-3">
          <span className="text-[11px] font-mono text-slate-400 w-11 text-right">
            {formatSec(currentTime)}
          </span>
          <input
            type="range"
            min={0}
            max={duration > 0 ? duration : 100}
            step={0.1}
            value={currentTime}
            onChange={(e) => {
              const t = Number(e.target.value);
              setCurrentTime(t);
              if (videoRef.current) videoRef.current.currentTime = t;
            }}
            className="flex-1 accent-blue-500 h-1.5 cursor-pointer"
          />
          <span className="text-[11px] font-mono text-slate-400 w-11">
            {formatSec(duration)}
          </span>
        </div>

        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <button
              onClick={togglePlay}
              disabled={!videoUrl}
              className="w-9 h-9 rounded-xl bg-blue-600 hover:bg-blue-500 disabled:opacity-40 text-white flex items-center justify-center transition"
            >
              {isPlaying ? <Pause className="w-4 h-4" /> : <Play className="w-4 h-4" />}
            </button>
            <button
              onClick={() => {
                if (videoRef.current) {
                  videoRef.current.currentTime = 0;
                  setCurrentTime(0);
                }
              }}
              disabled={!videoUrl}
              className="w-8 h-8 rounded-xl bg-slate-800 hover:bg-slate-700 disabled:opacity-40 text-slate-200 flex items-center justify-center"
              title={isAr ? 'إعادة من البداية' : 'Restart'}
            >
              <RotateCcw className="w-3.5 h-3.5" />
            </button>

            <div className="flex items-center gap-1.5 ml-2">
              <button
                onClick={() => {
                  const nextMute = !isMuted;
                  setIsMuted(nextMute);
                  if (videoRef.current) videoRef.current.muted = nextMute;
                }}
                className="text-slate-300 hover:text-white"
              >
                {isMuted ? <VolumeX className="w-4 h-4" /> : <Volume2 className="w-4 h-4" />}
              </button>
              <input
                type="range"
                min={0}
                max={1}
                step={0.02}
                value={isMuted ? 0 : volume}
                onChange={(e) => {
                  const v = Number(e.target.value);
                  setVolume(v);
                  setIsMuted(v === 0);
                  if (videoRef.current) {
                    videoRef.current.volume = v;
                    videoRef.current.muted = v === 0;
                  }
                }}
                className="w-20 accent-blue-500 h-1 cursor-pointer"
              />
            </div>
          </div>

          <div className="flex items-center gap-2">
            {([0.75, 1, 1.25, 1.5, 2] as const).map((rate) => (
              <button
                key={rate}
                onClick={() => {
                  setPlaybackRate(rate);
                  if (videoRef.current) videoRef.current.playbackRate = rate;
                }}
                className={`px-2 py-1 rounded-lg text-[11px] font-semibold ${
                  playbackRate === rate
                    ? 'bg-blue-600 text-white'
                    : 'bg-slate-800 text-slate-300 hover:bg-slate-700'
                }`}
              >
                {rate}x
              </button>
            ))}

            <button
              onClick={() => {
                if (videoRef.current?.requestFullscreen) {
                  videoRef.current.requestFullscreen().catch(() => {});
                } else if (containerRef.current?.requestFullscreen) {
                  containerRef.current.requestFullscreen().catch(() => {});
                }
              }}
              className="p-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200"
              title={isAr ? 'ملء الشاشة' : 'Fullscreen'}
            >
              <Maximize2 className="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
