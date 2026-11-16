import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowLeft,
  BookOpen,
  ChevronLeft,
  ChevronRight,
  Disc,
  Download,
  Heart,
  Layers,
  ListMusic,
  Loader2,
  Play,
  Plus,
  RefreshCw,
  Search,
  Sparkles,
  User,
  Volume2,
} from 'lucide-react';
import {
  AudioItem,
  Playlist,
  QuranMoshaf,
  QuranReciter,
  QuranSurah,
  ThemeColors,
} from '../../types';
import { quranService } from '../../services/QuranService';
import {
  downloadOnlineItem,
  downloadOnlineItemAsync,
} from '../../services/OnlineMusicService';

interface QuranScreenProps {
  themeColors: ThemeColors;
  isArabic: boolean;
  currentPlayingId?: string;
  isPlaying: boolean;
  playlists: Playlist[];
  favoriteIds: Set<string>;
  onPlayTrack: (item: AudioItem, queue?: AudioItem[]) => void;
  onEnqueueTrack: (item: AudioItem) => void;
  onSaveToLibrary: (item: AudioItem) => void;
  onToggleFavorite: (item: AudioItem) => void;
  onAddToPlaylist: (playlistId: string, item: AudioItem) => void;
  onCreatePlaylistAndAdd: (name: string, item: AudioItem) => void;
  onSendToDeckA: (item: AudioItem) => void;
  onSendToDeckB: (item: AudioItem) => void;
}

export const QuranScreen: React.FC<QuranScreenProps> = ({
  themeColors,
  isArabic,
  currentPlayingId,
  isPlaying,
  playlists,
  favoriteIds,
  onPlayTrack,
  onEnqueueTrack,
  onSaveToLibrary,
  onToggleFavorite,
  onAddToPlaylist,
  onCreatePlaylistAndAdd,
  onSendToDeckA,
  onSendToDeckB,
}) => {
  const [reciters, setReciters] = useState<QuranReciter[]>([]);
  const [suwar, setSuwar] = useState<QuranSurah[]>(quranService.getBuiltinSuwar());
  const [selectedReciter, setSelectedReciter] = useState<QuranReciter | null>(null);
  const [selectedMoshaf, setSelectedMoshaf] = useState<QuranMoshaf | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [downloadingId, setDownloadingId] = useState<string | null>(null);
  const [playlistModalItem, setPlaylistModalItem] = useState<AudioItem | null>(null);
  const [newPlaylistName, setNewPlaylistName] = useState('');

  // Dynamic viewport pagination so zero scrolling is required
  const gridContainerRef = useRef<HTMLDivElement | null>(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(12);
  const [gridCols, setGridCols] = useState(3);

  const loadData = async (force = false) => {
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const lang = isArabic ? 'ar' : 'eng';
      const [reciterList, suwarList] = await Promise.all([
        quranService.getReciters(lang, force),
        quranService.getSuwar(lang),
      ]);
      setReciters(reciterList);
      if (suwarList.length > 0) {
        setSuwar(suwarList);
      }
    } catch (err) {
      setErrorMessage(
        err instanceof Error
          ? err.message
          : isArabic
          ? 'تعذر تحميل قائمة القراء من MP3Quran.net'
          : 'Failed to load reciters from MP3Quran.net'
      );
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadData(false);
  }, [isArabic]);

  useEffect(() => {
    const updateGridMetrics = () => {
      const el = gridContainerRef.current;
      const width = el?.clientWidth || window.innerWidth - 64;
      const height = el?.clientHeight || window.innerHeight - 240;

      const cols = width >= 1360 ? 4 : width >= 960 ? 3 : width >= 620 ? 2 : 1;
      const rowHeight = selectedMoshaf ? 72 : 82;
      const rows = Math.max(2, Math.floor((height + 10) / rowHeight));
      setGridCols(cols);
      setItemsPerPage(Math.max(cols * 2, cols * rows));
    };

    updateGridMetrics();
    const observer = new ResizeObserver(updateGridMetrics);
    if (gridContainerRef.current) observer.observe(gridContainerRef.current);
    window.addEventListener('resize', updateGridMetrics);
    return () => {
      observer.disconnect();
      window.removeEventListener('resize', updateGridMetrics);
    };
  }, [selectedReciter, selectedMoshaf]);

  useEffect(() => {
    setCurrentPage(1);
  }, [searchQuery, selectedReciter, selectedMoshaf, itemsPerPage]);

  const suwarById = useMemo(() => {
    const map = new Map<number, QuranSurah>();
    for (const s of quranService.getBuiltinSuwar()) {
      map.set(s.id, s);
    }
    for (const s of suwar) {
      map.set(s.id, s);
    }
    return map;
  }, [suwar]);

  const availableSurahs = useMemo<QuranSurah[]>(() => {
    if (!selectedMoshaf) return [];
    const ids = selectedMoshaf.surahList;
    const list: QuranSurah[] = [];
    for (const id of ids) {
      const found = suwarById.get(id);
      if (found) {
        list.push(found);
      } else {
        list.push({ id, name: `${id}`, makkia: true, startPage: 1, endPage: 1 });
      }
    }
    return list;
  }, [selectedMoshaf, suwarById]);

  const currentMoshafQueue = useMemo(() => {
    if (!selectedReciter || !selectedMoshaf) return [];
    return availableSurahs.map((s) =>
      quranService.convertSurahToAudioItem(selectedReciter, selectedMoshaf, s, isArabic)
    );
  }, [selectedReciter, selectedMoshaf, availableSurahs, isArabic]);

  const filteredReciters = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return reciters;
    return reciters.filter(
      (r) =>
        r.name.toLowerCase().includes(q) ||
        r.moshaf.some((m) => m.name.toLowerCase().includes(q))
    );
  }, [reciters, searchQuery]);

  const filteredMoshafs = useMemo(() => {
    if (!selectedReciter) return [];
    const q = searchQuery.trim().toLowerCase();
    if (!q) return selectedReciter.moshaf;
    return selectedReciter.moshaf.filter((m) => m.name.toLowerCase().includes(q));
  }, [selectedReciter, searchQuery]);

  const filteredSurahs = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return availableSurahs;
    return availableSurahs.filter(
      (s) => s.name.toLowerCase().includes(q) || String(s.id).includes(q)
    );
  }, [availableSurahs, searchQuery]);

  const handleSelectReciter = (reciter: QuranReciter) => {
    setSelectedReciter(reciter);
    setSearchQuery('');
    if (reciter.moshaf.length === 1) {
      setSelectedMoshaf(reciter.moshaf[0]);
    } else {
      setSelectedMoshaf(null);
    }
  };

  const handleSelectMoshaf = (moshaf: QuranMoshaf) => {
    setSelectedMoshaf(moshaf);
    setSearchQuery('');
  };

  const handleBack = () => {
    setSearchQuery('');
    if (selectedMoshaf && selectedReciter && selectedReciter.moshaf.length > 1) {
      setSelectedMoshaf(null);
      return;
    }
    setSelectedMoshaf(null);
    setSelectedReciter(null);
  };

  const handlePlaySurah = (surah: QuranSurah) => {
    if (!selectedReciter || !selectedMoshaf) return;
    const item = quranService.convertSurahToAudioItem(
      selectedReciter,
      selectedMoshaf,
      surah,
      isArabic
    );
    onPlayTrack(item, currentMoshafQueue);
    setStatusMessage(
      isArabic
        ? `جاري تشغيل ${item.title} - ${selectedReciter.name}`
        : `Playing ${item.title} - ${selectedReciter.name}`
    );
  };

  const handlePlayAllSurahs = () => {
    if (currentMoshafQueue.length === 0) return;
    onPlayTrack(currentMoshafQueue[0], currentMoshafQueue);
    setStatusMessage(
      isArabic
        ? `تشغيل جميع سور ${selectedReciter?.name} (${currentMoshafQueue.length} سورة)`
        : `Playing all Surahs by ${selectedReciter?.name}`
    );
  };

  const handleDownloadSurah = async (surah: QuranSurah) => {
    if (!selectedReciter || !selectedMoshaf) return;
    const item = quranService.convertSurahToAudioItem(
      selectedReciter,
      selectedMoshaf,
      surah,
      isArabic
    );
    setDownloadingId(item.id);
    setErrorMessage(null);
    try {
      await downloadOnlineItemAsync(item, (msg) =>
        setStatusMessage(isArabic ? `جاري تنزيل ${item.title}...` : msg)
      );
      onSaveToLibrary(item);
      setStatusMessage(
        isArabic ? `تم تنزيل وحفظ "${item.title}" بنجاح!` : `Downloaded "${item.title}"!`
      );
    } catch {
      if (downloadOnlineItem(item)) {
        onSaveToLibrary(item);
        setStatusMessage(
          isArabic ? `بدأ تنزيل "${item.title}"...` : `Downloading "${item.title}"...`
        );
      } else {
        setErrorMessage(
          isArabic ? 'تعذر تنزيل السورة.' : 'Could not download Surah.'
        );
      }
    } finally {
      setDownloadingId(null);
    }
  };

  const activeItemsCount = selectedMoshaf
    ? filteredSurahs.length
    : selectedReciter
    ? filteredMoshafs.length
    : filteredReciters.length;

  const totalPages = Math.max(1, Math.ceil(activeItemsCount / itemsPerPage));
  const safePage = Math.min(currentPage, totalPages);
  const startIndex = (safePage - 1) * itemsPerPage;

  const pagedReciters = filteredReciters.slice(startIndex, startIndex + itemsPerPage);
  const pagedMoshafs = filteredMoshafs.slice(startIndex, startIndex + itemsPerPage);
  const pagedSurahs = filteredSurahs.slice(startIndex, startIndex + itemsPerPage);

  return (
    <div
      className="h-full min-h-0 flex flex-col overflow-hidden p-3 select-none"
      style={{ color: themeColors.textPrimary }}
    >
      {/* Compact Header Bar */}
      <div
        className="rounded-xl p-2.5 border mb-2.5 shrink-0 flex flex-wrap items-center justify-between gap-2"
        style={{
          backgroundColor: themeColors.surface,
          borderColor: themeColors.border,
        }}
      >
        <div className="flex items-center gap-2.5 min-w-0">
          {(selectedReciter || selectedMoshaf) && (
            <button
              onClick={handleBack}
              className="px-2.5 py-1.5 rounded-lg border flex items-center gap-1.5 text-xs font-bold transition-all hover:scale-105 shrink-0"
              style={{
                backgroundColor: themeColors.surfaceVariant,
                borderColor: themeColors.border,
                color: themeColors.textPrimary,
              }}
            >
              <ArrowLeft className="w-3.5 h-3.5" />
              {isArabic ? 'رجوع' : 'Back'}
            </button>
          )}
          <div
            className="w-8 h-8 rounded-lg flex items-center justify-center shrink-0"
            style={{ backgroundColor: `${themeColors.primary}20` }}
          >
            <BookOpen className="w-4 h-4" style={{ color: themeColors.primary }} />
          </div>
          <div className="min-w-0">
            <h1 className="text-sm font-bold tracking-tight truncate">
              {selectedMoshaf && selectedReciter
                ? `${selectedReciter.name} — ${selectedMoshaf.name}`
                : selectedReciter
                ? selectedReciter.name
                : isArabic
                ? 'القرآن الكريم'
                : 'Holy Quran'}
            </h1>
          </div>
        </div>

        {/* Breadcrumb Pills + Search + Actions */}
        <div className="flex items-center gap-2 flex-1 justify-end min-w-[240px]">
          <div className="hidden xl:flex items-center gap-1 text-[11px] font-semibold">
            <button
              onClick={() => {
                setSelectedReciter(null);
                setSelectedMoshaf(null);
                setSearchQuery('');
              }}
              className="px-2 py-1 rounded-md transition-colors"
              style={{
                backgroundColor: !selectedReciter
                  ? themeColors.primary
                  : themeColors.surfaceVariant,
                color: !selectedReciter ? '#fff' : themeColors.textSecondary,
              }}
            >
              {isArabic ? `القراء (${reciters.length})` : `Reciters (${reciters.length})`}
            </button>
            {selectedReciter && (
              <>
                <span className="opacity-40">/</span>
                <button
                  onClick={() => {
                    if (selectedReciter.moshaf.length > 1) {
                      setSelectedMoshaf(null);
                      setSearchQuery('');
                    }
                  }}
                  className="px-2 py-1 rounded-md truncate max-w-[150px]"
                  style={{
                    backgroundColor:
                      selectedReciter && !selectedMoshaf
                        ? themeColors.primary
                        : themeColors.surfaceVariant,
                    color:
                      selectedReciter && !selectedMoshaf
                        ? '#fff'
                        : themeColors.textSecondary,
                  }}
                >
                  {selectedReciter.name}
                </button>
              </>
            )}
            {selectedMoshaf && (
              <>
                <span className="opacity-40">/</span>
                <span
                  className="px-2 py-1 rounded-md truncate max-w-[160px]"
                  style={{
                    backgroundColor: themeColors.primary,
                    color: '#fff',
                  }}
                >
                  {selectedMoshaf.name} ({availableSurahs.length})
                </span>
              </>
            )}
          </div>

          <div className="relative w-48 sm:w-60">
            <Search className="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 opacity-50" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder={
                selectedMoshaf
                  ? isArabic
                    ? 'ابحث عن سورة...'
                    : 'Search Surah...'
                  : selectedReciter
                  ? isArabic
                    ? 'ابحث في الروايات...'
                    : 'Search narration...'
                  : isArabic
                  ? 'ابحث عن قارئ...'
                  : 'Search reciter...'
              }
              className="w-full pl-8 pr-2.5 py-1.5 rounded-lg border text-xs outline-none"
              style={{
                backgroundColor: themeColors.background,
                borderColor: themeColors.border,
                color: themeColors.textPrimary,
              }}
            />
          </div>

          {selectedMoshaf && currentMoshafQueue.length > 0 && (
            <button
              onClick={handlePlayAllSurahs}
              className="px-2.5 py-1.5 rounded-lg text-xs font-bold flex items-center gap-1.5 text-white shrink-0"
              style={{ backgroundColor: themeColors.primary }}
            >
              <Play className="w-3.5 h-3.5 fill-current" />
              {isArabic ? 'تشغيل الكل' : 'Play All'}
            </button>
          )}

          <button
            onClick={() => loadData(true)}
            disabled={isLoading}
            className="p-1.5 rounded-lg border transition-colors shrink-0"
            style={{
              backgroundColor: themeColors.surfaceVariant,
              borderColor: themeColors.border,
              color: themeColors.textPrimary,
            }}
            title={isArabic ? 'تحديث' : 'Refresh'}
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {/* Inline Status / Error Banner */}
      {(statusMessage || errorMessage) && (
        <div
          className="mb-2 px-3 py-1.5 rounded-lg border text-xs flex items-center justify-between shrink-0"
          style={{
            backgroundColor: errorMessage
              ? 'rgba(239,68,68,0.12)'
              : `${themeColors.primary}15`,
            borderColor: errorMessage
              ? 'rgba(239,68,68,0.35)'
              : `${themeColors.primary}40`,
            color: errorMessage ? '#fca5a5' : themeColors.textPrimary,
          }}
        >
          <span className="truncate">{errorMessage || statusMessage}</span>
          <button
            onClick={() => {
              setErrorMessage(null);
              setStatusMessage(null);
            }}
            className="text-[11px] opacity-70 hover:opacity-100 ml-2"
          >
            ✕
          </button>
        </div>
      )}

      {/* Main Responsive Grid Container (No Scroll) */}
      <div ref={gridContainerRef} className="flex-1 min-h-0 overflow-hidden flex flex-col">
        {isLoading ? (
          <div className="flex-1 flex items-center justify-center gap-2 text-xs opacity-70">
            <Loader2 className="w-5 h-5 animate-spin" style={{ color: themeColors.primary }} />
            <span>{isArabic ? 'جاري التحميل...' : 'Loading...'}</span>
          </div>
        ) : !selectedReciter ? (
          /* LEVEL 1: RECITERS */
          pagedReciters.length === 0 ? (
            <div className="flex-1 flex items-center justify-center text-xs opacity-60">
              {isArabic ? 'لا يوجد قراء مطابقون للبحث' : 'No matching reciters found'}
            </div>
          ) : (
            <div
              className="grid gap-2.5 content-start flex-1 min-h-0 overflow-hidden"
              style={{
                gridTemplateColumns: `repeat(${gridCols}, minmax(0, 1fr))`,
              }}
            >
              {pagedReciters.map((reciter) => (
                <button
                  key={reciter.id}
                  onClick={() => handleSelectReciter(reciter)}
                  className="p-2.5 rounded-xl border text-left transition-all hover:scale-[1.01] flex items-center gap-2.5 h-[68px]"
                  style={{
                    backgroundColor: themeColors.surface,
                    borderColor: themeColors.border,
                  }}
                >
                  <div
                    className="w-10 h-10 rounded-xl flex items-center justify-center shrink-0 font-bold text-sm"
                    style={{
                      backgroundColor: `${themeColors.primary}20`,
                      color: themeColors.primary,
                    }}
                  >
                    {reciter.letter || <User className="w-4 h-4" />}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="font-bold text-xs truncate">{reciter.name}</div>
                    <div className="flex items-center gap-1.5 mt-1">
                      <span
                        className="text-[10px] px-1.5 py-0.5 rounded font-semibold"
                        style={{
                          backgroundColor: themeColors.surfaceVariant,
                          color: themeColors.textSecondary,
                        }}
                      >
                        {reciter.moshaf.length}{' '}
                        {isArabic
                          ? reciter.moshaf.length === 1
                            ? 'رواية'
                            : 'روايات'
                          : reciter.moshaf.length === 1
                          ? 'Narration'
                          : 'Narrations'}
                      </span>
                      <span className="text-[10px] opacity-60 truncate">
                        {reciter.moshaf[0]?.surahTotal || 0}{' '}
                        {isArabic ? 'سورة' : 'Surahs'}
                      </span>
                    </div>
                  </div>
                </button>
              ))}
            </div>
          )
        ) : !selectedMoshaf ? (
          /* LEVEL 2: RECITATIONS (MOSHAF / REWAYAT) */
          <div
            className="grid gap-2.5 content-start flex-1 min-h-0 overflow-hidden"
            style={{
              gridTemplateColumns: `repeat(${gridCols}, minmax(0, 1fr))`,
            }}
          >
            {pagedMoshafs.map((moshaf) => (
              <button
                key={moshaf.id}
                onClick={() => handleSelectMoshaf(moshaf)}
                className="p-3 rounded-xl border text-left transition-all hover:scale-[1.01] flex items-center gap-3 h-[72px]"
                style={{
                  backgroundColor: themeColors.surface,
                  borderColor: themeColors.border,
                }}
              >
                <div
                  className="w-10 h-10 rounded-xl flex items-center justify-center shrink-0"
                  style={{
                    backgroundColor: `${themeColors.primary}20`,
                    color: themeColors.primary,
                  }}
                >
                  <Layers className="w-5 h-5" />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="font-bold text-xs truncate">{moshaf.name}</div>
                  <div className="text-[11px] opacity-70 mt-0.5">
                    {moshaf.surahList.length}{' '}
                    {isArabic ? 'سورة متاحة' : 'Available Surahs'}
                  </div>
                </div>
              </button>
            ))}
          </div>
        ) : (
          /* LEVEL 3: SURAHS */
          pagedSurahs.length === 0 ? (
            <div className="flex-1 flex items-center justify-center text-xs opacity-60">
              {isArabic ? 'لا توجد سور مطابقة للبحث' : 'No matching Surahs found'}
            </div>
          ) : (
            <div
              className="grid gap-2 content-start flex-1 min-h-0 overflow-hidden"
              style={{
                gridTemplateColumns: `repeat(${gridCols}, minmax(0, 1fr))`,
              }}
            >
              {pagedSurahs.map((surah) => {
                const audioItem = quranService.convertSurahToAudioItem(
                  selectedReciter,
                  selectedMoshaf,
                  surah,
                  isArabic
                );
                const isCurrent = currentPlayingId === audioItem.id;
                const isFav = favoriteIds.has(audioItem.id);
                const isDownloading = downloadingId === audioItem.id;

                return (
                  <div
                    key={surah.id}
                    className="p-2 rounded-xl border flex items-center justify-between gap-2 h-[62px] transition-all"
                    style={{
                      backgroundColor: isCurrent
                        ? `${themeColors.primary}18`
                        : themeColors.surface,
                      borderColor: isCurrent ? themeColors.primary : themeColors.border,
                    }}
                  >
                    <div
                      onClick={() => handlePlaySurah(surah)}
                      className="flex items-center gap-2 min-w-0 flex-1 cursor-pointer"
                    >
                      <div
                        className="w-8 h-8 rounded-lg flex items-center justify-center font-bold text-[11px] shrink-0"
                        style={{
                          backgroundColor: isCurrent
                            ? themeColors.primary
                            : themeColors.surfaceVariant,
                          color: isCurrent ? '#fff' : themeColors.textPrimary,
                        }}
                      >
                        {isCurrent && isPlaying ? (
                          <Volume2 className="w-4 h-4 animate-pulse" />
                        ) : (
                          String(surah.id).padStart(3, '0')
                        )}
                      </div>
                      <div className="min-w-0 flex-1">
                        <div className="font-bold text-xs truncate">{audioItem.title}</div>
                        <div className="text-[10px] opacity-60 truncate">
                          {surah.makkia
                            ? isArabic
                              ? 'مكية'
                              : 'Makkiyah'
                            : isArabic
                            ? 'مدنية'
                            : 'Madaniyah'}
                        </div>
                      </div>
                    </div>

                    {/* Action Buttons */}
                    <div className="flex items-center gap-1 shrink-0">
                      <button
                        onClick={() => handlePlaySurah(surah)}
                        className="p-1.5 rounded-lg text-white"
                        style={{ backgroundColor: themeColors.primary }}
                        title={isArabic ? 'تشغيل' : 'Play'}
                      >
                        <Play className="w-3 h-3 fill-current" />
                      </button>
                      <button
                        onClick={() => {
                          onEnqueueTrack(audioItem);
                          setStatusMessage(
                            isArabic
                              ? `تمت إضافة ${audioItem.title} إلى قائمة الانتظار`
                              : `Added ${audioItem.title} to Queue`
                          );
                        }}
                        className="p-1.5 rounded-lg border"
                        style={{
                          backgroundColor: themeColors.surfaceVariant,
                          borderColor: themeColors.border,
                        }}
                        title={isArabic ? 'إضافة إلى قائمة الانتظار' : 'Add to Queue'}
                      >
                        <ListMusic className="w-3 h-3" />
                      </button>
                      <button
                        onClick={() => setPlaylistModalItem(audioItem)}
                        className="p-1.5 rounded-lg border"
                        style={{
                          backgroundColor: themeColors.surfaceVariant,
                          borderColor: themeColors.border,
                        }}
                        title={isArabic ? 'إضافة إلى قائمة تشغيل' : 'Add to Playlist'}
                      >
                        <Plus className="w-3 h-3" />
                      </button>
                      <button
                        onClick={() => onToggleFavorite(audioItem)}
                        className="p-1.5 rounded-lg border"
                        style={{
                          backgroundColor: themeColors.surfaceVariant,
                          borderColor: themeColors.border,
                          color: isFav ? '#ef4444' : themeColors.textPrimary,
                        }}
                        title={isArabic ? 'مفضلة' : 'Favorite'}
                      >
                        <Heart className={`w-3 h-3 ${isFav ? 'fill-current' : ''}`} />
                      </button>
                      <button
                        onClick={() => handleDownloadSurah(surah)}
                        disabled={isDownloading}
                        className="p-1.5 rounded-lg border"
                        style={{
                          backgroundColor: themeColors.surfaceVariant,
                          borderColor: themeColors.border,
                        }}
                        title={isArabic ? 'تنزيل MP3' : 'Download MP3'}
                      >
                        {isDownloading ? (
                          <Loader2 className="w-3 h-3 animate-spin" />
                        ) : (
                          <Download className="w-3 h-3" />
                        )}
                      </button>
                      <button
                        onClick={() => onSendToDeckA(audioItem)}
                        className="px-1.5 py-1 rounded text-[10px] font-bold border"
                        style={{
                          borderColor: themeColors.accentA,
                          color: themeColors.accentA,
                        }}
                        title="Deck A"
                      >
                        A
                      </button>
                      <button
                        onClick={() => onSendToDeckB(audioItem)}
                        className="px-1.5 py-1 rounded text-[10px] font-bold border"
                        style={{
                          borderColor: themeColors.accentB,
                          color: themeColors.accentB,
                        }}
                        title="Deck B"
                      >
                        B
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          )
        )}
      </div>

      {/* Compact Pagination Footer */}
      <div
        className="mt-2 pt-2 border-t flex items-center justify-between text-xs shrink-0"
        style={{ borderColor: themeColors.border }}
      >
        <div className="opacity-70 text-[11px]">
          {isArabic
            ? `الإجمالي: ${activeItemsCount} • صفحة ${safePage} من ${totalPages}`
            : `Total: ${activeItemsCount} • Page ${safePage} of ${totalPages}`}
        </div>
        <div className="flex items-center gap-1.5">
          <button
            onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
            disabled={safePage <= 1}
            className="px-2.5 py-1 rounded-lg border disabled:opacity-40 flex items-center gap-1 text-[11px] font-semibold"
            style={{
              backgroundColor: themeColors.surface,
              borderColor: themeColors.border,
            }}
          >
            <ChevronLeft className="w-3.5 h-3.5" />
            {isArabic ? 'السابق' : 'Prev'}
          </button>
          <button
            onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
            disabled={safePage >= totalPages}
            className="px-2.5 py-1 rounded-lg border disabled:opacity-40 flex items-center gap-1 text-[11px] font-semibold"
            style={{
              backgroundColor: themeColors.surface,
              borderColor: themeColors.border,
            }}
          >
            {isArabic ? 'التالي' : 'Next'}
            <ChevronRight className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* Add to Playlist Modal */}
      {playlistModalItem && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div
            className="w-full max-w-md rounded-2xl border p-4 space-y-3"
            style={{
              backgroundColor: themeColors.surface,
              borderColor: themeColors.border,
              color: themeColors.textPrimary,
            }}
          >
            <div className="flex items-center justify-between">
              <h3 className="font-bold text-sm">
                {isArabic ? 'إضافة إلى قائمة تشغيل' : 'Add to Playlist'}
              </h3>
              <button
                onClick={() => setPlaylistModalItem(null)}
                className="text-xs opacity-60 hover:opacity-100"
              >
                ✕
              </button>
            </div>
            <div className="text-xs opacity-75 truncate">{playlistModalItem.title}</div>

            <div className="space-y-1.5 max-h-40 overflow-y-auto">
              {playlists.map((pl) => (
                <button
                  key={pl.id}
                  onClick={() => {
                    onAddToPlaylist(pl.id, playlistModalItem);
                    setPlaylistModalItem(null);
                    setStatusMessage(
                      isArabic
                        ? `تمت الإضافة إلى "${pl.name}"`
                        : `Added to "${pl.name}"`
                    );
                  }}
                  className="w-full p-2 rounded-lg border text-left text-xs font-semibold flex items-center justify-between"
                  style={{
                    backgroundColor: themeColors.surfaceVariant,
                    borderColor: themeColors.border,
                  }}
                >
                  <span>{pl.name}</span>
                  <span className="opacity-60">{pl.songCount}</span>
                </button>
              ))}
            </div>

            <div className="flex items-center gap-2 pt-2 border-t" style={{ borderColor: themeColors.border }}>
              <input
                type="text"
                value={newPlaylistName}
                onChange={(e) => setNewPlaylistName(e.target.value)}
                placeholder={isArabic ? 'اسم قائمة جديدة...' : 'New playlist name...'}
                className="flex-1 px-2.5 py-1.5 rounded-lg border text-xs outline-none"
                style={{
                  backgroundColor: themeColors.background,
                  borderColor: themeColors.border,
                  color: themeColors.textPrimary,
                }}
              />
              <button
                onClick={() => {
                  const trimmed = newPlaylistName.trim();
                  if (!trimmed) return;
                  onCreatePlaylistAndAdd(trimmed, playlistModalItem);
                  setNewPlaylistName('');
                  setPlaylistModalItem(null);
                }}
                className="px-3 py-1.5 rounded-lg text-xs font-bold text-white"
                style={{ backgroundColor: themeColors.primary }}
              >
                {isArabic ? 'إنشاء' : 'Create'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
