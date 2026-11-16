import React, { useEffect, useState } from 'react';
import {
  Settings as SettingsIcon,
  Palette,
  Languages,
  Info,
  Check,
  Headphones,
  Monitor,
  Download,
  Sparkles,
  Trash2,
  RefreshCw,
  CheckCircle2,
  AlertCircle,
  CloudDownload,
  Loader2,
  Cloud,
  LogOut,
  Volume2,
} from 'lucide-react';
import { AppThemeOption, CloudPlaybackState, CloudUser, LanguageMode, ThemeColors } from '../../types';
import { APP_VERSION, type UpdateInfo, type UpdateStatus } from '../../services/UpdateService';
import { THEMES } from '../../utils/theme';
import { downloadSetupBat, downloadUninstallBat, downloadShortcutBat } from '../../utils/setupDownloader';
import { cloudSyncService } from '../../services/CloudSyncService';

interface SettingsScreenProps {
  currentTheme: AppThemeOption;
  onThemeChange: (th: AppThemeOption) => void;
  isArabic: boolean;
  languageMode?: LanguageMode;
  onLanguageModeChange?: (mode: LanguageMode) => void;
  onToggleLanguage: () => void;
  themeColors: ThemeColors;
  onOpenSetupModal: () => void;
  autoUpdatesEnabled: boolean;
  onAutoUpdatesEnabledChange: (enabled: boolean) => void;
  updateStatus: UpdateStatus;
  updateInfo: UpdateInfo | null;
  updateMessage: string | null;
  onCheckForUpdates: () => void;
  onInstallUpdate: () => void;
}

export const SettingsScreen: React.FC<SettingsScreenProps> = ({
  currentTheme,
  onThemeChange,
  isArabic,
  languageMode = 'auto',
  onLanguageModeChange,
  onToggleLanguage,
  themeColors,
  onOpenSetupModal,
  autoUpdatesEnabled,
  onAutoUpdatesEnabledChange,
  updateStatus,
  updateInfo,
  updateMessage,
  onCheckForUpdates,
  onInstallUpdate,
}) => {
  const themeList = Object.keys(THEMES) as AppThemeOption[];

  const [cloudUser, setCloudUser] = useState<CloudUser | null>(cloudSyncService.getUser());
  const [cloudPlayback, setCloudPlayback] = useState<CloudPlaybackState | null>(
    cloudSyncService.getPlaybackState()
  );
  const [cloudStatus, setCloudStatus] = useState<string>('Ready');
  const [isSignUp, setIsSignUp] = useState(false);
  const [emailInput, setEmailInput] = useState('');
  const [passwordInput, setPasswordInput] = useState('');
  const [displayNameInput, setDisplayNameInput] = useState('');
  const [authError, setAuthError] = useState<string | null>(null);

  useEffect(() => {
    return cloudSyncService.subscribe(({ user, playbackState, status }) => {
      setCloudUser(user);
      setCloudPlayback(playbackState);
      setCloudStatus(status);
    });
  }, []);

  const handleAuthSubmit = async () => {
    setAuthError(null);
    try {
      if (isSignUp) {
        await cloudSyncService.signUpWithEmail(emailInput, passwordInput, displayNameInput);
      } else {
        await cloudSyncService.signInWithEmail(emailInput, passwordInput);
      }
      setPasswordInput('');
    } catch (err: any) {
      setAuthError(err?.message || 'Authentication failed');
    }
  };

  const handleGoogleSignIn = async () => {
    setAuthError(
      isArabic
        ? 'تسجيل Google السحابي يحتاج إعداد OAuth حقيقي (Client ID) في Vercel. استخدم البريد وكلمة المرور حاليًا.'
        : 'Google Cloud sign-in requires a verified OAuth Client ID configured in Vercel. Use email and password for now.'
    );
  };

  return (
    <div
      id="settings-screen"
      className="flex-1 flex flex-col h-full overflow-y-auto select-none p-4 space-y-6 max-w-4xl mx-auto w-full"
      style={{
        backgroundColor: themeColors.background,
        color: themeColors.textPrimary,
      }}
    >
      {/* Header */}
      <div className="flex items-center gap-2">
        <SettingsIcon className="w-6 h-6" style={{ color: themeColors.primary }} />
        <div>
          <h1 className="text-lg font-black tracking-wide">
            {isArabic ? 'إعدادات التطبيق والمزامنة السحابية' : 'APPLICATION & CLOUD SYNC SETTINGS'}
          </h1>
        </div>
      </div>

      {/* Neon Cloud Authentication & Single Active Output Sync Section */}
      <div
        data-testid="settings-cloud-auth-card"
        className="p-4 rounded-2xl border shadow-lg flex flex-col gap-3"
        style={{
          backgroundColor: themeColors.surface,
          borderColor: themeColors.border,
        }}
      >
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Cloud className="w-5 h-5" style={{ color: themeColors.primary }} />
            <div>
              <h2 className="font-bold text-sm">
                {isArabic
                  ? 'حساب Neon Cloud والمزامنة الفورية مع الهاتف'
                  : 'Neon Cloud Account & Real-Time Android/Web Sync'}
              </h2>
              <p className="text-[11px] opacity-70">{cloudStatus}</p>
            </div>
          </div>
          {cloudUser && (
            <button
              onClick={() => cloudSyncService.signOut()}
              data-testid="web-cloud-signout-btn"
              className="px-3 py-1.5 rounded-xl border text-xs font-bold flex items-center gap-1.5 text-red-400 border-red-500/40 hover:bg-red-500/10"
            >
              <LogOut className="w-3.5 h-3.5" />
              <span>{isArabic ? 'تسجيل الخروج' : 'Sign Out'}</span>
            </button>
          )}
        </div>

        {cloudUser ? (
          <div className="flex flex-col gap-2.5 pt-1">
            <div
              className="p-3 rounded-xl border flex items-center justify-between"
              style={{
                backgroundColor: themeColors.surfaceVariant,
                borderColor: themeColors.border,
              }}
            >
              <div>
                <div className="text-xs font-bold">{cloudUser.displayName}</div>
                <div className="text-[11px] opacity-75">
                  {cloudUser.email} • {cloudUser.authProvider.toUpperCase()} • ID: {cloudUser.userId}
                </div>
              </div>
              <span className="px-2.5 py-1 rounded-full text-[10px] font-bold bg-emerald-500/20 text-emerald-300">
                {isArabic ? 'معزول ومؤمن' : 'Isolated Vault Active'}
              </span>
            </div>

            <div
              className="p-3 rounded-xl border flex flex-wrap items-center justify-between gap-2"
              style={{ borderColor: themeColors.border }}
            >
              <div className="flex items-center gap-2">
                <Volume2 className="w-4 h-4" style={{ color: themeColors.primary }} />
                <div className="text-xs">
                  <div className="font-bold">
                    {isArabic
                      ? 'التحكم في مخرج الصوت النشط (جهاز واحد فقط في نفس الوقت)'
                      : 'Single Active Audio Output Arbitration'}
                  </div>
                  <div className="text-[11px] opacity-70">
                    {cloudPlayback?.activeDeviceId
                      ? `Active Platform: ${cloudPlayback.activeDevicePlatform.toUpperCase()} (${cloudPlayback.activeDeviceId})`
                      : isArabic
                      ? 'الويب جاهز لإخراج الصوت'
                      : 'Web output ready'}
                  </div>
                </div>
              </div>
              <button
                onClick={() =>
                  void cloudSyncService.claimWebAudioOutput({
                    isPlaying: true
                  })
                }
                data-testid="web-claim-audio-output-btn"
                className="px-3 py-1.5 rounded-xl text-xs font-bold text-white"
                style={{ backgroundColor: themeColors.primary }}
              >
                {isArabic ? 'تفعيل الصوت هنا وإيقاف الهاتف' : 'Claim Audio Output on Web'}
              </button>
            </div>
          </div>
        ) : (
          <div className="flex flex-col gap-2.5 pt-1">
            {authError && (
              <div className="text-xs text-red-400 font-semibold">{authError}</div>
            )}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
              {isSignUp && (
                <input
                  type="text"
                  value={displayNameInput}
                  onChange={(e) => setDisplayNameInput(e.target.value)}
                  placeholder={isArabic ? 'الاسم' : 'Display Name'}
                  className="px-3 py-2 rounded-xl border text-xs outline-none"
                  style={{
                    backgroundColor: themeColors.background,
                    borderColor: themeColors.border,
                    color: themeColors.textPrimary,
                  }}
                />
              )}
              <input
                type="email"
                value={emailInput}
                onChange={(e) => setEmailInput(e.target.value)}
                placeholder={isArabic ? 'البريد الإلكتروني' : 'Email address'}
                data-testid="web-auth-email-input"
                className="px-3 py-2 rounded-xl border text-xs outline-none"
                style={{
                  backgroundColor: themeColors.background,
                  borderColor: themeColors.border,
                  color: themeColors.textPrimary,
                }}
              />
              <input
                type="password"
                value={passwordInput}
                onChange={(e) => setPasswordInput(e.target.value)}
                placeholder={isArabic ? 'كلمة المرور' : 'Password'}
                data-testid="web-auth-password-input"
                className="px-3 py-2 rounded-xl border text-xs outline-none"
                style={{
                  backgroundColor: themeColors.background,
                  borderColor: themeColors.border,
                  color: themeColors.textPrimary,
                }}
              />
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <button
                onClick={() => void handleAuthSubmit()}
                data-testid="web-auth-submit-btn"
                className="px-4 py-2 rounded-xl text-xs font-bold text-white"
                style={{ backgroundColor: themeColors.primary }}
              >
                {isSignUp
                  ? isArabic
                    ? 'إنشاء حساب'
                    : 'Sign Up'
                  : isArabic
                  ? 'تسجيل الدخول'
                  : 'Sign In'}
              </button>
              <button
                onClick={() => void handleGoogleSignIn()}
                data-testid="web-auth-google-btn"
                className="px-4 py-2 rounded-xl border text-xs font-bold"
                style={{
                  backgroundColor: themeColors.surfaceVariant,
                  borderColor: themeColors.border,
                }}
              >
                {isArabic ? 'الدخول بحساب Google' : 'Continue with Google'}
              </button>
              <button
                onClick={() => setIsSignUp(!isSignUp)}
                className="px-3 py-2 rounded-xl text-xs underline opacity-80"
              >
                {isSignUp
                  ? isArabic
                    ? 'لدي حساب بالفعل'
                    : 'Already have an account? Sign In'
                  : isArabic
                  ? 'إنشاء حساب جديد'
                  : 'Create new account'}
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Language Section (Auto / Arabic RTL / English LTR) */}
      <div
        className="p-4 rounded-2xl border shadow-lg flex flex-col gap-3"
        style={{
          backgroundColor: themeColors.surface,
          borderColor: themeColors.border,
        }}
      >
        <div className="flex items-center gap-2">
          <Languages className="w-5 h-5" style={{ color: themeColors.primary }} />
          <h2 className="font-bold text-sm">
            {isArabic ? 'اللغة والاتجاه (تلقائي / عربي / إنجليزي)' : 'Language & Layout Direction (Auto / Arabic / English)'}
          </h2>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mt-1">
          <button
            onClick={() => onLanguageModeChange ? onLanguageModeChange('auto') : onToggleLanguage()}
            data-testid="lang-mode-auto"
            className={`p-3 rounded-xl border flex items-center justify-between text-xs font-bold transition-all ${
              languageMode === 'auto' ? 'ring-2' : 'opacity-70 hover:opacity-100'
            }`}
            style={{
              backgroundColor: languageMode === 'auto' ? `${themeColors.primary}20` : themeColors.surfaceVariant,
              borderColor: languageMode === 'auto' ? themeColors.primary : themeColors.border,
            }}
          >
            <div>{isArabic ? 'تلقائي حسب لغة الجهاز (Auto)' : 'Auto (System Language)'}</div>
            {languageMode === 'auto' && <Check className="w-4 h-4" style={{ color: themeColors.primary }} />}
          </button>

          <button
            onClick={() => onLanguageModeChange ? onLanguageModeChange('en') : (isArabic && onToggleLanguage())}
            data-testid="lang-mode-en"
            className={`p-3 rounded-xl border flex items-center justify-between text-xs font-bold transition-all ${
              languageMode === 'en' ? 'ring-2' : 'opacity-70 hover:opacity-100'
            }`}
            style={{
              backgroundColor: languageMode === 'en' ? `${themeColors.primary}20` : themeColors.surfaceVariant,
              borderColor: languageMode === 'en' ? themeColors.primary : themeColors.border,
            }}
          >
            <div className="text-left">
              <div>English (Left-to-Right)</div>
            </div>
            {languageMode === 'en' && <Check className="w-4 h-4" style={{ color: themeColors.primary }} />}
          </button>

          <button
            onClick={() => onLanguageModeChange ? onLanguageModeChange('ar') : (!isArabic && onToggleLanguage())}
            data-testid="lang-mode-ar"
            className={`p-3 rounded-xl border flex items-center justify-between text-xs font-bold transition-all ${
              languageMode === 'ar' ? 'ring-2' : 'opacity-70 hover:opacity-100'
            }`}
            style={{
              backgroundColor: languageMode === 'ar' ? `${themeColors.primary}20` : themeColors.surfaceVariant,
              borderColor: languageMode === 'ar' ? themeColors.primary : themeColors.border,
            }}
          >
            <div className="text-right">
              <div>العربية (من اليمين إلى اليسار)</div>
            </div>
            {languageMode === 'ar' && <Check className="w-4 h-4" style={{ color: themeColors.primary }} />}
          </button>
        </div>
      </div>

      {/* Themes Palette Section */}
      <div
        className="p-4 rounded-2xl border shadow-lg flex flex-col gap-3"
        style={{
          backgroundColor: themeColors.surface,
          borderColor: themeColors.border,
        }}
      >
        <div className="flex items-center gap-2">
          <Palette className="w-5 h-5" style={{ color: themeColors.primary }} />
          <h2 className="font-bold text-sm">
            {isArabic ? 'ثيمات وألوان التطبيق (Themes)' : 'Color Theme Engine'}
          </h2>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-2.5 mt-1">
          {themeList.map((thKey) => {
            const th = THEMES[thKey];
            const isSelected = currentTheme === thKey;
            return (
              <button
                key={thKey}
                onClick={() => onThemeChange(thKey)}
                className={`p-2.5 rounded-xl border flex flex-col gap-2 text-left transition-all ${
                  isSelected ? 'ring-2' : 'hover:scale-[1.02]'
                }`}
                style={{
                  backgroundColor: th.surface,
                  borderColor: isSelected ? th.primary : themeColors.border,
                }}
              >
                <div className="flex items-center justify-between">
                  <span
                    className="w-3.5 h-3.5 rounded-full shadow-sm"
                    style={{ backgroundColor: th.primary }}
                  />
                  <span
                    className="w-2.5 h-2.5 rounded-full"
                    style={{ backgroundColor: th.secondary }}
                  />
                  <span
                    className="w-2.5 h-2.5 rounded-full"
                    style={{ backgroundColor: th.tertiary }}
                  />
                </div>
                <div className="min-w-0">
                  <div
                    className="text-xs font-bold truncate"
                    style={{ color: th.textPrimary }}
                  >
                    {thKey.replace(/_/g, ' ')}
                  </div>
                  <div className="text-[9px] opacity-60" style={{ color: th.textMuted }}>
                    {th.isDark ? 'Dark Mode' : 'Light Mode'}
                  </div>
                </div>
              </button>
            );
          })}
        </div>
      </div>

      {/* Audio Engine Info */}
      <div
        className="p-4 rounded-2xl border shadow-lg flex flex-col gap-3"
        style={{
          backgroundColor: themeColors.surface,
          borderColor: themeColors.border,
        }}
      >
        <div className="flex items-center gap-2">
          <Headphones className="w-5 h-5" style={{ color: themeColors.primary }} />
          <h2 className="font-bold text-sm">
            {isArabic ? 'محرك الصوت ومعالجة الإشارات DSP' : 'Audio DSP & Hardware Routing'}
          </h2>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
          <div className="p-3 rounded-xl border" style={{ borderColor: themeColors.border }}>
            <span className="opacity-60 text-[10px]">SAMPLE RATE</span>
            <div className="font-mono font-bold text-sm mt-0.5">48,000 Hz (48 kHz)</div>
          </div>
          <div className="p-3 rounded-xl border" style={{ borderColor: themeColors.border }}>
            <span className="opacity-60 text-[10px]">DSP EQUALIZER</span>
            <div className="font-mono font-bold text-sm mt-0.5">10-Band Biquad Peaking</div>
          </div>
          <div className="p-3 rounded-xl border" style={{ borderColor: themeColors.border }}>
            <span className="opacity-60 text-[10px]">BUFFER LATENCY</span>
            <div className="font-mono font-bold text-sm mt-0.5">&lt; 15 ms (Ultra-Low)</div>
          </div>
        </div>
      </div>

      {/* Desktop Setup & Shortcut Installer */}
      <div
        className="p-5 rounded-2xl border shadow-lg flex flex-col gap-4 relative overflow-hidden"
        style={{
          backgroundColor: themeColors.surface,
          borderColor: themeColors.border,
        }}
      >
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Monitor className="w-5 h-5 text-emerald-400" />
            <h2 className="font-bold text-sm">
              {isArabic ? 'تثبيت البرنامج وملف Setup لسطح المكتب' : 'Desktop Setup & Shortcut Installer'}
            </h2>
          </div>
          <span className="text-[10px] px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-400 font-bold">
            Windows / PC
          </span>
        </div>

        <div className="flex flex-wrap items-center gap-3 pt-1">
          <button
            onClick={onOpenSetupModal}
            className="py-2.5 px-4 rounded-xl text-xs font-bold text-white flex items-center gap-2 shadow-md transition-all active:scale-95 hover:opacity-90"
            style={{ backgroundColor: themeColors.primary }}
          >
            <Sparkles className="w-4 h-4" />
            <span>{isArabic ? 'مركز الإعداد والتثبيت' : 'Setup & Install Hub'}</span>
          </button>

          <button
            onClick={downloadSetupBat}
            className="py-2.5 px-4 rounded-xl text-xs font-bold border flex items-center gap-2 transition-all hover:bg-white/10"
            style={{
              borderColor: 'rgba(16, 185, 129, 0.4)',
              backgroundColor: 'rgba(16, 185, 129, 0.1)',
              color: '#34d399',
            }}
          >
            <Download className="w-4 h-4" />
            <span>{isArabic ? 'تحميل (setup.bat)' : 'Download setup.bat'}</span>
          </button>

          <button
            onClick={downloadShortcutBat}
            className="py-2.5 px-4 rounded-xl text-xs font-bold border flex items-center gap-2 transition-all hover:bg-white/10"
            style={{
              borderColor: 'rgba(59, 130, 246, 0.4)',
              backgroundColor: 'rgba(59, 130, 246, 0.1)',
              color: '#60a5fa',
            }}
          >
            <Download className="w-4 h-4" />
            <span>{isArabic ? 'الأيقونة فقط (.bat)' : 'Shortcut only (.bat)'}</span>
          </button>

          <button
            onClick={downloadUninstallBat}
            className="py-2.5 px-4 rounded-xl text-xs font-bold border flex items-center gap-2 transition-all hover:bg-white/10"
            style={{
              borderColor: 'rgba(239, 68, 68, 0.4)',
              backgroundColor: 'rgba(239, 68, 68, 0.1)',
              color: '#f87171',
            }}
          >
            <Trash2 className="w-4 h-4" />
            <span>{isArabic ? 'تحميل (uninstall.bat)' : 'Download uninstall.bat'}</span>
          </button>
        </div>
      </div>

      {/* Updates */}
      <div className="p-5 rounded-2xl border shadow-lg flex flex-col gap-4" style={{ backgroundColor: themeColors.surface, borderColor: themeColors.border }}>
        <div className="flex items-center gap-2">
          <RefreshCw className="w-5 h-5" style={{ color: themeColors.primary }} />
          <div>
            <h2 className="font-bold text-sm">{isArabic ? 'التحديثات والإصدار' : 'Updates & Version'}</h2>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div className="rounded-xl border p-3" style={{ borderColor: themeColors.border }}>
            <div className="text-[10px] opacity-60">{isArabic ? 'الإصدار الحالي' : 'CURRENT VERSION'}</div>
            <div className="font-mono text-sm font-bold mt-1">v{updateInfo?.currentVersion || APP_VERSION}</div>
          </div>
          <div className="rounded-xl border p-3" style={{ borderColor: themeColors.border }}>
            <div className="text-[10px] opacity-60">{isArabic ? 'أحدث إصدار' : 'LATEST VERSION'}</div>
            <div className="font-mono text-sm font-bold mt-1">v{updateInfo?.latestVersion || '—'}</div>
          </div>
        </div>
        <label className="flex items-center gap-3 rounded-xl border p-3 cursor-pointer" style={{ borderColor: themeColors.border }}>
          <input type="checkbox" checked={autoUpdatesEnabled} onChange={(event) => onAutoUpdatesEnabledChange(event.target.checked)} className="h-4 w-4" />
          <span className="min-w-0">
            <span className="block text-xs font-bold">{isArabic ? 'البحث والتحديث التلقائي' : 'Automatic update checks and installation'}</span>
          </span>
        </label>
        <div className="flex flex-wrap items-center gap-2">
          <button onClick={onCheckForUpdates} disabled={updateStatus === 'checking' || updateStatus === 'installing'} className="px-4 py-2.5 rounded-xl border text-xs font-bold flex items-center gap-2 disabled:opacity-50" style={{ borderColor: themeColors.border, backgroundColor: themeColors.surfaceVariant }}>
            {updateStatus === 'checking' ? <Loader2 className="w-4 h-4 animate-spin" /> : <RefreshCw className="w-4 h-4" />}
            {isArabic ? 'البحث عن تحديثات الآن' : 'Check for updates now'}
          </button>
          {updateInfo?.updateAvailable && <button onClick={onInstallUpdate} disabled={updateStatus === 'installing'} className="px-4 py-2.5 rounded-xl text-xs font-bold text-white flex items-center gap-2 disabled:opacity-50" style={{ backgroundColor: themeColors.primary }}>
            {updateStatus === 'installing' ? <Loader2 className="w-4 h-4 animate-spin" /> : <CloudDownload className="w-4 h-4" />}
            {isArabic ? 'تنزيل وتثبيت التحديث' : 'Download & install update'}
          </button>}
        </div>
        {updateMessage && <div role="status" className="rounded-xl border p-3 text-xs flex items-start gap-2" style={{ borderColor: updateStatus === 'error' ? '#ef4444' : themeColors.border, color: updateStatus === 'error' ? '#ef4444' : themeColors.textPrimary, backgroundColor: themeColors.surfaceVariant }}>
          {updateStatus === 'error' ? <AlertCircle className="w-4 h-4 shrink-0" /> : updateStatus === 'upToDate' ? <CheckCircle2 className="w-4 h-4 shrink-0" /> : <RefreshCw className="w-4 h-4 shrink-0" />}
          <span>{updateMessage}</span>
        </div>}
        {updateInfo?.updateAvailable && !updateInfo.canAutoInstall && <button onClick={() => window.open(updateInfo.releaseUrl, '_blank', 'noopener,noreferrer')} className="text-left text-xs underline opacity-80">{isArabic ? 'فتح صفحة الإصدار للتنزيل اليدوي' : 'Open release page for manual download'}</button>}
      </div>

      {/* About Box */}
      <div
        className="p-4 rounded-2xl border shadow-lg flex flex-col gap-2"
        style={{
          backgroundColor: themeColors.surface,
          borderColor: themeColors.border,
        }}
      >
        <div className="flex items-center gap-2">
          <Info className="w-5 h-5" style={{ color: themeColors.primary }} />
          <h2 className="font-bold text-sm">{isArabic ? 'عن التطبيق' : 'About DJ Desktop'}</h2>
        </div>
        <p className="text-xs leading-relaxed" style={{ color: themeColors.textMuted }}>
          DJ Desktop v3.6.8 — Standalone Windows Desktop Audio Station. Remixed from the original
          Android DJ Suite into a high-performance desktop workstation with full feature parity: Dual
          DJ Decks with pitch control, 64-pad soundboard sampler, 10-band Dolby EQ, live hardware
          Karaoke monitoring with DSP filters, online cloud streaming, and direct Egypt & World Radio
          FM.
        </p>
      </div>
    </div>
  );
};
