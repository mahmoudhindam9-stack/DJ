import React, { useEffect, useState } from 'react';
import {
  AudioLines, Check, Clock3, Download, Headphones, HardDrive, Loader2, Music2, Play, Save, Sparkles,
} from 'lucide-react';
import { AudioItem, ThemeColors } from '../../types';
import { checkLocalMusicEngine, generateLocalMusic, startLocalMusicEngine } from '../../services/LocalMusicService';

interface AIMusicStudioScreenProps {
  themeColors: ThemeColors;
  isArabic: boolean;
  onPlayGeneratedTrack: (track: AudioItem) => void;
  onSendToDeckA: (track: AudioItem) => void;
  onSendToDeckB: (track: AudioItem) => void;
  onSaveToLibrary: (track: AudioItem) => Promise<void>;
}

const GENRES = ['Electronic / EDM', 'Arabic Pop', 'Hip-Hop', 'Cinematic Orchestral', 'Lo-fi / Chill', 'Rock', 'Jazz / Soul', 'Afrobeats', 'Ambient'];
const MOODS = ['Energetic', 'Uplifting', 'Dark', 'Romantic', 'Dreamy', 'Epic', 'Relaxed', 'Mysterious'];

export const AIMusicStudioScreen: React.FC<AIMusicStudioScreenProps> = ({
  themeColors, isArabic, onPlayGeneratedTrack, onSendToDeckA, onSendToDeckB, onSaveToLibrary,
}) => {
  const [localEngineReady, setLocalEngineReady] = useState(false);
  const [checkingEngine, setCheckingEngine] = useState(true);
  const [startingEngine, setStartingEngine] = useState(false);
  const [durationSeconds, setDurationSeconds] = useState(120);
  const [trackTitle, setTrackTitle] = useState('Midnight Drive');
  const [description, setDescription] = useState('A polished modern club track with a memorable hook, clean low end, tight percussion, evolving synth textures and a professional mix. Build tension, deliver a confident drop, and finish with a smooth outro.');
  const [genre, setGenre] = useState('Electronic / EDM');
  const [mood, setMood] = useState('Energetic');
  const [bpm, setBpm] = useState(124);
  const [vocalMode, setVocalMode] = useState<'instrumental' | 'generated' | 'custom'>('instrumental');
  const [customLyrics, setCustomLyrics] = useState('');
  const [generating, setGenerating] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [generatedTrack, setGeneratedTrack] = useState<AudioItem | null>(null);
  const [generatedUrl, setGeneratedUrl] = useState<string | null>(null);
  const [generatedLyrics, setGeneratedLyrics] = useState('');
  const [saved, setSaved] = useState(false);
  const [audioDurationMs, setAudioDurationMs] = useState(0);
  const [saving, setSaving] = useState(false);
  const accent = themeColors.primary;
  const card = themeColors.surface;
  const panel = themeColors.surfaceVariant;
  const border = themeColors.border;
  const muted = themeColors.textMuted;
  const fg = themeColors.textPrimary;

  useEffect(() => {
    let active = true;
    void checkLocalMusicEngine()
      .then((ready) => { if (active) setLocalEngineReady(ready); })
      .catch(() => { if (active) setLocalEngineReady(false); })
      .finally(() => { if (active) setCheckingEngine(false); });
    return () => { active = false; };
  }, []);

  const handleStartLocalEngine = async () => {
    setErrorMessage(null);
    setStartingEngine(true);
    setStatusMessage(isArabic
      ? 'يتم تشغيل ACE-Step المحلي. أول تشغيل يحتاج تنزيل المكونات والنماذج، وبعدها يعمل دون إنترنت.'
      : 'Starting ACE-Step locally. First run downloads its dependencies and model weights; later runs can work offline.');
    try {
      await startLocalMusicEngine();
      const deadline = Date.now() + 20 * 60 * 1000;
      let ready = false;
      while (Date.now() < deadline) {
        await new Promise((resolve) => window.setTimeout(resolve, 2500));
        try { ready = await checkLocalMusicEngine(); } catch { ready = false; }
        if (ready) break;
        setStatusMessage(isArabic
          ? 'المحرك يجهّز النماذج محليًا. اترك نافذة PowerShell مفتوحة حتى يصبح جاهزًا.'
          : 'The local engine is preparing its models. Keep the PowerShell window open until it is ready.');
      }
      setLocalEngineReady(ready);
      if (ready) {
        setStatusMessage(isArabic ? 'ACE-Step جاهز. التوليد يعمل محليًا دون مفتاح API.' : 'ACE-Step is ready. Generation runs locally without an API key.');
      } else {
        setErrorMessage(isArabic
          ? 'لم يصبح المحرك جاهزًا بعد. راجع نافذة PowerShell لأي خطأ ثم أعد المحاولة.'
          : 'The local engine did not become ready. Check the PowerShell window for errors, then retry.');
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : (isArabic ? 'تعذر تشغيل محرك الموسيقى المحلي.' : 'Could not start the local music engine.'));
    } finally {
      setStartingEngine(false);
    }
  };

  const buildPrompt = (): string => {
    const parts = [
      'Genre: ' + genre + '.',
      'Mood: ' + mood + '.',
      'Target tempo: approximately ' + bpm + ' BPM.',
      description.trim(),
    ];
    if (vocalMode === 'instrumental') parts.push('Instrumental only. No vocals and no lyrics.');
    else if (vocalMode === 'generated') parts.push('Create original vocals and lyrics suitable for the genre and mood. Do not imitate a specific existing singer.');
    else parts.push('Use these original lyrics with clear verse, chorus, and outro sections.');
    parts.push(durationSeconds <= 30
      ? 'Create a short 30-second music clip with a strong hook and clean ending.'
      : 'Create a complete song with a clear intro, verse, chorus, development, and satisfying outro.');
    return parts.join('\n\n');
  };

  const handleGenerate = async (event: React.FormEvent) => {
    event.preventDefault();
    setErrorMessage(null);
    setStatusMessage(null);
    if (!localEngineReady) {
      setErrorMessage(isArabic ? 'شغّل محرك ACE-Step المحلي أولاً.' : 'Start the local ACE-Step engine first.');
      return;
    }
    if (description.trim().length < 12) {
      setErrorMessage(isArabic ? 'اكتب وصفًا أوضح للموسيقى المطلوبة.' : 'Describe the music you want in more detail.');
      return;
    }
    if (vocalMode === 'custom' && customLyrics.trim().length < 4) {
      setErrorMessage(isArabic ? 'اكتب كلمات الأغنية أو اختر الغناء التلقائي.' : 'Add your lyrics or choose generated vocals.');
      return;
    }

    setGenerating(true);
    setSaved(false);
    setGeneratedLyrics('');
    try {
      const result = await generateLocalMusic({
        prompt: buildPrompt(),
        lyrics: vocalMode === 'custom' ? customLyrics.trim() : '',
        bpm,
        durationSeconds,
        vocalMode,
      });
      const url = new URL(result.audioUrl, window.location.origin).toString();
      setGeneratedUrl(url);
      const actualDuration = Math.max(10000, (result.durationSeconds || durationSeconds) * 1000);
      const track: AudioItem = {
        id: 'ace_step_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2, 8),
        title: trackTitle.trim() || (isArabic ? 'مقطوعة مولدة محليًا' : 'Locally Generated Track'),
        artist: 'DJ Desktop · ACE-Step 1.5 (Local)',
        album: isArabic ? 'استوديو الموسيقى المحلي' : 'Offline AI Music Studio',
        duration: actualDuration,
        uri: url,
        addedDate: Date.now(),
      };
      setGeneratedTrack(track);
      setAudioDurationMs(0);
      setGeneratedLyrics(result.lyrics || (vocalMode === 'custom' ? customLyrics.trim() : ''));
      setStatusMessage(isArabic
        ? 'تم توليد الموسيقى على جهازك. استمع إليها ثم احفظها أو أرسلها إلى أحد الـ Decks.'
        : 'Music generated on your computer. Preview it, save it, or send it to either DJ deck.');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : (isArabic ? 'فشل توليد الموسيقى المحلية.' : 'Local music generation failed.'));
    } finally {
      setGenerating(false);
    }
  };

  const handleSave = async () => {
    if (!generatedTrack || saved || saving) return;
    setSaving(true);
    setErrorMessage(null);
    try {
      const track = { ...generatedTrack, duration: audioDurationMs || generatedTrack.duration };
      await onSaveToLibrary(track);
      setGeneratedTrack(track);
      setSaved(true);
      setStatusMessage(isArabic ? 'تم حفظ الموسيقى في المكتبة.' : 'Saved to your music library.');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : (isArabic ? 'تعذر حفظ الموسيقى.' : 'Could not save the generated track.'));
    } finally {
      setSaving(false);
    }
  };

  const durationText = (milliseconds: number) => {
    const seconds = Math.max(0, Math.floor(milliseconds / 1000));
    return Math.floor(seconds / 60) + ':' + String(seconds % 60).padStart(2, '0');
  };
  const fieldClass = 'w-full rounded-xl border px-3 py-2.5 text-xs outline-none focus:ring-2 resize-y';
  const fieldStyle: React.CSSProperties = { backgroundColor: panel, borderColor: border, color: fg };

  return (
    <div id="ai-music-studio-screen" className="flex-1 min-h-0 overflow-y-auto p-4 sm:p-5" style={{ backgroundColor: themeColors.background, color: fg }}>
      <div className="max-w-6xl mx-auto space-y-4">
        <header className="relative overflow-hidden rounded-3xl border p-5 sm:p-6" style={{ backgroundColor: card, borderColor: border }}>
          <div className="absolute -right-8 -top-12 w-52 h-52 rounded-full blur-3xl opacity-15" style={{ backgroundColor: accent }} />
          <div className="relative flex items-start gap-4">
            <div className="w-14 h-14 rounded-2xl flex items-center justify-center shrink-0" style={{ background: 'linear-gradient(135deg,' + themeColors.primary + ',' + themeColors.secondary + ')' }}><AudioLines className="w-7 h-7 text-white" /></div>
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <h1 className="text-xl sm:text-2xl font-black tracking-tight">{isArabic ? 'استوديو الموسيقى بالذكاء الاصطناعي' : 'AI Music Studio'}</h1>
                <span className="rounded-full px-2 py-1 text-[9px] font-black uppercase tracking-wider" style={{ backgroundColor: panel, color: accent }}>ACE-Step 1.5 · LOCAL AI</span>
              </div>
              <div className="mt-4 flex flex-wrap gap-2 text-[10px] font-semibold">
                <span className="rounded-lg border px-2.5 py-1.5 flex items-center gap-1.5" style={{ borderColor: border }}><Headphones className="w-3.5 h-3.5" />{isArabic ? 'صوت ستيريو' : 'Stereo audio'}</span>
                <span className="rounded-lg border px-2.5 py-1.5 flex items-center gap-1.5" style={{ borderColor: border }}><Clock3 className="w-3.5 h-3.5" />{durationSeconds + ' sec'}</span>
                <span className="rounded-lg border px-2.5 py-1.5 flex items-center gap-1.5" style={{ borderColor: border }}><HardDrive className="w-3.5 h-3.5" />{isArabic ? 'استدلال محلي' : 'Local inference'}</span>
              </div>
            </div>
          </div>
        </header>

        <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,1.05fr)_minmax(300px,0.95fr)] gap-4 items-start">
          <form onSubmit={(event) => { void handleGenerate(event); }} className="rounded-2xl border p-4 sm:p-5 space-y-4" style={{ backgroundColor: card, borderColor: border }}>
            <div className="flex items-center gap-2"><Sparkles className="w-5 h-5" style={{ color: accent }} /><h2 className="text-sm font-black">{isArabic ? 'صمّم مقطوعتك' : 'Compose your track'}</h2></div>
            <div className="rounded-xl border p-3 space-y-3" style={{ borderColor: border, backgroundColor: panel }}>
              <div className="flex items-center gap-2">
                <HardDrive className="w-4 h-4" style={{ color: localEngineReady ? '#34d399' : accent }} />
                <span className="text-xs font-black">{isArabic ? 'نموذج موسيقى محلي — ACE-Step 1.5' : 'Local music model — ACE-Step 1.5'}</span>
                <span className="ml-auto rounded-full px-2 py-1 text-[9px] font-black" style={{ color: localEngineReady ? '#34d399' : muted, backgroundColor: localEngineReady ? '#34d39918' : card }}>
                  {checkingEngine ? (isArabic ? 'فحص...' : 'CHECKING…') : localEngineReady ? 'READY' : (isArabic ? 'غير مشغّل' : 'OFFLINE')}
                </span>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <button type="button" onClick={() => { void handleStartLocalEngine(); }} disabled={checkingEngine || startingEngine || localEngineReady} className="rounded-lg px-3 py-2 text-[10px] font-black text-white disabled:opacity-50" style={{ backgroundColor: accent }}>
                  {startingEngine ? (isArabic ? 'جاري التجهيز...' : 'Starting…') : localEngineReady ? (isArabic ? 'المحرك جاهز' : 'Engine ready') : (isArabic ? 'تجهيز / تشغيل النموذج المحلي' : 'Set up / start local model')}
                </button>
                <a href="https://github.com/ACE-Step/ACE-Step-1.5" target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 text-[10px] underline" style={{ color: muted }}>
                  {isArabic ? 'المشروع على GitHub' : 'ACE-Step on GitHub'}
                </a>
              </div>
            </div>

            <div className="space-y-2"><label className="block text-xs font-bold">{isArabic ? 'اسم المقطوعة' : 'Track title'}</label><input value={trackTitle} onChange={(event) => setTrackTitle(event.target.value)} maxLength={90} className={fieldClass} style={fieldStyle} /></div>

            <div className="space-y-2">
              <label className="block text-xs font-bold">{isArabic ? 'مدة المقطوعة' : 'Track duration'}</label>
              <div className="grid grid-cols-2 gap-2">
                {([
                  { value: 30, label: isArabic ? 'مقطع سريع' : 'Quick clip', detail: isArabic ? '30 ثانية' : '30 seconds' },
                  { value: 120, label: isArabic ? 'أغنية كاملة' : 'Full song', detail: isArabic ? 'دقيقتان تقريبًا' : 'About 2 minutes' },
                ] as Array<{ value: number; label: string; detail: string }>).map((option) => (
                  <button key={option.value} type="button" onClick={() => setDurationSeconds(option.value)} className="rounded-xl border p-3 text-left" style={{ backgroundColor: durationSeconds === option.value ? accent + '18' : panel, borderColor: durationSeconds === option.value ? accent : border }}>
                    <span className="block text-xs font-black">{option.label}</span><span className="block text-[10px] mt-1 opacity-70">{option.detail}</span>
                  </button>
                ))}
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <label className="space-y-1.5"><span className="block text-xs font-bold">{isArabic ? 'النوع الموسيقي' : 'Genre'}</span><select value={genre} onChange={(event) => setGenre(event.target.value)} className={fieldClass} style={fieldStyle}>{GENRES.map((value) => <option key={value} value={value}>{value}</option>)}</select></label>
              <label className="space-y-1.5"><span className="block text-xs font-bold">{isArabic ? 'المزاج' : 'Mood'}</span><select value={mood} onChange={(event) => setMood(event.target.value)} className={fieldClass} style={fieldStyle}>{MOODS.map((value) => <option key={value} value={value}>{value}</option>)}</select></label>
            </div>

            <div className="rounded-xl border p-3 space-y-2" style={{ borderColor: border }}><div className="flex items-center justify-between gap-3 text-xs"><label htmlFor="ace-step-bpm" className="font-bold">{isArabic ? 'السرعة المستهدفة' : 'Target BPM'}</label><span className="font-mono font-black" style={{ color: accent }}>{bpm} BPM</span></div><input id="ace-step-bpm" type="range" min={60} max={180} value={bpm} onChange={(event) => setBpm(Number(event.target.value))} className="w-full" style={{ accentColor: accent }} /><div className="flex justify-between text-[9px]" style={{ color: muted }}><span>60 BPM</span><span>120 BPM</span><span>180 BPM</span></div></div>

            <div className="space-y-2"><label className="block text-xs font-bold">{isArabic ? 'وصف الإنتاج الموسيقي' : 'Production direction'}</label><textarea rows={4} maxLength={6000} value={description} onChange={(event) => setDescription(event.target.value)} className={fieldClass + ' leading-relaxed'} style={fieldStyle} placeholder={isArabic ? 'صف الإيقاع والآلات والطاقة وبنية الأغنية...' : 'Describe the beat, instruments, energy and arrangement...'} /><div className="flex flex-wrap gap-1.5">{['Punchy club drums and deep bass', 'Warm Arabic strings and tabla', 'Cinematic risers and a powerful drop', 'Soft piano with vinyl texture'].map((preset) => <button key={preset} type="button" onClick={() => setDescription(preset + '. Build a professional mix with a clear intro, dynamic arrangement, memorable hook and polished ending.')} className="rounded-full border px-2.5 py-1 text-[9px] font-semibold" style={{ borderColor: border, backgroundColor: panel }}>{preset}</button>)}</div></div>

            <div className="space-y-2"><label className="block text-xs font-bold">{isArabic ? 'الغناء والكلمات' : 'Vocals & lyrics'}</label><div className="grid grid-cols-3 gap-2">{([
              { value: 'instrumental', label: isArabic ? 'آلات فقط' : 'Instrumental' },
              { value: 'generated', label: isArabic ? 'غناء تلقائي' : 'AI vocals' },
              { value: 'custom', label: isArabic ? 'كلماتي' : 'Custom lyrics' },
            ] as const).map((option) => <button type="button" key={option.value} onClick={() => setVocalMode(option.value)} className="rounded-lg border py-2 px-1.5 text-[10px] font-bold" style={{ backgroundColor: vocalMode === option.value ? accent + '18' : panel, borderColor: vocalMode === option.value ? accent : border }}>{option.label}</button>)}</div>{vocalMode === 'custom' && <textarea value={customLyrics} onChange={(event) => setCustomLyrics(event.target.value)} maxLength={6000} rows={5} className={fieldClass + ' leading-relaxed'} style={fieldStyle} placeholder={'[Verse 1]\nWrite your original lyrics...\n\n[Chorus]\nYour hook goes here...'} />}</div>

            {errorMessage && <div role="alert" className="rounded-xl border p-3 text-xs leading-relaxed" style={{ color: '#fb7185', borderColor: '#fb718555' }}>{errorMessage}</div>}
            {statusMessage && <div role="status" className="rounded-xl border p-3 text-xs leading-relaxed" style={{ color: '#34d399', borderColor: '#34d39955' }}>{statusMessage}</div>}
            <button type="submit" disabled={generating || !description.trim() || !localEngineReady} className="w-full rounded-xl px-4 py-3.5 text-xs font-black text-white flex items-center justify-center gap-2 disabled:opacity-60" style={{ background: 'linear-gradient(100deg,' + themeColors.primary + ',' + themeColors.secondary + ')' }}>{generating ? <Loader2 className="w-4 h-4 animate-spin" /> : <Sparkles className="w-4 h-4" />}{generating ? (isArabic ? 'النموذج المحلي يصنع الموسيقى...' : 'ACE-Step is composing locally...') : (isArabic ? 'توليد الموسيقى محليًا' : 'Generate music offline')}</button>
            <p className="text-[10px] leading-relaxed" style={{ color: muted }}>{isArabic ? 'لا يوجد مفتاح API أو رسوم لكل طلب. بعد إعداد النموذج وتنزيل الأوزان مرة واحدة، يعمل التوليد محليًا.' : 'No API key or per-request cloud fees. After the one-time setup and model download, inference runs locally.'}</p>
          </form>

          <section className="rounded-2xl border p-4 sm:p-5 space-y-4 lg:sticky lg:top-4" style={{ backgroundColor: card, borderColor: border }}>
            <div className="flex items-center justify-between gap-3"><div className="flex items-center gap-2"><Music2 className="w-5 h-5" style={{ color: accent }} /><h2 className="text-sm font-black">{isArabic ? 'غرفة الاستماع' : 'Listening room'}</h2></div>{generatedTrack && <span className="text-[9px] font-black tracking-wider" style={{ color: '#34d399' }}>READY</span>}</div>
            {generatedTrack && generatedUrl ? <>
              <div className="rounded-2xl border p-4" style={{ borderColor: border, background: 'linear-gradient(145deg,' + panel + ',' + card + ')' }}><div className="w-14 h-14 rounded-2xl flex items-center justify-center mb-3" style={{ background: 'linear-gradient(145deg,' + themeColors.primary + ',' + themeColors.secondary + ')' }}><AudioLines className="w-7 h-7 text-white" /></div><div className="text-sm font-black break-words">{generatedTrack.title}</div><div className="text-[10px] mt-1" style={{ color: muted }}>DJ Desktop · ACE-Step 1.5 (Local)</div><div className="flex flex-wrap gap-2 mt-3 text-[10px] font-bold"><span className="rounded-lg px-2 py-1" style={{ backgroundColor: panel }}>{genre}</span><span className="rounded-lg px-2 py-1" style={{ backgroundColor: panel }}>{bpm} BPM target</span><span className="rounded-lg px-2 py-1" style={{ backgroundColor: panel }}>{durationText(audioDurationMs || generatedTrack.duration)}</span></div></div>
              <audio controls preload="metadata" src={generatedUrl} className="w-full" onLoadedMetadata={(event) => { const duration = Number.isFinite(event.currentTarget.duration) ? event.currentTarget.duration * 1000 : 0; if (duration > 0) { setAudioDurationMs(duration); setGeneratedTrack((current) => current ? { ...current, duration } : current); } }} />
              <div className="grid grid-cols-2 gap-2">
                <button type="button" onClick={() => onPlayGeneratedTrack({ ...generatedTrack, duration: audioDurationMs || generatedTrack.duration })} className="rounded-xl px-3 py-2.5 text-xs font-black flex items-center justify-center gap-2 text-white" style={{ backgroundColor: accent }}><Play className="w-4 h-4 fill-current" />{isArabic ? 'تشغيل بالمشغل' : 'Play in player'}</button>
                <button type="button" onClick={() => void handleSave()} disabled={saved || saving} className="rounded-xl border px-3 py-2.5 text-xs font-black flex items-center justify-center gap-2 disabled:opacity-50" style={{ borderColor: border, backgroundColor: panel }}><Save className="w-4 h-4" />{saved ? (isArabic ? 'تم الحفظ' : 'Saved') : saving ? (isArabic ? 'حفظ...' : 'Saving...') : (isArabic ? 'حفظ بالمكتبة' : 'Save to library')}</button>
                <button type="button" onClick={() => onSendToDeckA({ ...generatedTrack, duration: audioDurationMs || generatedTrack.duration })} className="rounded-xl border px-3 py-2.5 text-xs font-black flex items-center justify-center gap-2" style={{ borderColor: themeColors.accentA, color: themeColors.accentA }}><Music2 className="w-4 h-4" />Send to Deck A</button>
                <button type="button" onClick={() => onSendToDeckB({ ...generatedTrack, duration: audioDurationMs || generatedTrack.duration })} className="rounded-xl border px-3 py-2.5 text-xs font-black flex items-center justify-center gap-2" style={{ borderColor: themeColors.accentB, color: themeColors.accentB }}><Music2 className="w-4 h-4" />Send to Deck B</button>
                <a href={generatedUrl} download={(generatedTrack.title || 'ace-step-track').replace(/[\\/:*?"<>|]/g, '_') + '.mp3'} className="col-span-2 rounded-xl border px-3 py-2.5 text-xs font-black flex items-center justify-center gap-2" style={{ borderColor: border, backgroundColor: panel }}><Download className="w-4 h-4" />{isArabic ? 'تنزيل ملف الموسيقى' : 'Download generated audio'}</a>
              </div>
              {generatedLyrics && <details className="rounded-xl border p-3" style={{ borderColor: border }}><summary className="cursor-pointer text-xs font-black">{isArabic ? 'الكلمات وبنية المقطوعة' : 'Generated lyrics & structure'}</summary><pre className="mt-3 whitespace-pre-wrap break-words text-xs leading-relaxed" style={{ color: muted }}>{generatedLyrics}</pre></details>}
              {saved && <p className="text-[10px] flex items-center gap-1.5" style={{ color: '#34d399' }}><Check className="w-3.5 h-3.5" />{isArabic ? 'الموسيقى محفوظة في المكتبة ويمكن تشغيلها بعد إعادة فتح التطبيق.' : 'Saved in your library and available after restarting.'}</p>}
            </> : <div className="min-h-[380px] rounded-2xl border flex flex-col items-center justify-center text-center p-6" style={{ borderColor: border, background: 'radial-gradient(ellipse at top,' + accent + '12,transparent 65%)' }}><div className="w-20 h-20 rounded-full border flex items-center justify-center mb-4" style={{ borderColor: accent + '60', color: accent }}><AudioLines className="w-9 h-9" /></div><div className="text-sm font-black">{isArabic ? 'مساحة الماستر' : 'Master listening room'}</div><p className="mt-2 max-w-xs text-xs leading-relaxed" style={{ color: muted }}>{isArabic ? 'ستظهر هنا المقطوعة ومشغل الاستماع وأزرار الحفظ والإرسال إلى الـ Decks بعد التوليد.' : 'Your generated track, audio player, lyrics, download, library save, and Deck A/B actions will appear here.'}</p></div>}
          </section>
        </div>
      </div>
    </div>
  );
};
