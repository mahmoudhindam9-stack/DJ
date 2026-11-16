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

  return (
    <div
      className="h-full min-h-0 flex flex-col justify-between overflow-hidden p-3 gap-2.5 select-none"
      style={{ color: themeColors.textPrimary }}
    >
      {/* Top Control Header (No Subtitles) */}
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
            <Sliders className="w-4 h-4" style={{ color: themeColors.primary }} />
          </div>
          <h1 className="text-sm font-bold tracking-tight">
            {isArabic ? 'معادل الصوت الاحترافي' : 'Pro Studio Equalizer'}
          </h1>
          {statusMessage && (
            <span
              className="text-xs px-2 py-0.5 rounded-md font-semibold"
              style={{
                backgroundColor: `${themeColors.primary}20`,
                color: themeColors.primary,
              }}
            >
              {statusMessage}
            </span>
          )}
        </div>

        <div className="flex items-center gap-1.5 flex-wrap">
          <button
            onClick={() => setShowSaveDialog((prev) => !prev)}
            className="px-2.5 py-1.5 rounded-lg border text-xs font-bold flex items-center gap-1"
            style={{
              backgroundColor: showSaveDialog
                ? `${themeColors.primary}20`
                : themeColors.surfaceVariant,
              borderColor: showSaveDialog ? themeColors.primary : themeColors.border,
              color: showSaveDialog ? themeColors.primary : themeColors.textPrimary,
            }}
          >
            <BookmarkPlus className="w-3.5 h-3.5" />
            {isArabic ? 'حفظ' : 'Save Preset'}
          </button>

          <button
            onClick={handleReset}
            className="px-2.5 py-1.5 rounded-lg border text-xs font-bold flex items-center gap-1"
            style={{
              backgroundColor: themeColors.surfaceVariant,
              borderColor: themeColors.border,
            }}
          >
            <RotateCcw className="w-3.5 h-3.5" />
            {isArabic ? 'تصفير' : 'Reset'}
          </button>

          <button
            onClick={handleExportJson}
            className="px-2.5 py-1.5 rounded-lg border text-xs font-bold flex items-center gap-1"
            style={{
              backgroundColor: themeColors.surfaceVariant,
              borderColor: themeColors.border,
            }}
          >
            <Download className="w-3.5 h-3.5" />
            {isArabic ? 'تصدير' : 'Export'}
          </button>

          <label
            className="px-2.5 py-1.5 rounded-lg border text-xs font-bold flex items-center gap-1 cursor-pointer"
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
            className="px-3 py-1.5 rounded-lg text-xs font-bold flex items-center gap-1.5"
            style={{
              backgroundColor: eqEnabled ? themeColors.primary : themeColors.surfaceVariant,
              color: eqEnabled ? '#ffffff' : themeColors.textMuted,
            }}
          >
            <Power className="w-3.5 h-3.5" />
            {eqEnabled ? 'ON' : 'OFF'}
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
        className="rounded-xl p-2 border shrink-0 flex flex-wrap items-center gap-1.5"
        style={{
          backgroundColor: themeColors.surface,
          borderColor: themeColors.border,
        }}
      >
        {Object.keys(BUILTIN_PRESETS).map((preset) => {
          const isSelected = selectedPreset === preset;
          return (
            <button
              key={preset}
              onClick={() => handlePresetSelect(preset)}
              className="px-2.5 py-1 rounded-lg text-[11px] font-bold transition-all border"
              style={{
                backgroundColor: isSelected
                  ? themeColors.primary
                  : themeColors.surfaceVariant,
                borderColor: isSelected ? themeColors.primary : themeColors.border,
                color: isSelected ? '#ffffff' : themeColors.textPrimary,
              }}
            >
              {preset}
            </button>
          );
        })}

        {customPresets.map((preset) => {
          const isSelected = selectedPreset === preset.name;
          return (
            <div
              key={preset.id}
              onClick={() => handleCustomPresetSelect(preset)}
              className="inline-flex items-center gap-1 pl-2.5 pr-1.5 py-1 rounded-lg text-[11px] font-bold border cursor-pointer"
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

      {/* 10-Band Vertical Sliders (Flexible Middle Section) */}
      <div
        className="flex-1 min-h-0 rounded-xl p-3 border flex flex-col justify-between overflow-hidden"
        style={{
          backgroundColor: themeColors.surface,
          borderColor: themeColors.border,
          opacity: eqEnabled ? 1 : 0.5,
        }}
      >
        <div className="flex items-center justify-between shrink-0 mb-1">
          <span className="text-xs font-bold">
            {isArabic ? 'نطاقات التردد (10-Band EQ)' : 'Frequency Bands (10-Band EQ)'}
          </span>
          <span className="text-[11px] font-mono opacity-60">-15 dB — +15 dB</span>
        </div>

        <div className="flex-1 min-h-0 grid grid-cols-10 gap-2 items-center py-1">
          {bands.map((band) => {
            const pct = ((band.currentLevelDb + 15) / 30) * 100;
            return (
              <div
                key={band.id}
                className="h-full flex flex-col items-center justify-between gap-1"
              >
                <span
                  className="text-[10px] font-mono font-bold"
                  style={{
                    color:
                      band.currentLevelDb > 0
                        ? themeColors.primary
                        : band.currentLevelDb < 0
                        ? themeColors.secondary
                        : themeColors.textMuted,
                  }}
                >
                  {band.currentLevelDb > 0
                    ? `+${band.currentLevelDb.toFixed(1)}`
                    : band.currentLevelDb.toFixed(1)}
                </span>

                <div className="relative flex-1 min-h-[80px] w-7 flex items-center justify-center">
                  <div
                    className="w-2 h-full rounded-full overflow-hidden relative"
                    style={{ backgroundColor: themeColors.surfaceVariant }}
                  >
                    <div
                      className="absolute bottom-0 left-0 right-0 rounded-full transition-all"
                      style={{
                        height: `${pct}%`,
                        background: `linear-gradient(to top, ${themeColors.primary}, ${themeColors.secondary})`,
                      }}
                    />
                  </div>

                  <input
                    type="range"
                    min={-15}
                    max={15}
                    step={0.5}
                    disabled={!eqEnabled}
                    value={band.currentLevelDb}
                    onChange={(e) => handleBandChange(band.id, Number(e.target.value))}
                    className="absolute w-28 h-7 -rotate-90 opacity-0 cursor-pointer"
                  />
                </div>

                <span
                  className="text-[10px] font-semibold"
                  style={{ color: themeColors.textSecondary }}
                >
                  {band.name}
                </span>
              </div>
            );
          })}
        </div>
      </div>

      {/* Bottom 3-Column Enhancers Row */}
      <div className="grid grid-cols-3 gap-2.5 shrink-0">
        {/* Preamp */}
        <div
          className="rounded-xl p-2.5 border flex flex-col gap-1.5"
          style={{
            backgroundColor: themeColors.surface,
            borderColor: themeColors.border,
          }}
        >
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-1.5">
              <Volume2 className="w-4 h-4" style={{ color: themeColors.primary }} />
              <span className="font-bold text-xs">
                {isArabic ? 'مضخم أولي (Preamp)' : 'Preamp Gain'}
              </span>
            </div>
            <span
              className="text-xs font-mono font-bold"
              style={{ color: themeColors.primary }}
            >
              {preampDb > 0 ? `+${preampDb}` : preampDb} dB
            </span>
          </div>
          <input
            type="range"
            min={-12}
            max={12}
            step={0.5}
            value={preampDb}
            onChange={(e) => handlePreampChange(Number(e.target.value))}
            className="w-full h-1.5 rounded-lg appearance-none cursor-pointer"
            style={{
              accentColor: themeColors.primary,
              backgroundColor: themeColors.surfaceVariant,
            }}
          />
        </div>

        {/* Bass Boost */}
        <div
          className="rounded-xl p-2.5 border flex flex-col gap-1.5"
          style={{
            backgroundColor: themeColors.surface,
            borderColor: themeColors.border,
          }}
        >
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-1.5">
              <Sparkles className="w-4 h-4" style={{ color: themeColors.secondary }} />
              <span className="font-bold text-xs">
                {isArabic ? 'تعزيز الباص (Bass)' : 'Bass Boost'}
              </span>
            </div>
            <span
              className="text-xs font-mono font-bold"
              style={{ color: themeColors.secondary }}
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
            className="w-full h-1.5 rounded-lg appearance-none cursor-pointer"
            style={{
              accentColor: themeColors.secondary,
              backgroundColor: themeColors.surfaceVariant,
            }}
          />
        </div>

        {/* Treble Boost */}
        <div
          className="rounded-xl p-2.5 border flex flex-col gap-1.5"
          style={{
            backgroundColor: themeColors.surface,
            borderColor: themeColors.border,
          }}
        >
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-1.5">
              <Sparkles className="w-4 h-4" style={{ color: themeColors.tertiary }} />
              <span className="font-bold text-xs">
                {isArabic ? 'تعزيز التريبل (Treble)' : 'Treble Clarity'}
              </span>
            </div>
            <span
              className="text-xs font-mono font-bold"
              style={{ color: themeColors.tertiary }}
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
            className="w-full h-1.5 rounded-lg appearance-none cursor-pointer"
            style={{
              accentColor: themeColors.tertiary,
              backgroundColor: themeColors.surfaceVariant,
            }}
          />
        </div>
      </div>
    </div>
  );
};
