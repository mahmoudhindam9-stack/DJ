import React, { useState } from 'react';
import {
  X,
  Monitor,
  Download,
  Terminal,
  CheckCircle2,
  Copy,
  Check,
  ExternalLink,
  Laptop,
  Sparkles,
  Layers,
} from 'lucide-react';
import { ThemeColors } from '../../types';
import { usePWAInstall } from '../../hooks/usePWAInstall';
import { downloadSetupBat, downloadUninstallBat } from '../../utils/setupDownloader';

import { Trash2, FolderTree } from 'lucide-react';

const PC_PACKAGE_URL = 'https://github.com/mahmoudhindam9-stack/DJ-PC-1/releases/latest/download/DJ-Desktop-PC-Latest.zip';

interface DesktopSetupModalProps {
  isOpen: boolean;
  onClose: () => void;
  themeColors: ThemeColors;
  isArabic: boolean;
}

export const DesktopSetupModal: React.FC<DesktopSetupModalProps> = ({
  isOpen,
  onClose,
  themeColors,
  isArabic,
}) => {
  const { isInstallable, isInstalled, install, isIOS } = usePWAInstall();
  const [activeTab, setActiveTab] = useState<'AUTO_SETUP' | 'INSTANT_PWA' | 'TAURI_EXE' | 'UNINSTALL'>('AUTO_SETUP');
  const [copiedCmd, setCopiedCmd] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleCopy = (text: string, id: string) => {
    navigator.clipboard.writeText(text);
    setCopiedCmd(id);
    setTimeout(() => setCopiedCmd(null), 2000);
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-fadeIn"
      dir={isArabic ? 'rtl' : 'ltr'}
    >
      <div
        className="w-full max-w-3xl rounded-2xl shadow-2xl overflow-hidden border flex flex-col max-h-[90vh]"
        style={{
          backgroundColor: themeColors.surface,
          borderColor: themeColors.border,
          color: themeColors.textPrimary,
        }}
      >
        {/* Header */}
        <div
          className="p-5 border-b flex items-center justify-between"
          style={{
            borderColor: themeColors.border,
            background: `linear-gradient(135deg, ${themeColors.surfaceVariant} 0%, ${themeColors.surface} 100%)`,
          }}
        >
          <div className="flex items-center gap-3">
            <div
              className="w-10 h-10 rounded-xl flex items-center justify-center shadow-lg"
              style={{ backgroundColor: themeColors.primary }}
            >
              <Monitor className="w-5 h-5 text-white" />
            </div>
            <div>
              <h2 className="text-lg font-bold">
                {isArabic ? 'تثبيت البرنامج وإنشاء أيقونة سطح المكتب' : 'Desktop Setup & Shortcut Installer'}
              </h2>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-2 rounded-lg hover:bg-white/10 transition-colors"
          >
            <X className="w-5 h-5 opacity-70 hover:opacity-100" />
          </button>
        </div>

        {/* Navigation Tabs */}
        <div
          className="flex border-b px-5 pt-3 gap-2 text-xs font-semibold overflow-x-auto"
          style={{ borderColor: themeColors.border }}
        >
          <button
            onClick={() => setActiveTab('AUTO_SETUP')}
            className={`pb-3 px-3 border-b-2 transition-all flex items-center gap-1.5 whitespace-nowrap ${
              activeTab === 'AUTO_SETUP'
                ? 'border-emerald-500 text-emerald-400 font-bold'
                : 'border-transparent opacity-60 hover:opacity-100'
            }`}
          >
            <Terminal className="w-4 h-4" />
            <span>{isArabic ? '1. ملف setup.bat التلقائي' : '1. Automatic setup.bat File'}</span>
          </button>

          <button
            onClick={() => setActiveTab('INSTANT_PWA')}
            className={`pb-3 px-3 border-b-2 transition-all flex items-center gap-1.5 whitespace-nowrap ${
              activeTab === 'INSTANT_PWA'
                ? 'border-blue-500 text-blue-400 font-bold'
                : 'border-transparent opacity-60 hover:opacity-100'
            }`}
          >
            <Sparkles className="w-4 h-4" />
            <span>{isArabic ? '2. تثبيت فوري بضغطة واحدة (PWA)' : '2. Instant 1-Click Install (PWA)'}</span>
          </button>

          <button
            onClick={() => setActiveTab('TAURI_EXE')}
            className={`pb-3 px-3 border-b-2 transition-all flex items-center gap-1.5 whitespace-nowrap ${
              activeTab === 'TAURI_EXE'
                ? 'border-purple-500 text-purple-400 font-bold'
                : 'border-transparent opacity-60 hover:opacity-100'
            }`}
          >
            <Layers className="w-4 h-4" />
            <span>{isArabic ? '3. بناء ملف تنفيذي (.exe / MSI)' : '3. Native .exe / MSI Build'}</span>
          </button>

          <button
            onClick={() => setActiveTab('UNINSTALL')}
            className={`pb-3 px-3 border-b-2 transition-all flex items-center gap-1.5 whitespace-nowrap ${
              activeTab === 'UNINSTALL'
                ? 'border-red-500 text-red-400 font-bold'
                : 'border-transparent opacity-60 hover:opacity-100'
            }`}
          >
            <Trash2 className="w-4 h-4" />
            <span>{isArabic ? '4. ملف إلغاء التثبيت (uninstall.bat)' : '4. Uninstaller (uninstall.bat)'}</span>
          </button>
        </div>

        {/* Content Body */}
        <div className="p-6 overflow-y-auto space-y-6 flex-1 text-sm">
          {/* TAB 1: AUTO SETUP BAT */}
          {activeTab === 'AUTO_SETUP' && (
            <div className="space-y-5">
              <div
                className="p-4 rounded-xl border border-emerald-500/30 bg-emerald-500/10 flex items-start gap-3"
              >
                <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
                <div className="text-xs leading-relaxed space-y-1">
                  <p className="font-semibold text-emerald-300">
                    {isArabic
                      ? 'تم تجهيز ملف setup.bat التلقائي في المجلد الرئيسي للبرنامج!'
                      : 'Download the complete Windows package to get setup.bat and all version files.'}
                  </p>
                  <p className="opacity-80">
                    {isArabic
                      ? 'بمجرد تنزيل مجلد البرنامج وتشغيل setup.bat، يقوم تلقائياً بفحص وتثبيت المتطلبات (Node.js & npm)، وتجهيز ملفات التشغيل، وإنشاء أيقونة "DJ Desktop Studio" الرسمية على سطح المكتب فوراً!'
                      : 'When downloading the app and running setup.bat, it automatically installs all prerequisites, builds the app, and creates a desktop shortcut with the official icon.'}
                  </p>
                </div>
              </div>

              {/* Download buttons: Full PC package + direct setup.bat */}
              <div className="flex flex-wrap items-center gap-3">
                <a href={PC_PACKAGE_URL} className="flex-1 min-w-[200px] py-3 px-5 rounded-xl font-bold text-sm bg-emerald-600 hover:bg-emerald-500 text-white shadow-lg shadow-emerald-900/30 flex items-center justify-center gap-2 transition-transform active:scale-95">
                  <Download className="w-4 h-4" />
                  <span>{isArabic ? 'تحميل حزمة ويندوز كاملة ومحدّثة' : 'Download complete, updated Windows package'}</span>
                </a>
                <button
                  type="button"
                  onClick={downloadSetupBat}
                  className="py-3 px-4 rounded-xl font-bold text-sm bg-emerald-500/20 hover:bg-emerald-500/30 text-emerald-300 border border-emerald-500/40 flex items-center justify-center gap-2 transition-transform active:scale-95"
                >
                  <Terminal className="w-4 h-4" />
                  <span>{isArabic ? 'تحميل setup.bat المحدث' : 'Download Updated setup.bat'}</span>
                </button>
                <a href="https://github.com/mahmoudhindam9-stack/DJ-PC-1/releases/latest" target="_blank" rel="noreferrer" className="py-3 px-4 rounded-xl font-bold text-sm bg-blue-600/20 hover:bg-blue-600/30 text-blue-300 border border-blue-500/30 flex items-center justify-center gap-2">
                  <ExternalLink className="w-4 h-4" />
                  <span>{isArabic ? 'تفاصيل الإصدار' : 'Release details'}</span>
                </a>
              </div>

              {/* Clean folder structure guide */}
              <div
                className="p-3.5 rounded-xl border flex items-start gap-3"
                style={{
                  backgroundColor: themeColors.surfaceVariant,
                  borderColor: themeColors.border,
                }}
              >
                <FolderTree className="w-5 h-5 text-amber-400 shrink-0 mt-0.5" />
                <div className="text-xs space-y-1">
                  <p className="font-semibold text-amber-300">
                    {isArabic ? 'تنظيم وهيكل المجلدات المنظم:' : 'Clean & Organized Folder Layout:'}
                  </p>
                  <p className="opacity-80 leading-relaxed font-mono text-[11px]">
                    {isArabic
                      ? '📁 الحزمة الكاملة تحتوي على setup.bat وuninstall.bat ومجلد app بكل ملفات التشغيل والبناء والتحديث.'
                      : '📁 The full ZIP contains setup.bat, uninstall.bat, and the complete app folder with production assets and updater scripts.'}
                  </p>
                </div>
              </div>

              {/* Step by step guide */}
              <div className="space-y-3">
                <h4 className="font-bold text-xs uppercase tracking-wider opacity-60">
                  {isArabic ? 'خطوات التثبيت على الكمبيوتر:' : 'How to Run on Your PC:'}
                </h4>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                  <div
                    className="p-4 rounded-xl border space-y-2"
                    style={{
                      backgroundColor: themeColors.surfaceVariant,
                      borderColor: themeColors.border,
                    }}
                  >
                    <div className="w-7 h-7 rounded-lg bg-emerald-500/20 text-emerald-400 flex items-center justify-center font-bold text-xs">
                      1
                    </div>
                    <h5 className="font-semibold text-xs">
                      {isArabic ? 'تنزيل وفك الضغط' : 'Download & Extract'}
                    </h5>
                    <p className="text-[11px] opacity-70 leading-relaxed">
                      {isArabic
                        ? 'نزّل حزمة ويندوز الكاملة، وفك ضغطها، ثم شغّل setup.bat الموجود بجانب مجلد app.'
                        : 'Download the complete Windows ZIP, extract it, then run setup.bat beside the app folder.'}
                    </p>
                  </div>

                  <div
                    className="p-4 rounded-xl border space-y-2"
                    style={{
                      backgroundColor: themeColors.surfaceVariant,
                      borderColor: themeColors.border,
                    }}
                  >
                    <div className="w-7 h-7 rounded-lg bg-emerald-500/20 text-emerald-400 flex items-center justify-center font-bold text-xs">
                      2
                    </div>
                    <h5 className="font-semibold text-xs">
                      {isArabic ? 'فتح ملف setup.bat' : 'Open setup.bat'}
                    </h5>
                    <p className="text-[11px] opacity-70 leading-relaxed">
                      {isArabic
                        ? 'ملف setup.bat داخل الحزمة الكاملة يشغّل سكريبت التثبيت وكل ملفات الإصدار معًا.'
                        : 'The bundled setup.bat runs the included setup scripts and installs this complete version.'}
                    </p>
                  </div>

                  <div
                    className="p-4 rounded-xl border space-y-2"
                    style={{
                      backgroundColor: themeColors.surfaceVariant,
                      borderColor: themeColors.border,
                    }}
                  >
                    <div className="w-7 h-7 rounded-lg bg-emerald-500/20 text-emerald-400 flex items-center justify-center font-bold text-xs">
                      3
                    </div>
                    <h5 className="font-semibold text-xs">
                      {isArabic ? 'أيقونة سطح المكتب جاهزة!' : 'Desktop Icon Ready!'}
                    </h5>
                    <p className="text-[11px] opacity-70 leading-relaxed">
                      {isArabic
                        ? 'ينشئ الملف أيقونة مستقلة على سطح المكتب مع مشغل خلفي صامت يفتح البرنامج فوراً بدون شاشة سوداء.'
                        : 'It creates a shortcut on your desktop with custom icon that launches silently.'}
                    </p>
                  </div>
                </div>
              </div>

              {/* What the script does */}
              <div
                className="p-4 rounded-xl border space-y-3"
                style={{
                  backgroundColor: themeColors.surfaceVariant,
                  borderColor: themeColors.border,
                }}
              >
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold flex items-center gap-2">
                    <Terminal className="w-4 h-4 text-emerald-400" />
                    {isArabic ? 'ما يقوم به ملف setup.bat داخل جهازك:' : 'Operations automated by setup.bat:'}
                  </span>
                  <button
                    onClick={() => handleCopy('.\\setup.bat', 'bat-cmd')}
                    className="px-2 py-1 rounded bg-white/10 hover:bg-white/20 text-[10px] flex items-center gap-1 transition-colors"
                  >
                    {copiedCmd === 'bat-cmd' ? <Check className="w-3 h-3 text-emerald-400" /> : <Copy className="w-3 h-3" />}
                    <span>{copiedCmd === 'bat-cmd' ? 'تم النسخ' : 'نسخ الأمر'}</span>
                  </button>
                </div>

                <ul className="text-xs space-y-1.5 opacity-80 list-disc list-inside">
                  <li>
                    {isArabic
                      ? 'فحص تثبيت Node.js & npm (مع تثبيت تلقائي فوري عبر winget إذا كان مفقوداً).'
                      : 'Checks Node.js & npm, and auto-installs via winget if missing.'}
                  </li>
                  <li>
                    {isArabic
                      ? 'تشغيل npm install لتثبيت جميع المكتبات ومحركات الصوت تلقائياً.'
                      : 'Runs npm install to download all audio engine packages.'}
                  </li>
                  <li>
                    {isArabic
                      ? 'بناء التطبيق بالكامل (npm run build) لسرعة إقلاع فائقة.'
                      : 'Compiles full production assets (npm run build) for instantaneous load.'}
                  </li>
                  <li>
                    {isArabic
                      ? 'إنشاء أيقونة سطح المكتب الرسمية في مسار Desktop الخاص بك مع ربطها بمشغل خفيف.'
                      : 'Generates DJ Desktop Studio.lnk on user desktop pointing to the silent launcher.'}
                  </li>
                </ul>
              </div>
            </div>
          )}

          {/* TAB 2: INSTANT PWA */}
          {activeTab === 'INSTANT_PWA' && (
            <div className="space-y-5">
              <div className="p-4 rounded-xl border border-blue-500/30 bg-blue-500/10 flex items-start gap-3">
                <Laptop className="w-5 h-5 text-blue-400 shrink-0 mt-0.5" />
                <div className="text-xs leading-relaxed space-y-1">
                  <p className="font-semibold text-blue-300">
                    {isArabic
                      ? 'تثبيت كبرنامج كمبيوتر مستقل بضغطة زر واحدة (بدون الحاجة لسطر الأوامر)!'
                      : 'Install directly as a standalone desktop app right now!'}
                  </p>
                  <p className="opacity-80">
                    {isArabic
                      ? 'بفضل تقنية Progressive Web App المتقدمة، يمكنك تثبيت البرنامج في ويندوز أو ماك بضغطة واحدة ليظهر له تطبيق مستقل على سطح المكتب وقائمة ابدأ ويعمل بدون إنترنت وبدون شريط متصفح.'
                      : 'Using Progressive Web App technology, you can install DJ Desktop into Windows with a desktop icon, Start Menu entry, and full offline caching.'}
                  </p>
                </div>
              </div>

              <div className="flex flex-col items-center justify-center p-6 border rounded-2xl gap-4 text-center"
                   style={{ backgroundColor: themeColors.surfaceVariant, borderColor: themeColors.border }}>
                <img
                  src="/pwa-192x192.png"
                  alt="DJ Desktop"
                  className="w-20 h-20 rounded-2xl shadow-xl border border-white/20"
                />
                <div>
                  <h3 className="font-bold text-base">DJ Desktop Studio</h3>
                  <p className="text-xs opacity-70 mt-1">
                    {isArabic
                      ? 'مشغل وميكسر الصوتيات الاحترافي للكمبيوتر'
                      : 'Professional Desktop Audio & DJ Workstation'}
                  </p>
                </div>

                {isInstalled ? (
                  <div className="flex items-center gap-2 text-emerald-400 font-bold text-sm bg-emerald-500/10 border border-emerald-500/30 px-4 py-2 rounded-xl">
                    <CheckCircle2 className="w-4 h-4" />
                    <span>{isArabic ? 'البرنامج مثبت بالفعل على جهازك!' : 'App is already installed on your PC!'}</span>
                  </div>
                ) : isInstallable ? (
                  <button
                    onClick={async () => {
                      await install();
                    }}
                    className="py-3 px-8 rounded-xl font-bold text-sm bg-blue-600 hover:bg-blue-500 text-white shadow-lg shadow-blue-900/30 flex items-center justify-center gap-2 transition-transform active:scale-95"
                  >
                    <Download className="w-4 h-4" />
                    <span>{isArabic ? 'تثبيت على سطح المكتب الآن' : 'Install to Desktop Now'}</span>
                  </button>
                ) : (
                  <div className="space-y-2 text-xs opacity-80 max-w-md">
                    <p>
                      {isArabic
                        ? 'إذا لم يظهر زر التثبيت التلقائي أعلاه في متصفحك (مثل Chrome أو Edge):'
                        : 'If direct installation prompt did not appear in your browser (Chrome/Edge):'}
                    </p>
                    <p className="p-3 bg-black/40 rounded-xl font-mono text-[11px] text-left">
                      1. انقر على أيقونة التثبيت (🖥️ أو ⊕) بجانب شريط العنوان في المتصفح.<br />
                      2. أو افتح القائمة (⋮) واضغط "تثبيت DJ Desktop" أو "Install DJ Desktop".
                    </p>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* TAB 3: TAURI EXE */}
          {activeTab === 'TAURI_EXE' && (
            <div className="space-y-4">
              <div className="p-4 rounded-xl border border-purple-500/30 bg-purple-500/10 flex items-start gap-3">
                <Layers className="w-5 h-5 text-purple-400 shrink-0 mt-0.5" />
                <div className="text-xs leading-relaxed space-y-1">
                  <p className="font-semibold text-purple-300">
                    {isArabic
                      ? 'بناء ملف تنفيذي أصلي بنظام Windows (.exe أو .msi)'
                      : 'Build native Windows executable installer (.exe / .msi)'}
                  </p>
                  <p className="opacity-80">
                    {isArabic
                      ? 'مشروع DJ Desktop مجهز مسبقاً بكامل إعدادات Tauri (محرك Rust خفيف الوزن). يمكنك توليد مثبت Windows كامل ومستقل بملف Setup تنفيذي.'
                      : 'DJ Desktop comes pre-configured with Tauri 2.0. You can build a 10MB ultra-fast native Windows installer.'}
                  </p>
                </div>
              </div>

              <div
                className="p-4 rounded-xl border space-y-3"
                style={{
                  backgroundColor: themeColors.surfaceVariant,
                  borderColor: themeColors.border,
                }}
              >
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold">{isArabic ? 'أمر التوليد والتثبيت:' : 'Build command:'}</span>
                  <button
                    onClick={() => handleCopy('npm run tauri build', 'tauri-cmd')}
                    className="px-2 py-1 rounded bg-white/10 hover:bg-white/20 text-[10px] flex items-center gap-1 transition-colors"
                  >
                    {copiedCmd === 'tauri-cmd' ? <Check className="w-3 h-3 text-emerald-400" /> : <Copy className="w-3 h-3" />}
                    <span>{copiedCmd === 'tauri-cmd' ? 'تم النسخ' : 'نسخ الأمر'}</span>
                  </button>
                </div>

                <div className="p-3 bg-black/60 rounded-xl font-mono text-xs text-purple-300">
                  npm run tauri build
                </div>

                <p className="text-[11px] opacity-70">
                  {isArabic
                    ? 'سيتم توليد ملف التثبيت النهائي داخل مجلد src-tauri/target/release/bundle/msi/DJ Desktop.msi'
                    : 'The final installer is produced under src-tauri/target/release/bundle/msi/DJ Desktop.msi'}
                </p>
              </div>
            </div>
          )}

          {/* TAB 4: UNINSTALL BAT */}
          {activeTab === 'UNINSTALL' && (
            <div className="space-y-5">
              <div className="p-4 rounded-xl border border-red-500/30 bg-red-500/10 flex items-start gap-3">
                <Trash2 className="w-5 h-5 text-red-400 shrink-0 mt-0.5" />
                <div className="text-xs leading-relaxed space-y-1">
                  <p className="font-semibold text-red-300">
                    {isArabic
                      ? 'ملف إلغاء التثبيت وحذف جميع الملفات المثبتة (uninstall.bat)'
                      : 'Uninstaller file: Cleanly remove all installed application files (uninstall.bat)'}
                  </p>
                  <p className="opacity-80">
                    {isArabic
                      ? 'تم إنشاء ملف uninstall.bat المتواجد في المجلد الرئيسي لحذف كافة الملفات والمخلفات التي تم تثبيتها بضغطة زر واحدة بأمان تام وبدون أي بقايا على حاسوبك.'
                      : 'uninstall.bat is located in the root directory to safely remove desktop shortcuts, node_modules, build packages, and background launchers.'}
                  </p>
                </div>
              </div>

              {/* Action Buttons */}
              <div className="flex flex-wrap items-center gap-3">
                <a
                  href={PC_PACKAGE_URL}
                  className="flex-1 py-3 px-5 rounded-xl font-bold text-sm bg-red-600 hover:bg-red-500 text-white shadow-lg shadow-red-900/30 flex items-center justify-center gap-2 transition-transform active:scale-95"
                >
                  <Trash2 className="w-4 h-4" />
                  <span>{isArabic ? 'تحميل الحزمة الكاملة التي تشمل uninstall.bat' : 'Download full package with uninstall.bat'}</span>
                </a>
                <button
                  type="button"
                  onClick={downloadUninstallBat}
                  className="py-3 px-4 rounded-xl font-bold text-sm bg-red-500/20 hover:bg-red-500/30 text-red-300 border border-red-500/40 flex items-center justify-center gap-2 transition-transform active:scale-95"
                >
                  <Trash2 className="w-4 h-4" />
                  <span>{isArabic ? 'تحميل uninstall.bat المحدث' : 'Download Updated uninstall.bat'}</span>
                </button>
              </div>

              {/* What gets removed list */}
              <div
                className="p-4 rounded-xl border space-y-3"
                style={{
                  backgroundColor: themeColors.surfaceVariant,
                  borderColor: themeColors.border,
                }}
              >
                <h4 className="font-bold text-xs">
                  {isArabic ? 'ما الذي سيتم حذفه عند تشغيل uninstall.bat؟' : 'What gets removed by uninstall.bat:'}
                </h4>
                <ul className="text-xs space-y-2 opacity-90">
                  <li className="flex items-center gap-2">
                    <span className="w-2 h-2 rounded-full bg-red-400 shrink-0" />
                    <span>
                      {isArabic
                        ? 'حذف أيقونة سطح المكتب الرسمية (DJ Desktop Studio.lnk)'
                        : 'Official desktop shortcut icon (DJ Desktop Studio.lnk)'}
                    </span>
                  </li>
                  <li className="flex items-center gap-2">
                    <span className="w-2 h-2 rounded-full bg-red-400 shrink-0" />
                    <span>
                      {isArabic
                        ? 'إيقاف أي سيرفرات أو عمليات تشغيل خلفية للبرنامج فوراً'
                        : 'Kill any background processes / node servers running for the app'}
                    </span>
                  </li>
                  <li className="flex items-center gap-2">
                    <span className="w-2 h-2 rounded-full bg-red-400 shrink-0" />
                    <span>
                      {isArabic
                        ? 'حذف حزم التثبيت والمكتبات (مجلد node_modules)'
                        : 'Installed dependency packages (node_modules folder)'}
                    </span>
                  </li>
                  <li className="flex items-center gap-2">
                    <span className="w-2 h-2 rounded-full bg-red-400 shrink-0" />
                    <span>
                      {isArabic
                        ? 'حذف ملفات الإنتاج المجمعة (مجلد dist و dev-dist)'
                        : 'Compiled production files (dist and dev-dist)'}
                    </span>
                  </li>
                  <li className="flex items-center gap-2">
                    <span className="w-2 h-2 rounded-full bg-red-400 shrink-0" />
                    <span>
                      {isArabic
                        ? 'حذف مشغلات الإقلاع التلقائي (run-dj-desktop.bat و run-dj-desktop.vbs)'
                        : 'Launcher scripts (run-dj-desktop.bat and run-dj-desktop.vbs)'}
                    </span>
                  </li>
                  <li className="flex items-center gap-2">
                    <span className="w-2 h-2 rounded-full bg-red-400 shrink-0" />
                    <span>
                      {isArabic
                        ? 'خيار اختياري لحذف مجلد البرنامج بالكامل نهائياً إذا أردت'
                        : 'Optional prompt to remove the entire project folder completely'}
                    </span>
                  </li>
                </ul>
              </div>

              {/* Instructions */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                <div
                  className="p-3.5 rounded-xl border space-y-1.5"
                  style={{
                    backgroundColor: themeColors.surfaceVariant,
                    borderColor: themeColors.border,
                  }}
                >
                  <span className="font-bold text-xs text-red-400">1</span>
                  <h5 className="font-semibold text-xs">{isArabic ? 'فتح المجلد' : 'Open Folder'}</h5>
                  <p className="text-[11px] opacity-70">
                    {isArabic
                      ? 'توجه إلى المجلد الرئيسي للبرنامج.'
                      : 'Navigate to the main application folder.'}
                  </p>
                </div>
                <div
                  className="p-3.5 rounded-xl border space-y-1.5"
                  style={{
                    backgroundColor: themeColors.surfaceVariant,
                    borderColor: themeColors.border,
                  }}
                >
                  <span className="font-bold text-xs text-red-400">2</span>
                  <h5 className="font-semibold text-xs">{isArabic ? 'تشغيل uninstall.bat' : 'Run uninstall.bat'}</h5>
                  <p className="text-[11px] opacity-70">
                    {isArabic
                      ? 'انقر نقراً مزدوجاً على ملف uninstall.bat.'
                      : 'Double click uninstall.bat in the root folder.'}
                  </p>
                </div>
                <div
                  className="p-3.5 rounded-xl border space-y-1.5"
                  style={{
                    backgroundColor: themeColors.surfaceVariant,
                    borderColor: themeColors.border,
                  }}
                >
                  <span className="font-bold text-xs text-red-400">3</span>
                  <h5 className="font-semibold text-xs">{isArabic ? 'تأكيد الحذف' : 'Confirm'}</h5>
                  <p className="text-[11px] opacity-70">
                    {isArabic
                      ? 'اضغط حرف Y للتأكيد وسيتم تنظيف وحذف كل شيء فوراً.'
                      : 'Type Y to confirm and all files and icons will be purged.'}
                  </p>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        <div
          className="p-4 border-t flex items-center justify-between text-xs"
          style={{ borderColor: themeColors.border, backgroundColor: themeColors.surfaceVariant }}
        >
          <div className="flex items-center gap-2 opacity-70">
            <CheckCircle2 className="w-4 h-4 text-emerald-400" />
            <span>
              {isArabic
                ? 'ملفات setup.bat و run.bat وأيقونات سطح المكتب جاهزة في المجلد'
                : 'setup.bat, run.bat and desktop icons ready in root directory'}
            </span>
          </div>

          <button
            onClick={onClose}
            className="py-1.5 px-4 rounded-lg font-semibold bg-white/10 hover:bg-white/20 transition-colors"
          >
            {isArabic ? 'إغلاق' : 'Close'}
          </button>
        </div>
      </div>
    </div>
  );
};
