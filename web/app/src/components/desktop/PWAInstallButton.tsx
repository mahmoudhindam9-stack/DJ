import React, { useState } from 'react';
import { Download, Monitor, Sparkles, Check } from 'lucide-react';
import { usePWAInstall } from '../../hooks/usePWAInstall';
import { ThemeColors } from '../../types';

interface PWAInstallButtonProps {
  themeColors: ThemeColors;
  isArabic: boolean;
  onOpenSetupModal?: () => void;
}

export const PWAInstallButton: React.FC<PWAInstallButtonProps> = ({
  themeColors,
  isArabic,
  onOpenSetupModal,
}) => {
  const { isInstallable, isInstalled, install, isIOS } = usePWAInstall();
  const [showIOSGuide, setShowIOSGuide] = useState(false);

  // Keep a visible desktop-installed status and allow reopening setup options.
  if (isInstalled) {
    return (
      <button
        type="button"
        onClick={onOpenSetupModal}
        title={isArabic ? 'مثبت على سطح المكتب' : 'Installed on Desktop'}
        className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold border transition-all hover:bg-white/10"
        style={{
          borderColor: 'rgba(16, 185, 129, 0.4)',
          backgroundColor: 'rgba(16, 185, 129, 0.15)',
          color: '#34d399',
        }}
      >
        <Check className="w-3.5 h-3.5" />
        <span>{isArabic ? 'مثبت لسطح المكتب' : 'Desktop Installed'}</span>
      </button>
    );
  }

  // If browser has prompted beforeinstallprompt
  if (isInstallable) {
    return (
      <button
        onClick={async () => {
          const ok = await install();
          if (!ok && onOpenSetupModal) {
            onOpenSetupModal();
          }
        }}
        title={isArabic ? 'تثبيت البرنامج على سطح المكتب' : 'Install DJ Desktop to Desktop'}
        className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold text-white shadow-md transition-all active:scale-95 animate-pulse"
        style={{
          backgroundColor: themeColors.primary,
        }}
      >
        <Download className="w-3.5 h-3.5" />
        <span>{isArabic ? 'تثبيت لسطح المكتب' : 'Install App'}</span>
      </button>
    );
  }

  // Fallback button to open the Setup & Desktop Installer Modal
  return (
    <>
      <button
        onClick={onOpenSetupModal}
        title={isArabic ? 'تثبيت البرنامج وملف Setup' : 'Desktop Setup & Installer'}
        className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-xl text-xs font-medium border transition-all hover:bg-white/10"
        style={{
          borderColor: themeColors.border,
          backgroundColor: themeColors.surfaceVariant,
          color: themeColors.textPrimary,
        }}
      >
        <Monitor className="w-3.5 h-3.5 text-emerald-400" />
        <span className="hidden sm:inline">
          {isArabic ? 'تثبيت لسطح المكتب' : 'Desktop Setup'}
        </span>
      </button>

      {/* iOS Modal if triggered */}
      {showIOSGuide && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4"
          dir={isArabic ? 'rtl' : 'ltr'}
        >
          <div
            className="w-full max-w-sm rounded-2xl p-6 shadow-2xl border text-center space-y-4"
            style={{
              backgroundColor: themeColors.surface,
              borderColor: themeColors.border,
              color: themeColors.textPrimary,
            }}
          >
            <div className="w-12 h-12 mx-auto rounded-full bg-blue-500/20 text-blue-400 flex items-center justify-center">
              <Sparkles className="w-6 h-6" />
            </div>
            <h3 className="text-base font-bold">
              {isArabic ? 'تثبيت على الهاتف أو الحاسب' : 'Add to Home Screen'}
            </h3>
            <p className="text-xs opacity-80 leading-relaxed">
              {isArabic
                ? 'اضغط على زر المشاركة (Share) في المتصفح، ثم اختر "إضافة إلى الشاشة الرئيسية" (Add to Home Screen).'
                : 'Tap the Share icon in your browser toolbar, then tap "Add to Home Screen".'}
            </p>
            <button
              onClick={() => setShowIOSGuide(false)}
              className="w-full py-2 rounded-xl text-xs font-bold text-white bg-blue-600 hover:bg-blue-500 transition-colors"
            >
              {isArabic ? 'فهمت' : 'Got it'}
            </button>
          </div>
        </div>
      )}
    </>
  );
};
