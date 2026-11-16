import React, { useState, useEffect, useRef } from 'react';
import {
  Radio as RadioIcon,
  Search,
  Play,
  Pause,
  Heart,
  ListPlus,
  Globe,
  Flame,
  Volume2,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react';
import { RadioStation, AudioItem, ThemeColors } from '../../types';
import { radioService, EGYPT_QURAN_STATION } from '../../services/RadioService';
import { db } from '../../storage/db';

interface RadioScreenProps {
  themeColors: ThemeColors;
  isArabic: boolean;
  onPlayStation: (station: RadioStation) => void;
  currentPlayingStationId: string | null;
  isPlayingRadio: boolean;
  onSendToDeckA: (item: AudioItem) => void;
  onSendToDeckB: (item: AudioItem) => void;
  onEnqueueSong: (item: AudioItem) => void;
}

export const RadioScreen: React.FC<RadioScreenProps> = ({
  themeColors,
  isArabic,
  onPlayStation,
  currentPlayingStationId,
  isPlayingRadio,
  onSendToDeckA,
  onSendToDeckB,
  onEnqueueSong,
}) => {
  const [activeTab, setActiveTab] = useState<'EG' | 'WORLD' | 'FAVORITES'>('EG');
  const [stations, setStations] = useState<RadioStation[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [favoriteStations, setFavoriteStations] = useState<RadioStation[]>([]);
  const favoriteIds = new Set(favoriteStations.map((station) => station.id));

  const gridRef = useRef<HTMLDivElement>(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(12);
  const [gridCols, setGridCols] = useState(3);

  useEffect(() => {
    let mounted = true;
    db.getRadioFavorites()
      .then((saved) => {
        if (mounted) setFavoriteStations(saved);
      })
      .catch(() => {});
    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    let mounted = true;
    if (activeTab === 'FAVORITES') {
      setIsLoading(false);
      return () => {
        mounted = false;
      };
    }
    setIsLoading(true);
    const country = activeTab === 'EG' ? 'EG' : null;
    radioService
      .getStations(country, searchQuery)
      .then((result) => {
        if (!mounted) return;
        setStations(result);
        setIsLoading(false);
      })
      .catch(() => {
        if (mounted) {
          setStations([]);
          setIsLoading(false);
        }
      });
    return () => {
      mounted = false;
    };
  }, [activeTab, searchQuery]);

  useEffect(() => {
    const updateMetrics = () => {
      const el = gridRef.current;
      const width = el?.clientWidth || window.innerWidth - 64;
      const height = el?.clientHeight || window.innerHeight - 230;
      const cols = width >= 1320 ? 4 : width >= 920 ? 3 : width >= 600 ? 2 : 1;
      const rowHeight = 66;
      const rows = Math.max(2, Math.floor((height + 8) / rowHeight));
      setGridCols(cols);
      setItemsPerPage(Math.max(cols * 2, cols * rows));
    };
    updateMetrics();
    const obs = new ResizeObserver(updateMetrics);
    if (gridRef.current) obs.observe(gridRef.current);
    window.addEventListener('resize', updateMetrics);
    return () => {
      obs.disconnect();
      window.removeEventListener('resize', updateMetrics);
    };
  }, []);

  useEffect(() => {
    setCurrentPage(1);
  }, [activeTab, searchQuery, itemsPerPage]);

  const toggleFavorite = async (stationId: string) => {
    const station = [...stations, ...favoriteStations].find(
      (item) => item.id === stationId
    );
    if (!station) return;
    try {
      await db.toggleRadioFavorite(station);
      setFavoriteStations(await db.getRadioFavorites());
    } catch {
      // ignore
    }
  };

  const displayedStations =
    activeTab === 'FAVORITES'
      ? favoriteStations.filter(
          (station) =>
            !searchQuery.trim() ||
            `${station.name} ${station.tags || ''}`
              .toLocaleLowerCase()
              .includes(searchQuery.trim().toLocaleLowerCase())
        )
      : [...stations].sort(
          (left, right) =>
            Number(favoriteIds.has(right.id)) - Number(favoriteIds.has(left.id))
        );

  const stationToAudioItem = (station: RadioStation): AudioItem => ({
    id: 'radio_' + station.id,
    title: station.name,
    artist: 'Live Radio FM',
    album: station.tags || 'Broadcasting',
    duration: 0,
    uri: station.streamUrls[0] || '',
    fallbackUris: station.streamUrls.slice(1),
    addedDate: Date.now(),
  });

  const totalPages = Math.max(1, Math.ceil(displayedStations.length / itemsPerPage));
  const safePage = Math.min(currentPage, totalPages);
  const pagedStations = displayedStations.slice(
    (safePage - 1) * itemsPerPage,
    safePage * itemsPerPage
  );

  return (
    <div
      id="radio-screen"
      className="h-full min-h-0 flex flex-col justify-between overflow-hidden select-none p-3"
      style={{
        backgroundColor: themeColors.background,
        color: themeColors.textPrimary,
      }}
    >
      {/* Top Bar (No Subtitles) */}
      <div
        className="rounded-xl p-2.5 border mb-2.5 shrink-0 flex flex-wrap items-center justify-between gap-2"
        style={{
          backgroundColor: themeColors.surface,
          borderColor: themeColors.border,
        }}
      >
        <div className="flex items-center gap-2">
          <div
            className="w-8 h-8 rounded-lg flex items-center justify-center"
            style={{ backgroundColor: `${themeColors.primary}20` }}
          >
            <RadioIcon className="w-4 h-4" style={{ color: themeColors.primary }} />
          </div>
          <h1 className="text-sm font-bold tracking-tight">
            {isArabic ? 'محطات الراديو المباشرة' : 'Live Radio FM'}
          </h1>
        </div>

        {/* Scope Tabs */}
        <div className="flex items-center gap-1.5">
          <button
            onClick={() => setActiveTab('EG')}
            className="px-2.5 py-1 rounded-lg text-xs font-bold border flex items-center gap-1"
            style={{
              backgroundColor:
                activeTab === 'EG' ? themeColors.primary : themeColors.surfaceVariant,
              borderColor:
                activeTab === 'EG' ? themeColors.primary : themeColors.border,
              color: activeTab === 'EG' ? '#ffffff' : themeColors.textPrimary,
            }}
          >
            <Flame className="w-3.5 h-3.5" />
            {isArabic ? 'راديو مصر' : 'Egypt Radio'}
          </button>

          <button
            onClick={() => setActiveTab('WORLD')}
            className="px-2.5 py-1 rounded-lg text-xs font-bold border flex items-center gap-1"
            style={{
              backgroundColor:
                activeTab === 'WORLD' ? themeColors.primary : themeColors.surfaceVariant,
              borderColor:
                activeTab === 'WORLD' ? themeColors.primary : themeColors.border,
              color: activeTab === 'WORLD' ? '#ffffff' : themeColors.textPrimary,
            }}
          >
            <Globe className="w-3.5 h-3.5" />
            {isArabic ? 'راديو عالمي' : 'World Stations'}
          </button>

          <button
            onClick={() => setActiveTab('FAVORITES')}
            className="px-2.5 py-1 rounded-lg text-xs font-bold border flex items-center gap-1"
            style={{
              backgroundColor:
                activeTab === 'FAVORITES'
                  ? themeColors.primary
                  : themeColors.surfaceVariant,
              borderColor:
                activeTab === 'FAVORITES' ? themeColors.primary : themeColors.border,
              color: activeTab === 'FAVORITES' ? '#ffffff' : themeColors.textPrimary,
            }}
          >
            <Heart className="w-3.5 h-3.5" />
            {isArabic
              ? `المفضلة (${favoriteStations.length})`
              : `Favorites (${favoriteStations.length})`}
          </button>
        </div>

        {/* Search */}
        <div
          className="flex items-center gap-2 px-2.5 py-1.5 rounded-lg border text-xs w-52 sm:w-64"
          style={{
            backgroundColor: themeColors.background,
            borderColor: themeColors.border,
          }}
        >
          <Search className="w-3.5 h-3.5 opacity-60 shrink-0" />
          <input
            type="text"
            placeholder={isArabic ? 'بحث عن محطة إذاعية...' : 'Search stations...'}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="bg-transparent outline-none w-full text-xs"
          />
        </div>
      </div>

      {/* Stations Grid (No Scroll) */}
      <div ref={gridRef} className="flex-1 min-h-0 overflow-hidden flex flex-col">
        {isLoading ? (
          <div className="flex-1 flex items-center justify-center text-xs opacity-70">
            {isArabic ? 'جاري تحميل المحطات...' : 'Loading radio stations...'}
          </div>
        ) : pagedStations.length === 0 ? (
          <div className="flex-1 flex items-center justify-center text-xs opacity-60">
            {isArabic ? 'لا توجد محطات مطابقة' : 'No matching stations found'}
          </div>
        ) : (
          <div
            className="grid gap-2 content-start flex-1 min-h-0 overflow-hidden"
            style={{
              gridTemplateColumns: `repeat(${gridCols}, minmax(0, 1fr))`,
            }}
          >
            {pagedStations.map((st) => {
              const isCurrent = currentPlayingStationId === st.id;
              const isFav = favoriteIds.has(st.id);
              const isQuran = st.id === EGYPT_QURAN_STATION.id;

              return (
                <div
                  key={st.id}
                  className="p-2 rounded-xl border flex items-center justify-between gap-2 h-[60px]"
                  style={{
                    backgroundColor: isCurrent
                      ? `${themeColors.primary}18`
                      : themeColors.surface,
                    borderColor: isCurrent ? themeColors.primary : themeColors.border,
                  }}
                >
                  <div
                    onClick={() => onPlayStation(st)}
                    className="flex items-center gap-2 min-w-0 flex-1 cursor-pointer"
                  >
                    <div
                      className="w-9 h-9 rounded-lg flex items-center justify-center shrink-0"
                      style={{
                        backgroundColor: isCurrent
                          ? themeColors.primary
                          : themeColors.surfaceVariant,
                        color: isCurrent ? '#fff' : themeColors.primary,
                      }}
                    >
                      {isCurrent && isPlayingRadio ? (
                        <Volume2 className="w-4 h-4 animate-pulse" />
                      ) : (
                        <RadioIcon className="w-4 h-4" />
                      )}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="font-bold text-xs truncate">
                        {isQuran ? '🕌 ' : ''}
                        {st.name}
                      </div>
                      <div className="text-[10px] opacity-60 truncate">
                        {st.countryCode} • {st.codec} {st.bitrate ? `${st.bitrate}kbps` : ''}
                      </div>
                    </div>
                  </div>

                  <div className="flex items-center gap-1 shrink-0">
                    <button
                      onClick={() => onPlayStation(st)}
                      className="p-1.5 rounded-lg text-white"
                      style={{ backgroundColor: themeColors.primary }}
                      title={isArabic ? 'تشغيل' : 'Play'}
                    >
                      {isCurrent && isPlayingRadio ? (
                        <Pause className="w-3 h-3 fill-current" />
                      ) : (
                        <Play className="w-3 h-3 fill-current" />
                      )}
                    </button>
                    <button
                      onClick={() => toggleFavorite(st.id)}
                      className="p-1.5 rounded-lg border"
                      style={{
                        backgroundColor: themeColors.surfaceVariant,
                        borderColor: themeColors.border,
                        color: isFav ? '#ef4444' : themeColors.textPrimary,
                      }}
                    >
                      <Heart className={`w-3 h-3 ${isFav ? 'fill-current' : ''}`} />
                    </button>
                    <button
                      onClick={() => onEnqueueSong(stationToAudioItem(st))}
                      className="p-1.5 rounded-lg border"
                      style={{
                        backgroundColor: themeColors.surfaceVariant,
                        borderColor: themeColors.border,
                      }}
                      title="Queue"
                    >
                      <ListPlus className="w-3 h-3" />
                    </button>
                    <button
                      onClick={() => onSendToDeckA(stationToAudioItem(st))}
                      className="px-1.5 py-1 rounded text-[10px] font-bold border"
                      style={{
                        borderColor: themeColors.accentA,
                        color: themeColors.accentA,
                      }}
                    >
                      A
                    </button>
                    <button
                      onClick={() => onSendToDeckB(stationToAudioItem(st))}
                      className="px-1.5 py-1 rounded text-[10px] font-bold border"
                      style={{
                        borderColor: themeColors.accentB,
                        color: themeColors.accentB,
                      }}
                    >
                      B
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Pagination Bar */}
      <div
        className="mt-2 pt-2 border-t flex items-center justify-between text-xs shrink-0"
        style={{ borderColor: themeColors.border }}
      >
        <div className="opacity-70 text-[11px]">
          {isArabic
            ? `المحطات: ${displayedStations.length} • صفحة ${safePage} من ${totalPages}`
            : `Stations: ${displayedStations.length} • Page ${safePage} of ${totalPages}`}
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
    </div>
  );
};
