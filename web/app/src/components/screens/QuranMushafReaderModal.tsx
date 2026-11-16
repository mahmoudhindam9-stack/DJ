import React, { useEffect, useMemo, useState } from 'react';
import { ArrowLeft, BookOpen, ChevronLeft, ChevronRight, Volume2 } from 'lucide-react';
import { mainAudioEngine } from '../../audio/AudioEngine';

export interface MushafVerse {
  numberInSurah: number;
  surahNumber: number;
  surahNameAr: string;
  page: number;
  text: string;
}

interface Props {
  isOpen: boolean;
  initialSurahId: number;
  initialSurahName: string;
  reciterName?: string;
  isArabic: boolean;
  onClose: () => void;
}

const ARABIC_DIGITS = ['٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩'];

export function toEasternArabicDigits(num: number): string {
  return String(Math.max(1, Math.floor(num)))
    .split('')
    .map((ch) => {
      const code = ch.charCodeAt(0) - 48;
      return code >= 0 && code <= 9 ? ARABIC_DIGITS[code] : ch;
    })
    .join('');
}

/**
 * Strips any existing trailing verse numbers or ornate markers from a raw ayah string
 * so that exactly ONE verse marker is rendered per ayah (eliminating duplicate markers).
 */
export function sanitizeQuranVerseText(raw: string): string {
  return String(raw || '')
    .replace(/[\u06DD\uFD3E\uFD3F\u0660-\u0669\u06F0-\u06F90-9()[\]]+\s*$/g, '')
    .trim();
}

export const QuranMushafReaderModal: React.FC<Props> = ({
  isOpen,
  initialSurahId,
  initialSurahName,
  reciterName,
  isArabic,
  onClose
}) => {
  const [verses, setVerses] = useState<MushafVerse[]>([]);
  const [currentPage, setCurrentPage] = useState<number>(1);
  const [pagesInSurah, setPagesInSurah] = useState<number[]>([1]);
  const [activeAyahNumber, setActiveAyahNumber] = useState<number>(1);
  const [loading, setLoading] = useState<boolean>(false);

  // Keep screen awake like watching a video while the Mushaf Reader is open
  useEffect(() => {
    if (!isOpen) return;
    let wakeLockSentinel: any = null;
    const requestWakeLock = async () => {
      try {
        if ('wakeLock' in navigator && (navigator as any).wakeLock?.request) {
          wakeLockSentinel = await (navigator as any).wakeLock.request('screen');
        }
      } catch {
        // WakeLock not supported or permission denied in browser
      }
    };
    requestWakeLock();
    return () => {
      if (wakeLockSentinel && typeof wakeLockSentinel.release === 'function') {
        wakeLockSentinel.release().catch(() => {});
      }
    };
  }, [isOpen]);

  // Fetch Surah verses & Mushaf page numbers
  useEffect(() => {
    if (!isOpen) return;
    let cancelled = false;
    const loadSurahMushaf = async () => {
      setLoading(true);
      try {
        const resp = await fetch(
          `https://api.alquran.cloud/v1/surah/${initialSurahId}/quran-uthmani`
        );
        const json = await resp.json();
        const ayahs = json?.data?.ayahs;
        const surahNameAr = json?.data?.name || initialSurahName || 'سورة';
        if (!cancelled && Array.isArray(ayahs) && ayahs.length > 0) {
          const mapped: MushafVerse[] = ayahs.map((a: any) => ({
            numberInSurah: Number(a.numberInSurah) || 1,
            surahNumber: initialSurahId,
            surahNameAr,
            page: Number(a.page) || 1,
            text: sanitizeQuranVerseText(a.text)
          }));
          setVerses(mapped);
          const uniquePages = Array.from(new Set(mapped.map((v) => v.page))).sort(
            (a, b) => a - b
          );
          setPagesInSurah(uniquePages.length > 0 ? uniquePages : [1]);
          setCurrentPage(uniquePages[0] || 1);
          setActiveAyahNumber(1);
        }
      } catch {
        if (!cancelled) {
          setVerses([
            {
              numberInSurah: 1,
              surahNumber: initialSurahId,
              surahNameAr: initialSurahName,
              page: 1,
              text: 'بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ'
            }
          ]);
          setPagesInSurah([1]);
          setCurrentPage(1);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    loadSurahMushaf();
    return () => {
      cancelled = true;
    };
  }, [isOpen, initialSurahId, initialSurahName]);

  // Synchronize active verse highlight with audio engine playback progress
  useEffect(() => {
    if (!isOpen || verses.length === 0) return;

    // Precompute cumulative character weights for accurate ayah timing synchronization
    const weights = verses.map((v) => Math.max(12, v.text.length));
    const totalWeight = weights.reduce((acc, w) => acc + w, 0);

    const timer = window.setInterval(() => {
      const dur = mainAudioEngine.durationMs;
      const pos = mainAudioEngine.currentTimeMs;
      if (dur > 1000 && pos >= 0) {
        const ratio = Math.min(0.999, Math.max(0, pos / dur));
        const targetWeight = ratio * totalWeight;
        let cumulative = 0;
        let matchedVerse = verses[0];
        for (let i = 0; i < verses.length; i++) {
          cumulative += weights[i];
          if (targetWeight <= cumulative) {
            matchedVerse = verses[i];
            break;
          }
        }
        if (matchedVerse) {
          setActiveAyahNumber(matchedVerse.numberInSurah);
          if (matchedVerse.page !== currentPage && mainAudioEngine.isPlaying) {
            setCurrentPage(matchedVerse.page);
          }
        }
      }
    }, 220);

    return () => window.clearInterval(timer);
  }, [isOpen, verses, currentPage]);

  const pageVerses = useMemo(() => {
    const filtered = verses.filter((v) => v.page === currentPage);
    return filtered.length > 0 ? filtered : verses.slice(0, 12);
  }, [verses, currentPage]);

  const handleTapVerse = (verse: MushafVerse) => {
    setActiveAyahNumber(verse.numberInSurah);
    const dur = mainAudioEngine.durationMs;
    if (dur > 1000 && verses.length > 0) {
      const weights = verses.map((v) => Math.max(12, v.text.length));
      const totalWeight = weights.reduce((acc, w) => acc + w, 0);
      let beforeWeight = 0;
      for (const v of verses) {
        if (v.numberInSurah === verse.numberInSurah) break;
        beforeWeight += Math.max(12, v.text.length);
      }
      const targetMs = (beforeWeight / Math.max(1, totalWeight)) * dur;
      mainAudioEngine.seekTo(targetMs);
    }
  };

  if (!isOpen) return null;

  const pageIdx = pagesInSurah.indexOf(currentPage);
  const canPrevPage = pageIdx > 0;
  const canNextPage = pageIdx >= 0 && pageIdx < pagesInSurah.length - 1;

  return (
    <div
      data-testid="quran-mushaf-reader-modal"
      dir="rtl"
      className="fixed inset-0 z-50 bg-[#FBF6E9] text-[#1A1612] flex flex-col justify-between overflow-hidden select-none"
    >
      {/* Top Mushaf Header Bar */}
      <div className="flex items-center justify-between px-4 py-2 bg-[#EFE6CE] border-b border-[#C8B273] shrink-0">
        <div className="flex items-center gap-2">
          <button
            onClick={onClose}
            data-testid="quran-mushaf-close-btn"
            className="px-3 py-1 rounded-lg bg-[#3A2E18] text-[#FBF6E9] text-xs font-bold flex items-center gap-1.5"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            <span>{isArabic ? 'إغلاق المصحف' : 'Close Mushaf'}</span>
          </button>
          <span className="text-xs font-bold text-[#4A3B1C] flex items-center gap-1">
            <BookOpen className="w-3.5 h-3.5 text-[#8C6D2E]" />
            {initialSurahName}
            {reciterName ? ` • ${reciterName}` : ''}
          </span>
        </div>

        <div className="flex items-center gap-3 text-xs font-bold text-[#4A3B1C]">
          <span className="flex items-center gap-1 text-[#B71C1C]">
            <Volume2 className="w-3.5 h-3.5" />
            {isArabic
              ? `الآية المتلوة الآن: ﴿${toEasternArabicDigits(activeAyahNumber)}﴾`
              : `Active Ayah: (${activeAyahNumber})`}
          </span>
          <span className="px-2.5 py-0.5 rounded-full bg-[#DFD2AE] border border-[#B59B57]">
            {isArabic
              ? `صفحة ${toEasternArabicDigits(currentPage)}`
              : `Page ${currentPage}`}
          </span>
        </div>
      </div>

      {/* Main Full-Page Paper Mushaf Frame (Fits 100% height with NO vertical scrolling) */}
      <div className="flex-1 min-h-0 p-3 sm:p-5 flex items-center justify-center overflow-hidden">
        <div
          data-testid="quran-mushaf-page-frame"
          className="w-full max-w-3xl h-full border-4 border-double border-[#8C6D2E] rounded-xl bg-[#FCF8EC] shadow-inner px-5 py-3 flex flex-col justify-between overflow-hidden"
        >
          {/* Ornamental Surah Banner (shown on first page of Surah) */}
          {pageIdx <= 0 && (
            <div className="shrink-0 mb-1">
              <div className="w-full py-1.5 px-4 rounded-lg border-2 border-[#8C6D2E] bg-gradient-to-r from-[#E6D6AC] via-[#F7EFE0] to-[#E6D6AC] flex items-center justify-between text-[#2D2210]">
                <span className="text-xs font-bold">
                  {isArabic
                    ? `ترتيبها ${toEasternArabicDigits(initialSurahId)}`
                    : `Surah #${initialSurahId}`}
                </span>
                <span className="text-lg sm:text-xl font-extrabold tracking-wide">
                  {initialSurahName}
                </span>
                <span className="text-xs font-bold">
                  {isArabic
                    ? `آياتها ${toEasternArabicDigits(verses.length)}`
                    : `${verses.length} Ayahs`}
                </span>
              </div>
              {initialSurahId !== 1 && initialSurahId !== 9 && (
                <div className="text-center text-base sm:text-lg font-bold text-[#1A1612] mt-1">
                  بِسْمِ <span className="text-[#C62828]">ٱللَّهِ</span> ٱلرَّحْمَٰنِ ٱلرَّحِيمِ
                </div>
              )}
            </div>
          )}

          {/* Justified Quranic Mushaf Body (Single verse marker per verse + Red underline on active verse) */}
          <div
            data-testid="quran-mushaf-text-container"
            className="flex-1 min-h-0 flex items-center justify-center overflow-hidden"
          >
            {loading ? (
              <div className="text-sm font-bold text-[#6E5623]">
                {isArabic ? 'جاري تحميل صفحة المصحف الشريف...' : 'Loading Mushaf page...'}
              </div>
            ) : (
              <div
                style={{
                  textAlign: 'justify',
                  textAlignLast: 'center',
                  lineHeight: pageVerses.length > 12 ? '1.85' : '2.15',
                  fontSize:
                    pageVerses.length > 15
                      ? 'clamp(14px, 1.85vh, 20px)'
                      : 'clamp(16px, 2.35vh, 25px)',
                  fontFamily:
                    '"Amiri Quran", "Scheherazade New", "Traditional Arabic", serif'
                }}
                className="w-full max-h-full overflow-hidden text-[#181410] font-medium"
              >
                {pageVerses.map((verse) => {
                  const isCurrentAyah = verse.numberInSurah === activeAyahNumber;
                  const cleanText = sanitizeQuranVerseText(verse.text);
                  const singleMarker = `\u06DD${toEasternArabicDigits(verse.numberInSurah)}`;
                  return (
                    <span
                      key={verse.numberInSurah}
                      data-testid={`mushaf-ayah-${verse.numberInSurah}`}
                      data-active={isCurrentAyah ? 'true' : 'false'}
                      onClick={() => handleTapVerse(verse)}
                      style={{
                        // Red underline on currently recited verse — never a red box
                        borderBottom: isCurrentAyah ? '2.5px solid #D32F2F' : 'none',
                        paddingBottom: isCurrentAyah ? '2px' : '0px',
                        backgroundColor: 'transparent',
                        cursor: 'pointer'
                      }}
                      className="inline transition-colors mx-0.5"
                    >
                      <span>{cleanText} </span>
                      <span
                        data-testid={`mushaf-marker-${verse.numberInSurah}`}
                        className="inline-block text-[#7A5B20] font-bold mx-1 select-none"
                      >
                        {singleMarker}
                      </span>{' '}
                    </span>
                  );
                })}
              </div>
            )}
          </div>

          {/* Bottom Mushaf Page Navigation Footer */}
          <div className="flex items-center justify-between pt-1.5 border-t border-[#D8C690] text-xs font-bold text-[#4A3B1C] shrink-0">
            <button
              onClick={() => canPrevPage && setCurrentPage(pagesInSurah[pageIdx - 1])}
              disabled={!canPrevPage}
              className="px-3 py-1 rounded-lg bg-[#E8D9B5] disabled:opacity-40 flex items-center gap-1"
            >
              <ChevronRight className="w-3.5 h-3.5" />
              <span>{isArabic ? 'الصفحة السابقة' : 'Previous Page'}</span>
            </button>

            <span>
              {isArabic
                ? `صفحة ${toEasternArabicDigits(currentPage)} (${toEasternArabicDigits(
                    pageIdx + 1
                  )} / ${toEasternArabicDigits(pagesInSurah.length)})`
                : `Page ${currentPage} (${pageIdx + 1} / ${pagesInSurah.length})`}
            </span>

            <button
              onClick={() => canNextPage && setCurrentPage(pagesInSurah[pageIdx + 1])}
              disabled={!canNextPage}
              className="px-3 py-1 rounded-lg bg-[#E8D9B5] disabled:opacity-40 flex items-center gap-1"
            >
              <span>{isArabic ? 'الصفحة التالية' : 'Next Page'}</span>
              <ChevronLeft className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
