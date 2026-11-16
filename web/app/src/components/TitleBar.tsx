import React from 'react';
import {
  Minus,
  Square,
  X,
  Radio,
  Sliders,
  Mic,
  Disc,
  Music,
  Globe,
  BookOpen,
  Settings,
  Languages,
  Palette,
  Sparkles,
} from 'lucide-react';
import { TabType, AppThemeOption, ThemeColors } from '../types';
import { THEMES } from '../utils/theme';
import { APP_VERSION } from '../services/UpdateService';

interface TitleBarProps {
  activeTab: TabType;
  onTabChange: (tab: TabType) => void;
  currentTheme: AppThemeOption;
  onThemeChange: (theme: AppThemeOption) => void;
  isArabic: boolean;
  onToggleLanguage: () => void;
  themeColors: ThemeColors;
  onOpenSetupModal: () => void;
  onOpenControlPanel?: () => void;
}

export const TitleBar: React.FC<TitleBarProps> = ({
  activeTab,
  onTabChange,
  currentTheme,
  onThemeChange,
  isArabic,
  onToggleLanguage,
  themeColors,
}) => {
  const tabs: Array<{ id: TabType; labelEn: string; labelAr: string; icon: React.ReactNode }> = [
    { id: 'LIBRARY', labelEn: 'Library', labelAr: 'المكتبة', icon: <Music className="w-4 h-4" /> },
    { id: 'DJ_MIXER', labelEn: 'DJ Studio', labelAr: 'دي جي ميكسر', icon: <Disc className="w-4 h-4" /> },
    { id: 'EQUALIZER', labelEn: 'Equalizer', labelAr: 'المعادل الصوتي', icon: <Sliders className="w-4 h-4" /> },
    { id: 'KARAOKE', labelEn: 'Karaoke', labelAr: 'كاريوكي استوديو', icon: <Mic className="w-4 h-4" /> },
    { id: 'RADIO', labelEn: 'Radio FM', labelAr: 'راديو مباشر', icon: <Radio className="w-4 h-4" /> },
    { id: 'ONLINE_MUSIC', labelEn: 'Online Music', labelAr: 'موسيقى أونلاين', icon: <Globe className="w-4 h-4" /> },
    { id: 'QURAN', labelEn: 'Holy Quran', labelAr: 'القرآن الكريم', icon: <BookOpen className="w-4 h-4" /> },
    { id: 'AI_STUDIO', labelEn: 'AI Studio', labelAr: 'استوديو الذكاء الاصطناعي', icon: <Sparkles className="w-4 h-4" /> },
    { id: 'SETTINGS', labelEn: 'Settings', labelAr: 'الإعدادات', icon: <Settings className="w-4 h-4" /> },
  ];

  return (
    <header
      id="dj-titlebar"
      className="flex items-center justify-between px-3 py-1.5 border-b select-none text-xs z-50 shrink-0"
      style={{
        backgroundColor: themeColors.surface,
        borderColor: themeColors.border,
        color: themeColors.textPrimary,
      }}
    >
      {/* Left / Brand Info */}
      <div className="flex items-center gap-2">
        <img
          src="/icon.jpg"
          alt="DJ Desktop"
          className="w-5 h-5 rounded-full object-cover shadow-sm"
          onError={(e) => {
            (e.target as HTMLElement).style.display = 'none';
          }}
        />
        <span className="font-bold tracking-wider text-sm flex items-center gap-1.5">
          <span style={{ color: themeColors.primary }}>DJ</span> DESKTOP
        </span>
        <span
          className="px-1.5 py-0.2 rounded text-[10px] font-semibold opacity-75"
          style={{ backgroundColor: themeColors.surfaceVariant }}
        >
          v{APP_VERSION}
        </span>
      </div>

      {/* Navigation Tabs */}
      <nav className="flex items-center gap-1">
        {tabs.map((tab) => {
          const isActive = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              onClick={() => onTabChange(tab.id)}
              className={`flex items-center gap-1.5 px-2.5 py-1 rounded transition-all font-medium ${
                isActive ? 'shadow-sm' : 'hover:opacity-80'
              }`}
              style={{
                backgroundColor: isActive ? themeColors.primary : 'transparent',
                color: isActive ? '#ffffff' : themeColors.textSecondary,
              }}
              data-active={isActive ? 'true' : undefined}
              title={isArabic ? tab.labelAr : tab.labelEn}
            >
              {tab.icon}
              <span className="hidden md:inline">{isArabic ? tab.labelAr : tab.labelEn}</span>
            </button>
          );
        })}
      </nav>

      {/* Right Controls: Quick Theme, Language, Window buttons */}
      <div className="flex items-center gap-1.5">

        {/* Language switch */}
        <button
          onClick={onToggleLanguage}
          className="p-1.5 rounded hover:opacity-80 transition-opacity flex items-center gap-1 text-[11px]"
          style={{ backgroundColor: themeColors.surfaceVariant }}
          title={isArabic ? 'Switch to English' : 'التحويل إلى العربية'}
        >
          <Languages className="w-3.5 h-3.5" />
          <span>{isArabic ? 'EN' : 'عربي'}</span>
        </button>

        {/* Quick Theme Selector Dropdown */}
        <div className="relative group">
          <button
            className="p-1.5 rounded hover:opacity-80 transition-opacity flex items-center gap-1"
            style={{ backgroundColor: themeColors.surfaceVariant }}
            title="App Theme"
          >
            <Palette className="w-3.5 h-3.5" />
          </button>
          <div
            className="absolute right-0 top-full mt-1 hidden group-hover:block p-1.5 rounded shadow-xl border w-44 z-50 text-[11px]"
            style={{
              backgroundColor: themeColors.surface,
              borderColor: themeColors.border,
            }}
          >
            <div className="font-semibold px-2 py-1 opacity-60">Color Themes</div>
            {Object.keys(THEMES).map((themeKey) => {
              const th = THEMES[themeKey as AppThemeOption];
              return (
                <button
                  key={themeKey}
                  onClick={() => onThemeChange(themeKey as AppThemeOption)}
                  className="w-full text-left px-2 py-1 rounded flex items-center justify-between hover:bg-white/10"
                >
                  <span className={currentTheme === themeKey ? 'font-bold' : ''}>
                    {themeKey.replace(/_/g, ' ')}
                  </span>
                  <span
                    className="w-2.5 h-2.5 rounded-full"
                    style={{ backgroundColor: th.primary }}
                  />
                </button>
              );
            })}
          </div>
        </div>

        {/* Windows Titlebar Mock controls (Tauri native-compatible) */}
        <div className="flex items-center ml-1">
          <button
            className="p-1.5 hover:bg-white/10 rounded transition-colors"
            title="Minimize"
            onClick={() => {}}
          >
            <Minus className="w-3.5 h-3.5" />
          </button>
          <button
            className="p-1.5 hover:bg-white/10 rounded transition-colors"
            title="Maximize"
            onClick={() => {
              if (document.fullscreenElement) {
                document.exitFullscreen().catch(() => {});
              } else {
                document.documentElement.requestFullscreen().catch(() => {});
              }
            }}
          >
            <Square className="w-3 h-3" />
          </button>
          <button
            className="p-1.5 hover:bg-red-500 hover:text-white rounded transition-colors"
            title="Close"
            onClick={() => {}}
          >
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>
    </header>
  );
};
