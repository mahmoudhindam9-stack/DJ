import React, { useState, useEffect } from 'react';
import {
  Sliders,
  Power,
  RotateCcw,
  Download,
  Upload,
  Sparkles,
  Volume2,
  Trash2,
  BookmarkPlus,
  X,
  Check,
} from 'lucide-react';
import { ThemeColors, EqualizerBand, CustomPreset } from '../../types';
import { mainAudioEngine } from '../../audio/AudioEngine';
import { BUILTIN_PRESETS } from '../../audio/EqualizerPresets';
import { db } from '../../storage/db';

interface EqualizerScreenProps {
  themeColors: ThemeColors;
  isArabic: boolean;
}

const PRESET_AR_NAMES: Record<string, string> = {
  Flat: 'مسطح (Flat)',
  'Bass Boost': 'تعزيز الباص',
  'Treble Boost': 'تعزيز التريبل',
  Vocal: 'إبراز الصوت (Vocal)',
  Rock: 'روك (Rock)',
  Pop: 'بوب (Pop)',
  Jazz: 'جاز (Jazz)',
  Electronic: 'إلكتروني (EDM)',
  Classical: 'كلاسيكي',
  'Hip Hop': 'هيب هوب',
  Dance: 'دانس (Dance)',
  Live: 'حفل مباشر (Live)',
  Club: 'نادي (Club)',
  Acoustic: 'أكوستيك',
  Techno: 'تكنو (Techno)',
  Custom: 'مخصص (Custom)',
};

export const EqualizerScreen: React.FC<EqualizerScreenProps> = ({ themeColors, isArabic }) => {
  const [eqEnabled, setEqEnabled] = useState(mainAudioEngine.eqEnabled);
  const [bands, setBands] = useState<EqualizerBand[]>(mainAudioEngine.getBands());
  const [selectedPreset, setSelectedPreset] = useState(mainAudioEngine.currentPreset);
  const [bassBoost, setBassBoost] = useState(mainAudioEngine.bassBoostLevel);
  const [trebleBoost, setTrebleBoost] = useState(mainAudioEngine.trebleBoostLevel);
  const [preampDb, setPreampDb] = useState(mainAudioEngine.currentPreampDb);
  const [customPresets, setCustomPresets] = useState<CustomPreset[]>([]);
  const [customPresetName, setCustomPresetName] = useState('');
  const [showSaveDialog, setShowSaveDialog] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;

    db.getCustomPresets()
      .then((presets) => {
        if (mounted) setCustomPresets(presets);
      })
      .catch((err) => console.warn('Could not load custom presets:', err));

    db.getSetting<{
      enabled?: boolean;
      preset?: string;
      bands?: number[];
      bassBoost?: number;
      trebleBoost?: number;
      preampDb?: number;
    } | null>('active_eq_state', null)
      .then((saved) => {
        if (!mounted || !saved) return;
        if (typeof saved.enabled === 'boolean') {
          setEqEnabled(saved.enabled);
          mainAudioEngine.setEqEnabled(saved.enabled);
        }
        if (Array.isArray(saved.bands) && saved.bands.length > 0) {
          mainAudioEngine.applyCustomBands(saved.bands);
          setBands([...mainAudioEngine.getBands()]);
        }
        if (typeof saved.bassBoost === 'number') {
          setBassBoost(saved.bassBoost);
          mainAudioEngine.setBassBoostLevel(saved.bassBoost);
        }
        if (typeof saved.trebleBoost === 'number') {
          setTrebleBoost(saved.trebleBoost);
          mainAudioEngine.setTrebleBoostLevel(saved.trebleBoost);
        }
        if (typeof saved.preampDb === 'number') {
          setPreampDb(saved.preampDb);
          mainAudioEngine.setPreampDb(saved.preampDb);
        }
        if (saved.preset) {
          setSelectedPreset(saved.preset);
        }
      })
      .catch((err) => console.warn('Could not restore active EQ:', err));

    return () => {
      mounted = false;
    };
  }, []);

  const persistActiveState = (
    currentBands: EqualizerBand[],
    preset: string,
    enabled: boolean,
    bass: number,
    treble: number,
    preamp: number
  ) => {
    void db.setSetting('active_eq_state', {
      enabled,
      preset,
      bands: currentBands.map((b) => b.currentLevelDb),
      bassBoost: bass,
      trebleBoost: treble,
      preampDb: preamp,
    });
  };

  const handleToggleEq = () => {
    const next = !eqEnabled;
    setEqEnabled(next);
    mainAudioEngine.setEqEnabled(next);
    persistActiveState(bands, selectedPreset, next, bassBoost, trebleBoost, preampDb);
  };

  const handleBandChange = (bandId: number, val: number) => {
    mainAudioEngine.setBandLevel(bandId, val);
    const updated = [...mainAudioEngine.getBands()];
    setBands(updated);
    setSelectedPreset('Custom');
    persistActiveState(updated, 'Custom', eqEnabled, bassBoost, trebleBoost, preampDb);
  };

  const handlePresetSelect = (presetName: string) => {
    setSelectedPreset(presetName);
    mainAudioEngine.applyPreset(presetName);
    const updated = [...mainAudioEngine.getBands()];
    setBands(updated);
    persistActiveState(updated, presetName, eqEnabled, bassBoost, trebleBoost, preampDb);
  };

  const handleCustomPresetSelect = (preset: CustomPreset) => {
    mainAudioEngine.applyCustomBands(preset.bands);
    const updated = [...mainAudioEngine.getBands()];
    setBands(updated);
    setSelectedPreset(preset.name);

    const nextBass = typeof preset.bassBoost === 'number' ? preset.bassBoost : bassBoost;
    const nextTreble = typeof preset.trebleBoost === 'number' ? preset.trebleBoost : trebleBoost;
    const nextPreamp = typeof preset.preampDb === 'number' ? preset.preampDb : preampDb;

    if (typeof preset.bassBoost === 'number') {
      setBassBoost(preset.bassBoost);
      mainAudioEngine.setBassBoostLevel(preset.bassBoost);
    }
    if (typeof preset.trebleBoost === 'number') {
      setTrebleBoost(preset.trebleBoost);
      mainAudioEngine.setTrebleBoostLevel(preset.trebleBoost);
    }
    if (typeof preset.preampDb === 'number') {
      setPreampDb(preset.preampDb);
      mainAudioEngine.setPreampDb(preset.preampDb);
    }

    persistActiveState(updated, preset.name, eqEnabled, nextBass, nextTreble, nextPreamp);
    setStatusMessage(
      isArabic ? `تم تفعيل الإعداد: ${preset.name}` : `Activated preset: ${preset.name}`
    );
    setTimeout(() => setStatusMessage(null), 3000);
  };

  const handleReset = () => {
    handlePresetSelect('Flat');
    handleBassBoostChange(0);
    handleTrebleBoostChange(0);
    handlePreampChange(0);
  };

  const handleBassBoostChange = (v: number) => {
    setBassBoost(v);
    mainAudioEngine.setBassBoostLevel(v);
    persistActiveState(bands, selectedPreset, eqEnabled, v, trebleBoost, preampDb);
  };

  const handleTrebleBoostChange = (v: number) => {
    setTrebleBoost(v);
    mainAudioEngine.setTrebleBoostLevel(v);
    persistActiveState(bands, selectedPreset, eqEnabled, bassBoost, v, preampDb);
  };

  const handlePreampChange = (v: number) => {
    setPreampDb(v);
    mainAudioEngine.setPreampDb(v);
    persistActiveState(bands, selectedPreset, eqEnabled, bassBoost, trebleBoost, v);
  };

  const handleSaveCustomPreset = async () => {
    const trimmed = customPresetName.trim();
    if (!trimmed) return;

    const newPreset: CustomPreset = {
      id: 'eq_custom_' + Date.now(),
      name: trimmed,
      bands: bands.map((b) => b.currentLevelDb),
      bassBoost,
      trebleBoost,
      preampDb,
    };

    try {
      await db.saveCustomPreset(newPreset);
      const allPresets = await db.getCustomPresets();
      setCustomPresets(allPresets);
      setSelectedPreset(newPreset.name);
      persistActiveState(bands, newPreset.name, eqEnabled, bassBoost, trebleBoost, preampDb);
      setShowSaveDialog(false);
      setCustomPresetName('');
      setStatusMessage(
        isArabic ? `تم حفظ الإعداد "${trimmed}" بنجاح!` : `Preset "${trimmed}" saved!`
      );
      setTimeout(() => setStatusMessage(null), 3500);
    } catch {
      setStatusMessage(isArabic ? 'تعذر حفظ الإعداد.' : 'Could not save preset.');
    }
  };

  const handleDeleteCustomPreset = async (
    e: React.MouseEvent,
    id: string,
    name: string
  ) => {
    e.stopPropagation();
    try {
      await db.deleteCustomPreset(id);
      const remaining = await db.getCustomPresets();
      setCustomPresets(remaining);
      if (selectedPreset === name) {
        setSelectedPreset('Custom');
      }
      setStatusMessage(
        isArabic ? `تم حذف الإعداد "${name}".` : `Deleted preset "${name}".`
      );
      setTimeout(() => setStatusMessage(null), 3000);
    } catch {
      // ignore
    }
  };

  const handleExportJson = () => {
    const data = {
      presetName: selectedPreset,
      bands: bands.map((b) => b.currentLevelDb),
      bassBoost,
      trebleBoost,
      preampDb,
    };
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `EQ_Preset_${selectedPreset.replace(/\s+/g, '_')}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleImportJson = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (event) => {
      try {
        const json = JSON.parse(event.target?.result as string);
        if (Array.isArray(json.bands)) {
          mainAudioEngine.applyCustomBands(json.bands);
          const updated = [...mainAudioEngine.getBands()];
          setBands(updated);
          const nextBass = typeof json.bassBoost === 'number' ? json.bassBoost : bassBoost;
          const nextTreble =
            typeof json.trebleBoost === 'number' ? json.trebleBoost : trebleBoost;
          const nextPreamp = typeof json.preampDb === 'number' ? json.preampDb : preampDb;
          const nextPresetName = json.presetName || 'Imported';

          if (typeof json.bassBoost === 'number') {
            setBassBoost(json.bassBoost);
            mainAudioEngine.setBassBoostLevel(json.bassBoost);
          }
          if (typeof json.trebleBoost === 'number') {
            setTrebleBoost(json.trebleBoost);
            mainAudioEngine.setTrebleBoostLevel(json.trebleBoost);
          }
          if (typeof json.preampDb === 'number') {
            setPreampDb(json.preampDb);
            mainAudioEngine.setPreampDb(json.preampDb);
          }
          setSelectedPreset(nextPresetName);
          persistActiveState(
            updated,
            nextPresetName,
            eqEnabled,
            nextBass,
            nextTreble,
            nextPreamp
          );
        }
      } catch {
        // ignore invalid file
      }
    };
    reader.readAsText(file);
  };

  const updateBandFromPointer = (bandId: number, clientY: number, rect: DOMRect) => {
    if (!eqEnabled || rect.height <= 0) return;
    const ratio = 1 - Math.max(0, Math.min(1, (clientY - rect.top) / rect.height));
    const rawDb = -15 + ratio * 30;
    const snappedDb = Math.round(rawDb * 2) / 2;
    handleBandChange(bandId, Math.max(-15, Math.min(15, snappedDb)));
  };

  return (
    <div
      className="h-full min-h-0 flex flex-col justify-between overflow-y-auto p-3 gap-2.5 select-none"
      style={{ color: themeColors.textPrimary }}
    >
      {/* Top Control Header */}
      <div
        className="rounded-xl px-3.5 py-2.5 border shrink-0 flex flex-wrap items-center justify-between gap-2 shadow-sm"
        style={{
          backgroundColor: themeColors.surface,
          borderColor: themeColors.border,
        }}
      >
        <div className="flex items-center gap-2.5">
          <div
            className="w-9 h-9 rounded-lg flex items-center justify-center border"
            style={{
              backgroundColor: `${themeColors.primary}20`,
              borderColor: `${themeColors.primary}45`,
            }}
          >
            <Sliders className="w-4 h-4" style={{ color: themeColors.primary }} />
          </div>
          <div>
            <h1 className="text-sm font-extrabold tracking-tight leading-tight">
              {isArabic ? 'معادل الصوت الاحترافي (10-Band EQ)' : 'Pro Studio Equalizer (10-Band EQ)'}
            </h1>
            <p className="text-[11px] leading-tight" style={{ color: themeColors.textMuted }}>
              {isArabic
                ? `الوضع الحالي: ${selectedPreset} • اسحب المفاتيح العمودية لضبط كل تردد`
                : `Active Preset: ${selectedPreset} • Drag vertical fader knobs to sculpt sound`}
            </p>
          </div>
          {statusMessage && (
            <span
              className="text-xs px-2.5 py-1 rounded-md font-bold"
              style={{
                backgroundColor: `${themeColors.primary}25`,
                color: themeColors.primary,
              }}
            >
              {statusMessage}
            </span>
          )}
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          <button
            onClick={() => setShowSaveDialog((prev) => !prev)}
            className="px-3 py-1.5 rounded-lg border text-xs font-bold flex items-center gap-1.5 transition hover:opacity-90"
            style={{
              backgroundColor: showSaveDialog
                ? `${themeColors.primary}25`
                : themeColors.surfaceVariant,
              borderColor: showSaveDialog ? themeColors.primary : themeColors.border,
              color: showSaveDialog ? themeColors.primary : themeColors.textPrimary,
            }}
          >
            <BookmarkPlus className="w-3.5 h-3.5" />
            {isArabic ? 'حفظ الإعداد' : 'Save Preset'}
          </button>

          <button
            onClick={handleReset}
            className="px-3 py-1.5 rounded-lg border text-xs font-bold flex items-center gap-1.5 transition hover:opacity-90"
            style={{
              backgroundColor: themeColors.surfaceVariant,
              borderColor: themeColors.border,
            }}
          >
            <RotateCcw className="w-3.5 h-3.5" />
            {isArabic ? 'تصفير (Flat)' : 'Reset (Flat)'}
          </button>

          <button
            onClick={handleExportJson}
            className="px-3 py-1.5 rounded-lg border text-xs font-bold flex items-center gap-1.5 transition hover:opacity-90"
            style={{
              backgroundColor: themeColors.surfaceVariant,
              borderColor: themeColors.border,
            }}
          >
            <Download className="w-3.5 h-3.5" />
            {isArabic ? 'تصدير' : 'Export'}
          </button>

          <label
            className="px-3 py-1.5 rounded-lg border text-xs font-bold flex items-center gap-1.5 cursor-pointer transition hover:opacity-90"
            style={{
              backgroundColor: themeColors.surfaceVariant,
              borderColor: themeColors.border,
            }}
          >
            <Upload className="w-3.5 h-3.5" />
            {isArabic ? 'استيراد' : 'Import'}
            <input type="file" accept=".json" onChange={handleImportJson} className="hidden" />
          </label>

          <button
            onClick={handleToggleEq}
            className="px-3.5 py-1.5 rounded-lg text-xs font-extrabold flex items-center gap-1.5 shadow-sm transition"
            style={{
              backgroundColor: eqEnabled ? themeColors.primary : themeColors.surfaceVariant,
              color: eqEnabled ? '#ffffff' : themeColors.textMuted,
              border: `1px solid ${eqEnabled ? themeColors.primary : themeColors.border}`,
            }}
          >
            <Power className="w-3.5 h-3.5" />
            {eqEnabled ? (isArabic ? 'مُفعّل ON' : 'ON') : (isArabic ? 'مُعطّل OFF' : 'OFF')}
          </button>
        </div>
      </div>

      {/* Inline Save Preset Bar */}
      {showSaveDialog && (
        <div
          className="rounded-xl p-2.5 border flex items-center gap-2 shrink-0"
          style={{
            backgroundColor: themeColors.surface,
            borderColor: themeColors.primary,
          }}
        >
          <input
            type="text"
            value={customPresetName}
            onChange={(e) => setCustomPresetName(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSaveCustomPreset()}
            placeholder={isArabic ? 'اسم الإعداد المخصص...' : 'Custom preset name...'}
            className="flex-1 px-3 py-1.5 rounded-lg border text-xs outline-none"
            style={{
              backgroundColor: themeColors.background,
              borderColor: themeColors.border,
              color: themeColors.textPrimary,
            }}
            autoFocus
          />
          <button
            onClick={handleSaveCustomPreset}
            disabled={!customPresetName.trim()}
            className="px-3 py-1.5 rounded-lg text-xs font-bold text-white flex items-center gap-1 disabled:opacity-40"
            style={{ backgroundColor: themeColors.primary }}
          >
            <Check className="w-3.5 h-3.5" />
            {isArabic ? 'تأكيد' : 'Confirm'}
          </button>
          <button
            onClick={() => setShowSaveDialog(false)}
            className="p-1.5 rounded-lg border"
            style={{
              backgroundColor: themeColors.surfaceVariant,
              borderColor: themeColors.border,
            }}
          >
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      )}

      {/* Presets Strip */}
      <div
        className="rounded-xl px-3 py-2 border shrink-0 flex flex-wrap items-center gap-1.5"
        style={{
          backgroundColor: themeColors.surface,
          borderColor: themeColors.border,
        }}
      >
        <span
          className="text-[11px] font-extrabold uppercase tracking-wider mr-1"
          style={{ color: themeColors.textMuted }}
        >
          {isArabic ? 'الأوضاع الجاهزة:' : 'Presets:'}
        </span>
        {Object.keys(BUILTIN_PRESETS).map((preset) => {
          const isSelected = selectedPreset === preset;
          return (
            <button
              key={preset}
              onClick={() => handlePresetSelect(preset)}
              className="px-3 py-1 rounded-lg text-xs font-bold transition-all border"
              style={{
                backgroundColor: isSelected
                  ? themeColors.primary
                  : themeColors.surfaceVariant,
                borderColor: isSelected ? themeColors.primary : themeColors.border,
                color: isSelected ? '#ffffff' : themeColors.textPrimary,
              }}
            >
              {isArabic ? PRESET_AR_NAMES[preset] || preset : preset}
            </button>
          );
        })}

        {customPresets.map((preset) => {
          const isSelected = selectedPreset === preset.name;
          return (
            <div
              key={preset.id}
              onClick={() => handleCustomPresetSelect(preset)}
              className="inline-flex items-center gap-1 pl-2.5 pr-1.5 py-1 rounded-lg text-xs font-bold border cursor-pointer"
              style={{
                backgroundColor: isSelected
                  ? themeColors.secondary
                  : themeColors.surfaceVariant,
                borderColor: isSelected ? themeColors.secondary : themeColors.border,
                color: isSelected ? '#ffffff' : themeColors.textPrimary,
              }}
            >
              <span>{preset.name}</span>
              <button
                onClick={(e) => handleDeleteCustomPreset(e, preset.id, preset.name)}
                className="p-0.5 rounded hover:bg-black/20"
              >
                <Trash2 className="w-3 h-3" />
              </button>
            </div>
          );
        })}
      </div>

      {/* 10-Band Vertical Faders Section with Visible Knobs & Unclipped Frequency Labels */}
      <div
        className="flex-1 min-h-[250px] rounded-xl px-4 py-3 border flex flex-col justify-between"
        style={{
          backgroundColor: themeColors.surface,
          borderColor: themeColors.border,
          opacity: eqEnabled ? 1 : 0.55,
        }}
      >
        <div className="flex items-center justify-between shrink-0 mb-2">
          <div className="flex items-center gap-2">
            <span className="text-xs font-extrabold">
              {isArabic ? 'مفاتيح الترددات (10-Band Graphic EQ)' : 'Frequency Bands (10-Band Graphic EQ)'}
            </span>
            <span
              className="text-[10px] px-2 py-0.5 rounded font-mono font-semibold"
              style={{
                backgroundColor: themeColors.surfaceVariant,
                color: themeColors.textSecondary,
              }}
            >
              31Hz — 16kHz
            </span>
          </div>
          <div className="flex items-center gap-3 text-[11px] font-mono" style={{ color: themeColors.textMuted }}>
            <span>{isArabic ? 'الأدنى: -15 dB' : 'Min: -15 dB'}</span>
            <span>{isArabic ? 'المنتصف: 0 dB' : 'Center: 0 dB'}</span>
            <span>{isArabic ? 'الأقصى: +15 dB' : 'Max: +15 dB'}</span>
          </div>
        </div>

        <div className="flex-1 min-h-[190px] grid grid-cols-10 gap-2 sm:gap-3 items-stretch">
          {bands.map((band) => {
            const pct = Math.max(0, Math.min(100, ((band.currentLevelDb + 15) / 30) * 100));
            return (
              <div
                key={band.id}
                className="h-full flex flex-col items-center justify-between rounded-xl px-1.5 py-2 border"
                style={{
                  backgroundColor: `${themeColors.surfaceVariant}65`,
                  borderColor: themeColors.border,
                }}
              >
                {/* Top dB Readout Badge */}
                <span
                  className="text-[11px] font-mono font-extrabold px-1.5 py-0.5 rounded shrink-0"
                  style={{
                    backgroundColor:
                      band.currentLevelDb !== 0 ? `${themeColors.primary}22` : 'transparent',
                    color:
                      band.currentLevelDb > 0
                        ? themeColors.primary
                        : band.currentLevelDb < 0
                        ? themeColors.secondary
                        : themeColors.textSecondary,
                  }}
                >
                  {band.currentLevelDb > 0
                    ? `+${band.currentLevelDb.toFixed(1)}`
                    : band.currentLevelDb.toFixed(1)}
                </span>

                {/* Vertical Fader Track with Prominent Visible Hardware Knob */}
                <div
                  role="slider"
                  aria-label={`${band.name} Equalizer Band`}
                  aria-valuemin={-15}
                  aria-valuemax={15}
                  aria-valuenow={band.currentLevelDb}
                  tabIndex={eqEnabled ? 0 : -1}
                  onPointerDown={(e) => {
                    if (!eqEnabled) return;
                    e.currentTarget.setPointerCapture(e.pointerId);
                    updateBandFromPointer(band.id, e.clientY, e.currentTarget.getBoundingClientRect());
                  }}
                  onPointerMove={(e) => {
                    if (!eqEnabled || (e.buttons & 1) === 0) return;
                    updateBandFromPointer(band.id, e.clientY, e.currentTarget.getBoundingClientRect());
                  }}
                  onDoubleClick={() => eqEnabled && handleBandChange(band.id, 0)}
                  className="relative flex-1 w-full min-h-[120px] my-1.5 flex items-center justify-center cursor-ns-resize touch-none"
                  title={
                    isArabic
                      ? `${band.name}: اسحب للأعلى أو الأسفل (نقر مزدوج للتصفير)`
                      : `${band.name}: Drag up/down (Double-click to reset to 0 dB)`
                  }
                >
                  {/* Tick marks (-15, 0, +15) */}
                  <div
                    className="absolute inset-x-2 top-0 border-t border-dashed opacity-25 pointer-events-none"
                    style={{ borderColor: themeColors.textMuted }}
                  />
                  <div
                    className="absolute inset-x-1 top-1/2 -translate-y-1/2 border-t-2 opacity-50 pointer-events-none"
                    style={{ borderColor: themeColors.textMuted }}
                  />
                  <div
                    className="absolute inset-x-2 bottom-0 border-t border-dashed opacity-25 pointer-events-none"
                    style={{ borderColor: themeColors.textMuted }}
                  />

                  {/* Vertical Rail */}
                  <div
                    className="w-2.5 h-full rounded-full overflow-hidden relative border"
                    style={{
                      backgroundColor: themeColors.background,
                      borderColor: themeColors.border,
                    }}
                  >
                    {/* Active Fill Bar */}
                    <div
                      className="absolute bottom-0 left-0 right-0 rounded-full transition-all duration-75"
                      style={{
                        height: `${pct}%`,
                        background: `linear-gradient(to top, ${themeColors.primary}, ${themeColors.secondary})`,
                      }}
                    />
                  </div>

                  {/* Prominent Hardware Fader Cap / Knob */}
                  <div
                    className="absolute left-1/2 -translate-x-1/2 w-7 h-4 rounded-md border-2 shadow-md flex items-center justify-center pointer-events-none transition-all duration-75"
                    style={{
                      bottom: `calc(${pct}% - 8px)`,
                      backgroundColor: themeColors.surface,
                      borderColor:
                        band.currentLevelDb !== 0 ? themeColors.primary : themeColors.textSecondary,
                      boxShadow: `0 2px 8px rgba(0,0,0,0.45)`,
                    }}
                  >
                    <div
                      className="w-4 h-0.5 rounded-full"
                      style={{
                        backgroundColor:
                          band.currentLevelDb !== 0 ? themeColors.primary : themeColors.textPrimary,
                      }}
                    />
                  </div>
                </div>

                {/* Bottom Frequency Label (Always visible & unclipped) */}
                <span
                  className="text-[11px] font-extrabold tracking-tight shrink-0 px-1 py-0.5 rounded"
                  style={{
                    color: themeColors.textPrimary,
                    backgroundColor: themeColors.surface,
                  }}
                >
                  {band.name}
                </span>
              </div>
            );
          })}
        </div>
      </div>

      {/* Bottom 3-Column Enhancers Row (Preamp, Bass Boost, Treble Clarity) */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-3 shrink-0">
        {/* Preamp */}
        <div
          className="rounded-xl px-3.5 py-2.5 border flex flex-col gap-2"
          style={{
            backgroundColor: themeColors.surface,
            borderColor: themeColors.border,
          }}
        >
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-1.5">
              <Volume2 className="w-4 h-4" style={{ color: themeColors.primary }} />
              <span className="font-bold text-xs">
                {isArabic ? 'مضخم الصوت الأولي (Preamp)' : 'Preamp Gain'}
              </span>
            </div>
            <div className="flex items-center gap-1.5">
              <span
                className="text-xs font-mono font-extrabold px-2 py-0.5 rounded"
                style={{
                  color: themeColors.primary,
                  backgroundColor: `${themeColors.primary}20`,
                }}
              >
                {preampDb > 0 ? `+${preampDb}` : preampDb} dB
              </span>
              {preampDb !== 0 && (
                <button
                  onClick={() => handlePreampChange(0)}
                  className="text-[10px] px-1.5 py-0.5 rounded border hover:opacity-80"
                  style={{ borderColor: themeColors.border, color: themeColors.textMuted }}
                >
                  0dB
                </button>
              )}
            </div>
          </div>
          <input
            type="range"
            min={-12}
            max={12}
            step={0.5}
            value={preampDb}
            onChange={(e) => handlePreampChange(Number(e.target.value))}
            className="w-full h-2 rounded-lg cursor-pointer"
            style={{
              accentColor: themeColors.primary,
              backgroundColor: themeColors.surfaceVariant,
            }}
          />
          <div className="flex justify-between text-[10px] font-mono" style={{ color: themeColors.textMuted }}>
            <span>-12 dB</span>
            <span>0 dB</span>
            <span>+12 dB</span>
          </div>
        </div>

        {/* Bass Boost */}
        <div
          className="rounded-xl px-3.5 py-2.5 border flex flex-col gap-2"
          style={{
            backgroundColor: themeColors.surface,
            borderColor: themeColors.border,
          }}
        >
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-1.5">
              <Sparkles className="w-4 h-4" style={{ color: themeColors.secondary }} />
              <span className="font-bold text-xs">
                {isArabic ? 'تعزيز الباص العميق (Bass Boost)' : 'Bass Boost'}
              </span>
            </div>
            <span
              className="text-xs font-mono font-extrabold px-2 py-0.5 rounded"
              style={{
                color: themeColors.secondary,
                backgroundColor: `${themeColors.secondary}20`,
              }}
            >
              {Math.round(bassBoost * 100)}%
            </span>
          </div>
          <input
            type="range"
            min={0}
            max={1}
            step={0.01}
            value={bassBoost}
            onChange={(e) => handleBassBoostChange(Number(e.target.value))}
            className="w-full h-2 rounded-lg cursor-pointer"
            style={{
              accentColor: themeColors.secondary,
              backgroundColor: themeColors.surfaceVariant,
            }}
          />
          <div className="flex justify-between text-[10px] font-mono" style={{ color: themeColors.textMuted }}>
            <span>0%</span>
            <span>50%</span>
            <span>100%</span>
          </div>
        </div>

        {/* Treble Boost */}
        <div
          className="rounded-xl px-3.5 py-2.5 border flex flex-col gap-2"
          style={{
            backgroundColor: themeColors.surface,
            borderColor: themeColors.border,
          }}
        >
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-1.5">
              <Sparkles className="w-4 h-4" style={{ color: themeColors.tertiary }} />
              <span className="font-bold text-xs">
                {isArabic ? 'وضوح التريبل (Treble Clarity)' : 'Treble Clarity'}
              </span>
            </div>
            <span
              className="text-xs font-mono font-extrabold px-2 py-0.5 rounded"
              style={{
                color: themeColors.tertiary,
                backgroundColor: `${themeColors.tertiary}20`,
              }}
            >
              {Math.round(trebleBoost * 100)}%
            </span>
          </div>
          <input
            type="range"
            min={0}
            max={1}
            step={0.01}
            value={trebleBoost}
            onChange={(e) => handleTrebleBoostChange(Number(e.target.value))}
            className="w-full h-2 rounded-lg cursor-pointer"
            style={{
              accentColor: themeColors.tertiary,
              backgroundColor: themeColors.surfaceVariant,
            }}
          />
          <div className="flex justify-between text-[10px] font-mono" style={{ color: themeColors.textMuted }}>
            <span>0%</span>
            <span>50%</span>
            <span>100%</span>
          </div>
        </div>
      </div>
    </div>
  );
};
