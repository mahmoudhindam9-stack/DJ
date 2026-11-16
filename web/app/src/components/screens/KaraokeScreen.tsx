import React, { useState, useEffect } from 'react';
import {
  Mic,
  MicOff,
  Radio,
  Circle,
  Square,
  Volume2,
  Sparkles,
  Sliders,
  CheckCircle2,
  RefreshCw,
} from 'lucide-react';
import { ThemeColors, MicFilterType, BeatFxDivision, MicVoiceEffectType } from '../../types';
import { micEngine } from '../../audio/MicEngine';

interface KaraokeScreenProps {
  themeColors: ThemeColors;
  isArabic: boolean;
}

export const KaraokeScreen: React.FC<KaraokeScreenProps> = ({ themeColors, isArabic }) => {
  const [isMicEnabled, setIsMicEnabled] = useState(micEngine.isMicEnabled);
  const [isRecording, setIsRecording] = useState(micEngine.isRecording);
  const [recordingSeconds, setRecordingSeconds] = useState(micEngine.recordingSeconds);
  const [micVolume, setMicVolume] = useState(micEngine.micVolume);
  const [echoLevel, setEchoLevel] = useState(micEngine.echoLevel);
  const [reverbLevel, setReverbLevel] = useState(micEngine.reverbLevel);
  const [flangerMix, setFlangerMix] = useState(micEngine.flangerMix);
  const [filterMix, setFilterMix] = useState(micEngine.filterMix);
  const [currentFilter, setCurrentFilter] = useState<MicFilterType>(micEngine.currentFilter);
  const [voiceEffect, setVoiceEffect] = useState<MicVoiceEffectType>(micEngine.currentVoiceEffect);
  const [beatFxEnabled, setBeatFxEnabled] = useState(micEngine.beatFxEnabled);
  const [bpm, setBpm] = useState(micEngine.bpm);
  const [beatDivision, setBeatDivision] = useState<BeatFxDivision>(micEngine.beatDivision);
  const [voiceProcessing, setVoiceProcessing] = useState(micEngine.voiceProcessing);
  const [devices, setDevices] = useState(micEngine.inputDevices);
  const [selectedDevice, setSelectedDevice] = useState(micEngine.selectedDeviceId);
  const [vuLevel, setVuLevel] = useState(0);

  useEffect(() => {
    const unsub = micEngine.subscribe(() => {
      setIsMicEnabled(micEngine.isMicEnabled);
      setIsRecording(micEngine.isRecording);
      setRecordingSeconds(micEngine.recordingSeconds);
      setMicVolume(micEngine.micVolume);
      setEchoLevel(micEngine.echoLevel);
      setReverbLevel(micEngine.reverbLevel);
      setFlangerMix(micEngine.flangerMix);
      setFilterMix(micEngine.filterMix);
      setCurrentFilter(micEngine.currentFilter);
      setVoiceEffect(micEngine.currentVoiceEffect);
      setBeatFxEnabled(micEngine.beatFxEnabled);
      setBpm(micEngine.bpm);
      setBeatDivision(micEngine.beatDivision);
      setVoiceProcessing(micEngine.voiceProcessing);
      setDevices(micEngine.inputDevices);
      setSelectedDevice(micEngine.selectedDeviceId);
    });

    let animId: number;
    const pollVu = () => {
      if (micEngine.isMicEnabled) {
        setVuLevel(micEngine.getVuLevel());
      } else {
        setVuLevel(0);
      }
      animId = requestAnimationFrame(pollVu);
    };
    pollVu();

    return () => {
      unsub();
      cancelAnimationFrame(animId);
    };
  }, []);

  const handleToggleMic = async () => {
    await micEngine.toggleMic();
  };

  const handleToggleRecord = () => {
    if (isRecording) {
      micEngine.stopRecordingAndDownload();
    } else {
      micEngine.startRecording();
    }
  };

  const formatTimer = (sec: number) => {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  };

  const filterList: Array<{ id: MicFilterType; name: string }> = [
    { id: 'NORMAL', name: 'Clean' },
    { id: 'STUDIO_REVERB', name: 'Studio Reverb' },
    { id: 'WARM', name: 'Warm Vocal' },
    { id: 'BRIGHT', name: 'Bright Treble' },
    { id: 'TELEPHONE', name: 'Telephone' },
    { id: 'ROBOT', name: 'Robot' },
    { id: 'RADIO', name: 'Radio' },
    { id: 'MEGAPHONE', name: 'Megaphone' },
    { id: 'CHORUS', name: 'Chorus' },
    { id: 'TREMOLO', name: 'Tremolo' },
    { id: 'BASS_BOOST', name: 'Bass Boost' },
    { id: 'CLUB', name: 'Club Stage' },
    { id: 'KID', name: 'Kid' },
    { id: 'CHIPMUNK', name: 'Chipmunk' },
    { id: 'SMALL_WOMAN', name: 'Young Woman' },
    { id: 'OLD_WOMAN', name: 'Old Woman' },
    { id: 'OLD_MAN', name: 'Old Man' },
    { id: 'GIANT', name: 'Giant' },
    { id: 'MONSTER', name: 'Monster' },
  ];

  const voiceEffects: Array<{ id: MicVoiceEffectType; name: string; emoji: string }> = [
    { id: 'NONE', name: 'Clean', emoji: '🎙️' },
    { id: 'WOMAN', name: 'Woman Voice', emoji: '👩' },
    { id: 'KID', name: 'Kid Voice', emoji: '👶' },
    { id: 'CHIPMUNK', name: 'Chipmunk', emoji: '🐿️' },
    { id: 'MONSTER', name: 'Monster', emoji: '👹' },
    { id: 'DARK_DEMON', name: 'Dark Demon', emoji: '👻' },
    { id: 'GIANT_BASS', name: 'Giant Bass', emoji: '🏔️' },
  ];

  return (
    <div
      id="karaoke-screen"
      className="h-full min-h-0 flex flex-col justify-between overflow-hidden select-none p-3 gap-2.5"
      style={{
        backgroundColor: themeColors.background,
        color: themeColors.textPrimary,
      }}
    >
      {/* Top Header & Mic Controls Bar (No Subtitles) */}
      <div
        className="rounded-xl p-2.5 border shrink-0 flex flex-wrap items-center justify-between gap-2"
        style={{
          backgroundColor: themeColors.surface,
          borderColor: themeColors.border,
        }}
      >
        <div className="flex items-center gap-2.5">
          <div
            className="w-8 h-8 rounded-lg flex items-center justify-center"
            style={{ backgroundColor: `${themeColors.primary}20` }}
          >
            <Mic className="w-4 h-4" style={{ color: themeColors.primary }} />
          </div>
          <h1 className="text-sm font-bold tracking-tight">
            {isArabic ? 'استوديو الميكروفون والكاريوكي' : 'Microphone & Karaoke Studio'}
          </h1>
          <span
            className="text-[10px] font-bold px-2 py-0.5 rounded-full"
            style={{
              backgroundColor: isMicEnabled
                ? 'rgba(16, 185, 129, 0.2)'
                : themeColors.surfaceVariant,
              color: isMicEnabled ? '#10b981' : themeColors.textMuted,
            }}
          >
            {isMicEnabled
              ? isArabic
                ? 'متصل ومفعل'
                : 'LIVE ON AIR'
              : isArabic
              ? 'متوقف'
              : 'OFFLINE'}
          </span>
        </div>

        {/* Input Device + Noise Reduction + Power + Record */}
        <div className="flex items-center gap-2 flex-wrap">
          <div className="flex items-center gap-1">
            <select
              value={selectedDevice || 'default'}
              onChange={(e) =>
                micEngine.selectDevice(
                  e.target.value === 'default' ? null : e.target.value
                )
              }
              className="px-2.5 py-1.5 rounded-lg border text-xs outline-none max-w-[200px]"
              style={{
                backgroundColor: themeColors.background,
                borderColor: themeColors.border,
                color: themeColors.textPrimary,
              }}
            >
              <option value="default">
                {isArabic ? 'الميكروفون الافتراضي' : 'Default System Mic'}
              </option>
              {devices.map((d) => (
                <option key={d.deviceId} value={d.deviceId}>
                  {d.label || `Mic ${d.deviceId.slice(0, 6)}`}
                </option>
              ))}
            </select>
            <button
              onClick={() => micEngine.refreshDevices()}
              className="p-1.5 rounded-lg border"
              style={{
                backgroundColor: themeColors.surfaceVariant,
                borderColor: themeColors.border,
              }}
              title={isArabic ? 'تحديث الأجهزة' : 'Refresh Devices'}
            >
              <RefreshCw className="w-3.5 h-3.5" />
            </button>
          </div>

          <button
            onClick={() => micEngine.toggleVoiceProcessing(!voiceProcessing)}
            className="px-2.5 py-1.5 rounded-lg border text-xs font-bold flex items-center gap-1"
            style={{
              backgroundColor: voiceProcessing
                ? `${themeColors.primary}20`
                : themeColors.surfaceVariant,
              borderColor: voiceProcessing ? themeColors.primary : themeColors.border,
              color: voiceProcessing ? themeColors.primary : themeColors.textSecondary,
            }}
          >
            <CheckCircle2 className="w-3.5 h-3.5" />
            {isArabic ? 'عزل الضوضاء' : 'Noise Gate'}
          </button>

          <button
            onClick={handleToggleMic}
            className="px-3 py-1.5 rounded-lg text-xs font-bold flex items-center gap-1.5 text-white transition-all"
            style={{
              backgroundColor: isMicEnabled ? '#ef4444' : themeColors.primary,
            }}
          >
            {isMicEnabled ? (
              <>
                <MicOff className="w-3.5 h-3.5" />
                {isArabic ? 'إيقاف المايك' : 'Stop Mic'}
              </>
            ) : (
              <>
                <Mic className="w-3.5 h-3.5" />
                {isArabic ? 'تشغيل المايك' : 'Start Mic'}
              </>
            )}
          </button>

          <button
            onClick={handleToggleRecord}
            disabled={!isMicEnabled}
            className="px-3 py-1.5 rounded-lg text-xs font-bold flex items-center gap-1.5 border disabled:opacity-40"
            style={{
              backgroundColor: isRecording ? '#ef4444' : themeColors.surfaceVariant,
              borderColor: isRecording ? '#ef4444' : themeColors.border,
              color: isRecording ? '#fff' : themeColors.textPrimary,
            }}
          >
            {isRecording ? (
              <>
                <Square className="w-3.5 h-3.5 fill-current" />
                {formatTimer(recordingSeconds)}
              </>
            ) : (
              <>
                <Circle className="w-3.5 h-3.5 fill-red-500 text-red-500" />
                {isArabic ? 'تسجيل MP3' : 'Record MP3'}
              </>
            )}
          </button>
        </div>
      </div>

      {/* Live VU Meter Bar */}
      <div
        className="rounded-xl px-3 py-2 border shrink-0 flex items-center gap-3"
        style={{
          backgroundColor: themeColors.surface,
          borderColor: themeColors.border,
        }}
      >
        <span className="text-xs font-bold shrink-0">
          {isArabic ? 'مؤشر الإشارة (VU)' : 'Input Level'}
        </span>
        <div
          className="flex-1 h-2.5 rounded-full overflow-hidden"
          style={{ backgroundColor: themeColors.surfaceVariant }}
        >
          <div
            className="h-full rounded-full transition-all duration-75"
            style={{
              width: `${Math.min(100, Math.round(vuLevel))}%`,
              background: 'linear-gradient(90deg, #10b981, #eab308, #ef4444)',
            }}
          />
        </div>
        <span className="text-xs font-mono font-bold w-10 text-right">
          {Math.round(vuLevel)}%
        </span>
      </div>

      {/* Middle 2-Column Workspace: Left = Voice Changer & Vocal Presets | Right = 5 DSP Sliders */}
      <div className="flex-1 min-h-0 grid grid-cols-1 lg:grid-cols-12 gap-2.5 overflow-hidden">
        {/* Left Column: Voice Effects & Vocal Presets */}
        <div className="lg:col-span-7 flex flex-col justify-between gap-2.5 min-h-0 overflow-hidden">
          {/* Voice Pitch / Character Changer */}
          <div
            className="rounded-xl p-2.5 border shrink-0 flex flex-col gap-2"
            style={{
              backgroundColor: themeColors.surface,
              borderColor: themeColors.border,
            }}
          >
            <div className="flex items-center gap-1.5">
              <Sparkles className="w-4 h-4" style={{ color: themeColors.primary }} />
              <span className="text-xs font-bold">
                {isArabic ? 'تغيير طبقات الصوت (Voice Changer)' : 'Live Voice Changer'}
              </span>
            </div>
            <div className="grid grid-cols-4 sm:grid-cols-7 gap-1.5">
              {voiceEffects.map((fx) => {
                const active = voiceEffect === fx.id;
                return (
                  <button
                    key={fx.id}
                    onClick={() => micEngine.setVoiceEffect(fx.id)}
                    className="p-1.5 rounded-lg border text-center transition-all flex flex-col items-center justify-center gap-0.5"
                    style={{
                      backgroundColor: active
                        ? themeColors.primary
                        : themeColors.surfaceVariant,
                      borderColor: active ? themeColors.primary : themeColors.border,
                      color: active ? '#fff' : themeColors.textPrimary,
                    }}
                  >
                    <span className="text-sm">{fx.emoji}</span>
                    <span className="text-[10px] font-bold truncate w-full">{fx.name}</span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* 19 Studio Vocal Filter Presets */}
          <div
            className="flex-1 min-h-0 rounded-xl p-2.5 border flex flex-col justify-between overflow-hidden"
            style={{
              backgroundColor: themeColors.surface,
              borderColor: themeColors.border,
            }}
          >
            <div className="flex items-center justify-between shrink-0 mb-1.5">
              <div className="flex items-center gap-1.5">
                <Radio className="w-4 h-4" style={{ color: themeColors.secondary }} />
                <span className="text-xs font-bold">
                  {isArabic ? 'فلاتر الاستوديو الصوتية (19 Preset)' : 'Studio Vocal Filters'}
                </span>
              </div>
              <span
                className="text-[11px] font-bold px-2 py-0.5 rounded"
                style={{
                  backgroundColor: `${themeColors.secondary}20`,
                  color: themeColors.secondary,
                }}
              >
                {currentFilter}
              </span>
            </div>

            <div className="flex-1 min-h-0 grid grid-cols-4 sm:grid-cols-5 gap-1.5 content-center">
              {filterList.map((f) => {
                const active = currentFilter === f.id;
                return (
                  <button
                    key={f.id}
                    onClick={() => micEngine.setFilter(f.id)}
                    className="px-2 py-1.5 rounded-lg border text-[11px] font-bold truncate transition-all"
                    style={{
                      backgroundColor: active
                        ? themeColors.secondary
                        : themeColors.surfaceVariant,
                      borderColor: active ? themeColors.secondary : themeColors.border,
                      color: active ? '#fff' : themeColors.textPrimary,
                    }}
                  >
                    {f.name}
                  </button>
                );
              })}
            </div>
          </div>
        </div>

        {/* Right Column: 5 Live DSP Parameter Sliders */}
        <div
          className="lg:col-span-5 rounded-xl p-3 border flex flex-col justify-between min-h-0 overflow-hidden"
          style={{
            backgroundColor: themeColors.surface,
            borderColor: themeColors.border,
          }}
        >
          <div className="flex items-center gap-1.5 shrink-0">
            <Sliders className="w-4 h-4" style={{ color: themeColors.primary }} />
            <span className="text-xs font-bold">
              {isArabic ? 'معالجة الصوت الحية (DSP Rack)' : 'Live Vocal DSP Rack'}
            </span>
          </div>

          <div className="flex-1 flex flex-col justify-evenly gap-2 py-1">
            {[
              {
                label: isArabic ? 'مستوى صوت المايك (Mic Gain)' : 'Mic Gain',
                value: micVolume,
                max: 2,
                color: themeColors.primary,
                onChange: (v: number) => micEngine.setMicVolume(v),
              },
              {
                label: isArabic ? 'الصدى المتكرر (Echo)' : 'Echo Delay',
                value: echoLevel,
                max: 1,
                color: themeColors.secondary,
                onChange: (v: number) => micEngine.setEchoLevel(v),
              },
              {
                label: isArabic ? 'صدى القاعة (Reverb)' : 'Hall Reverb',
                value: reverbLevel,
                max: 1,
                color: themeColors.tertiary,
                onChange: (v: number) => micEngine.setReverbLevel(v),
              },
              {
                label: isArabic ? 'تأثير فلانجر (Flanger)' : 'Flanger Mix',
                value: flangerMix,
                max: 1,
                color: themeColors.accentA,
                onChange: (v: number) => micEngine.setFlangerMix(v),
              },
              {
                label: isArabic ? 'قوة الفلتر (Filter Mix)' : 'Filter Wet/Dry',
                value: filterMix,
                max: 1,
                color: themeColors.accentB,
                onChange: (v: number) => micEngine.setFilterMix(v),
              },
            ].map((ctrl) => (
              <div key={ctrl.label} className="space-y-1">
                <div className="flex items-center justify-between text-xs">
                  <span className="font-semibold">{ctrl.label}</span>
                  <span className="font-mono font-bold" style={{ color: ctrl.color }}>
                    {Math.round((ctrl.value / ctrl.max) * 100)}%
                  </span>
                </div>
                <input
                  type="range"
                  min={0}
                  max={ctrl.max}
                  step={0.01}
                  value={ctrl.value}
                  onChange={(e) => ctrl.onChange(Number(e.target.value))}
                  className="w-full h-1.5 rounded-lg appearance-none cursor-pointer"
                  style={{
                    accentColor: ctrl.color,
                    backgroundColor: themeColors.surfaceVariant,
                  }}
                />
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Bottom Beat FX Bar */}
      <div
        className="rounded-xl p-2.5 border shrink-0 flex flex-wrap items-center justify-between gap-3"
        style={{
          backgroundColor: themeColors.surface,
          borderColor: themeColors.border,
        }}
      >
        <div className="flex items-center gap-2">
          <Volume2 className="w-4 h-4" style={{ color: themeColors.primary }} />
          <span className="text-xs font-bold">
            {isArabic ? 'مزامنة الصدى مع الإيقاع (Beat FX)' : 'Beat FX Tempo Sync'}
          </span>
          <button
            onClick={() => micEngine.setBeatFxEnabled(!beatFxEnabled)}
            className="px-2.5 py-1 rounded-lg text-xs font-bold"
            style={{
              backgroundColor: beatFxEnabled
                ? themeColors.primary
                : themeColors.surfaceVariant,
              color: beatFxEnabled ? '#fff' : themeColors.textMuted,
            }}
          >
            {beatFxEnabled ? 'SYNC ON' : 'SYNC OFF'}
          </button>
        </div>

        <div className="flex items-center gap-3 flex-wrap">
          <div className="flex items-center gap-2">
            <span className="text-xs font-mono font-bold">{bpm} BPM</span>
            <input
              type="range"
              min={60}
              max={180}
              value={bpm}
              onChange={(e) => micEngine.setBpm(Number(e.target.value))}
              className="w-28 h-1.5 rounded-lg appearance-none cursor-pointer"
              style={{ accentColor: themeColors.primary }}
            />
          </div>

          <div className="flex items-center gap-1">
            {(['1/1', '3/4', '1/2', '1/4', '1/8'] as BeatFxDivision[]).map((div) => (
              <button
                key={div}
                onClick={() => micEngine.setBeatDivision(div)}
                className="px-2 py-1 rounded text-xs font-mono font-bold border"
                style={{
                  backgroundColor:
                    beatDivision === div ? themeColors.primary : themeColors.surfaceVariant,
                  borderColor:
                    beatDivision === div ? themeColors.primary : themeColors.border,
                  color: beatDivision === div ? '#fff' : themeColors.textPrimary,
                }}
              >
                {div}
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};
