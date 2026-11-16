import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowLeft,
  ChevronLeft,
  ChevronRight,
  Disc,
  Download,
  FolderOpen,
  Globe,
  ListMusic,
  ListPlus,
  Music2,
  Play,
  Plus,
  RefreshCw,
  Search,
  Sparkles,
  Users,
  X,
} from 'lucide-react';
import {
  AlbumatyHomeData,
  AlbumatyLink,
  AlbumatySection,
  downloadOnlineItem,
  downloadOnlineItemAsync,
  onlineMusicService,
} from '../../services/OnlineMusicService';
import { AudioItem, AudiusTrack, Playlist, ThemeColors } from '../../types';

interface OnlineMusicScreenProps {
  themeColors: ThemeColors;
  isArabic: boolean;
  onPlayOnlineTrack: (track: AudioItem) => void;
  onSendToDeckA: (track: AudioItem) => void;
  onSendToDeckB: (track: AudioItem) => void;
  onEnqueueTrack: (track: AudioItem) => void;
  onSaveToLibrary: (track: AudioItem) => void;
  playlists: Playlist[];
  onAddSongToPlaylist: (playlistId: string, track: AudioItem) => Promise<void>;
  onCreatePlaylist: (name: string) => Promise<Playlist>;
  onOpenQueue: () => void;
}

const EMPTY_CATALOG: AlbumatyHomeData = {
  categories: [],
  albums: [],
  songs: [],
  artists: [],
};

type OnlineSourceTab = 'ALBUMATY' | 'OLDIES' | 'AUDIUS';
type CatalogSubTab = 'SONGS' | 'ARTISTS' | 'ALBUMS' | 'CATEGORIES';

function isSong(url: string): boolean {
  try {
    const parsed = new URL(url.startsWith('/') ? `https://www.hibamusic.com${url}` : url);
    const parts = parsed.pathname.toLowerCase().split('/').filter(Boolean);
    if (parts.length === 0) return false;
    const kind = ['n', 'a', 'ar'].includes(parts[0]) ? (parts[1] || '') : (parts[0] || '');
    return (
      kind === 'song' ||
      kind === 'titre' ||
      kind === 'track' ||
      parts.includes('song') ||
      parts.includes('titre') ||
      parts.includes('download') ||
      parts.includes('song.php') ||
      /\.(?:mp3|m4a|wav|ogg)$/i.test(parsed.pathname)
    );
  } catch {
    return false;
  }
}

function formatMs(ms?: number): string {
  if (!ms || Number.isNaN(ms)) return '0:00';
  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}

export const OnlineMusicScreen: React.FC<OnlineMusicScreenProps> = ({
  themeColors,
  isArabic,
  onPlayOnlineTrack,
  onSendToDeckA,
  onSendToDeckB,
  onEnqueueTrack,
  onSaveToLibrary,
  playlists,
  onAddSongToPlaylist,
  onCreatePlaylist,
  onOpenQueue,
}) => {
  const [source, setSource] = useState<OnlineSourceTab>('ALBUMATY');
  const [subTab, setSubTab] = useState<CatalogSubTab>('SONGS');
  const [albumatyHome, setAlbumatyHome] = useState<AlbumatyHomeData>(EMPTY_CATALOG);
  const [oldiesHome, setOldiesHome] = useState<AlbumatyHomeData>(EMPTY_CATALOG);
  const [searchResults, setSearchResults] = useState<AlbumatyHomeData | null>(null);
  const [activeSection, setActiveSection] = useState<AlbumatySection | null>(null);
  const [sectionHistory, setSectionHistory] = useState<AlbumatySection[]>([]);
  const [audiusTracks, setAudiusTracks] = useState<AudiusTrack[]>([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [showPlaylistPicker, setShowPlaylistPicker] = useState(false);
  const [pendingPlaylistTrack, setPendingPlaylistTrack] = useState<AudioItem | null>(null);
  const [newPlaylistName, setNewPlaylistName] = useState('');
  const [playlistBusy, setPlaylistBusy] = useState(false);

  // Viewport-adaptive pagination for zero scrolling
  const gridRef = useRef<HTMLDivElement | null>(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(12);
  const [gridCols, setGridCols] = useState(3);

  const loadAlbumaty = async (force = false) => {
    setLoading(true);
    setErrorMessage(null);
    setActiveSection(null);
    setSectionHistory([]);
    setSearchResults(null);
    try {
      const data = await onlineMusicService.getAlbumatyHome(force);
      setAlbumatyHome(data);
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : isArabic
          ? 'تعذر تحميل ألبوماتي حالياً.'
          : 'Unable to load Albumaty right now.'
      );
    } finally {
      setLoading(false);
    }
  };

  const loadOldies = async (force = false) => {
    setLoading(true);
    setErrorMessage(null);
    setActiveSection(null);
    setSectionHistory([]);
    setSearchResults(null);
    try {
      const data = await onlineMusicService.getOldiesHome(force);
      setOldiesHome(data);
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : isArabic
          ? 'تعذر تحميل هبة ميوزيك (الزمن الجميل) حالياً.'
          : 'Unable to load Hiba Music (Oldies & Classics) right now.'
      );
    } finally {
      setLoading(false);
    }
  };

  const loadAudiusTrending = async () => {
    setLoading(true);
    setErrorMessage(null);
    try {
      const tracks = await onlineMusicService.getTrendingTracks();
      setAudiusTracks(tracks);
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : isArabic
          ? 'تعذر تحميل Audius حالياً.'
          : 'Unable to load Audius right now.'
      );
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    setQuery('');
    setSearchResults(null);
    setActiveSection(null);
    setSectionHistory([]);
    setSubTab('SONGS');
    setCurrentPage(1);
    if (source === 'ALBUMATY') {
      loadAlbumaty(false);
    } else if (source === 'OLDIES') {
      loadOldies(false);
    } else {
      loadAudiusTrending();
    }
  }, [source]);

  useEffect(() => {
    const updateMetrics = () => {
      const el = gridRef.current;
      const width = el?.clientWidth || window.innerWidth - 64;
      const height = el?.clientHeight || window.innerHeight - 250;
      const cols = width >= 1340 ? 4 : width >= 920 ? 3 : width >= 600 ? 2 : 1;
      const cardHeight = 68;
      const rows = Math.max(2, Math.floor((height + 8) / cardHeight));
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
  }, [source, subTab, activeSection]);

  useEffect(() => {
    setCurrentPage(1);
  }, [source, subTab, activeSection, searchResults, itemsPerPage]);

  const handleSearch = async () => {
    const trimmed = query.trim();
    if (!trimmed) {
      setSearchResults(null);
      if (source === 'AUDIUS') loadAudiusTrending();
      return;
    }

    setLoading(true);
    setErrorMessage(null);
    setActiveSection(null);
    setSectionHistory([]);
    try {
      if (source === 'ALBUMATY') {
        const result = await onlineMusicService.searchAlbumaty(trimmed);
        setSearchResults(result);
        if (result.songs.length > 0) setSubTab('SONGS');
        else if (result.artists.length > 0) setSubTab('ARTISTS');
        else if (result.albums.length > 0) setSubTab('ALBUMS');
      } else if (source === 'OLDIES') {
        const result = await onlineMusicService.searchOldies(trimmed);
        setSearchResults(result);
        if (result.songs.length > 0) setSubTab('SONGS');
        else if (result.artists.length > 0) setSubTab('ARTISTS');
        else if (result.albums.length > 0) setSubTab('ALBUMS');
      } else {
        const tracks = await onlineMusicService.searchAudius(trimmed);
        setAudiusTracks(tracks);
      }
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : isArabic
          ? 'فشل البحث.'
          : 'Search failed.'
      );
    } finally {
      setLoading(false);
    }
  };

  const openSection = async (link: AlbumatyLink) => {
    setLoading(true);
    setErrorMessage(null);
    try {
      const section =
        source === 'OLDIES'
          ? await onlineMusicService.getOldiesSection(link.url)
          : await onlineMusicService.getAlbumatySection(link.url);
      if (activeSection) {
        setSectionHistory((prev) => [...prev, activeSection]);
      }
      setActiveSection(section);
      const hasSongs = section.content.some((l) => isSong(l.url));
      if (hasSongs) setSubTab('SONGS');
      else setSubTab('ARTISTS');
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : isArabic
          ? 'تعذر فتح القسم.'
          : 'Unable to open section.'
      );
    } finally {
      setLoading(false);
    }
  };

  const handleBackFromSection = () => {
    if (sectionHistory.length > 0) {
      const prev = sectionHistory[sectionHistory.length - 1];
      setSectionHistory((h) => h.slice(0, -1));
      setActiveSection(prev);
      const hasSongs = prev.content.some((l) => isSong(l.url));
      if (hasSongs) setSubTab('SONGS');
      else setSubTab('ARTISTS');
    } else {
      setActiveSection(null);
      setSubTab('SONGS');
    }
  };

  const resolveLinkToAudioItem = async (link: AlbumatyLink): Promise<AudioItem> => {
    const provider = source === 'OLDIES' ? 'OLDIES' : 'ALBUMATY';
    const resolved =
      provider === 'OLDIES'
        ? await onlineMusicService.resolveOldiesSong(link.url)
        : await onlineMusicService.resolveAlbumatySong(link.url);
    const stableId = `${provider === 'OLDIES' ? 'oldies' : 'albumaty'}_${encodeURIComponent(
      link.url
    )}`;
    return onlineMusicService.convertResolvedToAudioItem(resolved, stableId, provider);
  };

  const playCatalogLink = async (link: AlbumatyLink) => {
    if (!isSong(link.url)) {
      await openSection(link);
      return;
    }
    setMessage(isArabic ? `جاري تحضير: ${link.title}...` : `Preparing: ${link.title}...`);
    setErrorMessage(null);
    try {
      const item = await resolveLinkToAudioItem(link);
      onPlayOnlineTrack(item);
      setMessage(isArabic ? `يعمل الآن: ${item.title}` : `Playing: ${item.title}`);
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : isArabic
          ? 'تعذر تشغيل الأغنية.'
          : 'Could not play song.'
      );
    }
  };

  const sendCatalogLinkToDeck = async (link: AlbumatyLink, deck: 'A' | 'B') => {
    setMessage(
      isArabic ? `جاري تحميل إلى Deck ${deck}...` : `Loading into Deck ${deck}...`
    );
    setErrorMessage(null);
    try {
      const item = await resolveLinkToAudioItem(link);
      if (deck === 'A') onSendToDeckA(item);
      else onSendToDeckB(item);
      setMessage(
        isArabic
          ? `تم إرسال "${item.title}" إلى Deck ${deck}`
          : `Sent "${item.title}" to Deck ${deck}`
      );
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : isArabic
          ? 'تعذر الإرسال إلى الديك.'
          : 'Failed to send to deck.'
      );
    }
  };

  const enqueueCatalogLink = async (link: AlbumatyLink) => {
    try {
      const item = await resolveLinkToAudioItem(link);
      onEnqueueTrack(item);
      setMessage(
        isArabic
          ? `تمت إضافة "${item.title}" إلى قائمة الانتظار`
          : `Added "${item.title}" to Queue`
      );
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : isArabic
          ? 'تعذر الإضافة إلى قائمة الانتظار.'
          : 'Could not add to queue.'
      );
    }
  };

  const openPlaylistModalForCatalogLink = async (link: AlbumatyLink) => {
    try {
      const item = await resolveLinkToAudioItem(link);
      setPendingPlaylistTrack(item);
      setShowPlaylistPicker(true);
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : isArabic
          ? 'تعذر تجهيز الأغنية للقائمة.'
          : 'Could not prepare song for playlist.'
      );
    }
  };

  const downloadCatalogLink = async (link: AlbumatyLink) => {
    setMessage(isArabic ? `جاري تنزيل: ${link.title}...` : `Downloading: ${link.title}...`);
    setErrorMessage(null);
    try {
      const item = await resolveLinkToAudioItem(link);
      await downloadOnlineItemAsync(item, (msg) =>
        setMessage(isArabic ? `جاري تنزيل: ${item.title}...` : msg)
      );
      onSaveToLibrary(item);
      setMessage(
        isArabic ? `تم تنزيل "${item.title}" بنجاح!` : `Downloaded "${item.title}"!`
      );
    } catch (error) {
      try {
        const item = await resolveLinkToAudioItem(link);
        if (downloadOnlineItem(item)) {
          onSaveToLibrary(item);
          setMessage(
            isArabic
              ? 'بدأ تنزيل الأغنية عبر المتصفح...'
              : 'Download initiated via browser...'
          );
          return;
        }
      } catch {
        // ignore fallback error
      }
      setErrorMessage(
        error instanceof Error
          ? error.message
          : isArabic
          ? 'تعذر تنزيل الأغنية.'
          : 'Could not download song.'
      );
    }
  };

  const downloadAudius = async (track: AudiusTrack) => {
    setMessage(isArabic ? `جاري تنزيل: ${track.title}...` : `Downloading: ${track.title}...`);
    setErrorMessage(null);
    const item = onlineMusicService.convertAudiusToAudioItem(track);
    try {
      await downloadOnlineItemAsync(item, (msg) =>
        setMessage(isArabic ? `جاري تنزيل: ${track.title}...` : msg)
      );
      onSaveToLibrary(item);
      setMessage(
        isArabic ? `تم تنزيل "${track.title}" بنجاح!` : `Downloaded "${track.title}"!`
      );
    } catch (error) {
      if (downloadOnlineItem(item)) {
        onSaveToLibrary(item);
        setMessage(
          isArabic
            ? 'بدأ تنزيل الأغنية عبر المتصفح...'
            : 'Download initiated via browser...'
        );
      } else {
        setErrorMessage(
          error instanceof Error
            ? error.message
            : isArabic
            ? 'تعذر تنزيل الأغنية.'
            : 'Could not download track.'
        );
      }
    }
  };

  const activeCatalog = useMemo<AlbumatyHomeData>(() => {
    if (searchResults) return searchResults;
    return source === 'OLDIES' ? oldiesHome : albumatyHome;
  }, [source, searchResults, oldiesHome, albumatyHome]);

  const sectionSongs = useMemo<AlbumatyLink[]>(() => {
    if (!activeSection) return [];
    return activeSection.content.filter((l) => isSong(l.url));
  }, [activeSection]);

  const sectionSublinks = useMemo<AlbumatyLink[]>(() => {
    if (!activeSection) return [];
    return activeSection.content.filter((l) => !isSong(l.url));
  }, [activeSection]);

  const currentItems = useMemo(() => {
    if (source === 'AUDIUS') {
      return audiusTracks.map((t) => ({ kind: 'AUDIUS' as const, track: t }));
    }
    if (activeSection) {
      if (subTab === 'SONGS' && sectionSongs.length > 0) {
        return sectionSongs.map((link) => ({ kind: 'SONG' as const, link }));
      }
      if (sectionSublinks.length > 0) {
        return sectionSublinks.map((link) => ({
          kind: isSong(link.url) ? ('SONG' as const) : ('SECTION' as const),
          link,
        }));
      }
      return sectionSongs.map((link) => ({ kind: 'SONG' as const, link }));
    }
    if (subTab === 'SONGS') {
      return activeCatalog.songs.map((link) => ({ kind: 'SONG' as const, link }));
    }
    if (subTab === 'ARTISTS') {
      return activeCatalog.artists.map((link) => ({ kind: 'SECTION' as const, link }));
    }
    if (subTab === 'ALBUMS') {
      return activeCatalog.albums.map((link) => ({ kind: 'SECTION' as const, link }));
    }
    return activeCatalog.categories.map((link) => ({ kind: 'SECTION' as const, link }));
  }, [source, activeSection, sectionSongs, sectionSublinks, subTab, activeCatalog, audiusTracks]);

  const totalPages = Math.max(1, Math.ceil(currentItems.length / itemsPerPage));
  const safePage = Math.min(currentPage, totalPages);
  const pagedItems = currentItems.slice(
    (safePage - 1) * itemsPerPage,
    safePage * itemsPerPage
  );

  const handleSelectPlaylist = async (playlistId: string) => {
    if (!pendingPlaylistTrack) return;
    setPlaylistBusy(true);
    try {
      await onAddSongToPlaylist(playlistId, pendingPlaylistTrack);
      onSaveToLibrary(pendingPlaylistTrack);
      setShowPlaylistPicker(false);
      setPendingPlaylistTrack(null);
      setMessage(isArabic ? 'تمت الإضافة إلى قائمة التشغيل!' : 'Added to playlist!');
    } finally {
      setPlaylistBusy(false);
    }
  };

  const handleCreateAndAdd = async () => {
    const trimmed = newPlaylistName.trim();
    if (!trimmed || !pendingPlaylistTrack) return;
    setPlaylistBusy(true);
    try {
      const created = await onCreatePlaylist(trimmed);
      await onAddSongToPlaylist(created.id, pendingPlaylistTrack);
      onSaveToLibrary(pendingPlaylistTrack);
      setNewPlaylistName('');
      setShowPlaylistPicker(false);
      setPendingPlaylistTrack(null);
      setMessage(
        isArabic
          ? `تم إنشاء قائمة "${created.name}" وإضافة الأغنية!`
          : `Created "${created.name}" and added song!`
      );
    } finally {
      setPlaylistBusy(false);
    }
  };

  return (
    <div
      className="h-full min-h-0 flex flex-col overflow-hidden p-3 select-none"
      style={{ backgroundColor: themeColors.background, color: themeColors.textPrimary }}
    >
      {/* Top Control Header (No Subtitles) */}
      <div
        className="rounded-xl p-2.5 border mb-2.5 shrink-0 flex flex-wrap items-center justify-between gap-2"
        style={{
          backgroundColor: themeColors.surface,
          borderColor: themeColors.border,
        }}
      >
        <div className="flex items-center gap-2 min-w-0">
          {activeSection && (
            <button
              onClick={handleBackFromSection}
              className="px-2.5 py-1.5 rounded-lg border flex items-center gap-1 text-xs font-bold shrink-0"
              style={{
                backgroundColor: themeColors.surfaceVariant,
                borderColor: themeColors.border,
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
            <Globe className="w-4 h-4" style={{ color: themeColors.primary }} />
          </div>
          <h1 className="text-sm font-bold tracking-tight truncate">
            {activeSection
              ? activeSection.title
              : isArabic
              ? 'الموسيقى أونلاين'
              : 'Online Music'}
          </h1>
        </div>

        {/* Source Switcher: Albumaty | Oldies & Classics | Audius */}
        <div className="flex items-center gap-1.5 flex-wrap">
          <button
            onClick={() => setSource('ALBUMATY')}
            className="px-2.5 py-1.5 rounded-lg text-xs font-bold transition-all border"
            style={{
              backgroundColor:
                source === 'ALBUMATY' ? themeColors.primary : themeColors.surfaceVariant,
              borderColor:
                source === 'ALBUMATY' ? themeColors.primary : themeColors.border,
              color: source === 'ALBUMATY' ? '#fff' : themeColors.textPrimary,
            }}
          >
            {isArabic ? 'ألبوماتي (Albumaty)' : 'Albumaty'}
          </button>
          <button
            onClick={() => setSource('OLDIES')}
            className="px-2.5 py-1.5 rounded-lg text-xs font-bold transition-all border flex items-center gap-1"
            style={{
              backgroundColor:
                source === 'OLDIES' ? themeColors.primary : themeColors.surfaceVariant,
              borderColor:
                source === 'OLDIES' ? themeColors.primary : themeColors.border,
              color: source === 'OLDIES' ? '#fff' : themeColors.textPrimary,
            }}
          >
            <Sparkles className="w-3.5 h-3.5" />
            {isArabic ? 'هبة ميوزيك (الزمن الجميل)' : 'Hiba Music (Oldies)'}
          </button>
          <button
            onClick={() => setSource('AUDIUS')}
            className="px-2.5 py-1.5 rounded-lg text-xs font-bold transition-all border"
            style={{
              backgroundColor:
                source === 'AUDIUS' ? themeColors.primary : themeColors.surfaceVariant,
              borderColor:
                source === 'AUDIUS' ? themeColors.primary : themeColors.border,
              color: source === 'AUDIUS' ? '#fff' : themeColors.textPrimary,
            }}
          >
            Audius
          </button>
        </div>

        {/* Search & Refresh */}
        <div className="flex items-center gap-1.5 flex-1 justify-end min-w-[240px]">
          <div className="relative w-48 sm:w-60">
            <Search className="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 opacity-50" />
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
              placeholder={
                source === 'OLDIES'
                  ? isArabic
                    ? 'ابحث في هبة ميوزيك (الزمن الجميل)...'
                    : 'Search Hiba Music (Oldies)...'
                  : isArabic
                  ? 'ابحث عن أغنية أو فنان...'
                  : 'Search song or artist...'
              }
              className="w-full pl-8 pr-2.5 py-1.5 rounded-lg border text-xs outline-none"
              style={{
                backgroundColor: themeColors.background,
                borderColor: themeColors.border,
                color: themeColors.textPrimary,
              }}
            />
          </div>
          <button
            onClick={handleSearch}
            className="px-2.5 py-1.5 rounded-lg text-xs font-bold text-white shrink-0"
            style={{ backgroundColor: themeColors.primary }}
          >
            {isArabic ? 'بحث' : 'Search'}
          </button>
          <button
            onClick={onOpenQueue}
            className="px-2.5 py-1.5 rounded-lg border text-xs font-bold flex items-center gap-1 shrink-0"
            style={{
              backgroundColor: themeColors.surfaceVariant,
              borderColor: themeColors.border,
            }}
          >
            <ListMusic className="w-3.5 h-3.5" />
            Queue
          </button>
          <button
            onClick={() => {
              if (source === 'ALBUMATY') loadAlbumaty(true);
              else if (source === 'OLDIES') loadOldies(true);
              else loadAudiusTrending();
            }}
            disabled={loading}
            className="p-1.5 rounded-lg border shrink-0"
            style={{
              backgroundColor: themeColors.surfaceVariant,
              borderColor: themeColors.border,
            }}
            title={isArabic ? 'تحديث' : 'Refresh'}
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {/* Category Sub-Tabs for Albumaty / Oldies */}
      {source !== 'AUDIUS' && (
        <div className="flex items-center justify-between gap-2 mb-2 shrink-0">
          <div className="flex items-center gap-1.5 flex-wrap">
            {activeSection ? (
              <>
                {sectionSongs.length > 0 && (
                  <button
                    onClick={() => setSubTab('SONGS')}
                    className="px-2.5 py-1 rounded-lg text-xs font-bold border flex items-center gap-1"
                    style={{
                      backgroundColor:
                        subTab === 'SONGS'
                          ? `${themeColors.primary}22`
                          : themeColors.surface,
                      borderColor:
                        subTab === 'SONGS' ? themeColors.primary : themeColors.border,
                      color:
                        subTab === 'SONGS' ? themeColors.primary : themeColors.textSecondary,
                    }}
                  >
                    <Music2 className="w-3.5 h-3.5" />
                    {isArabic
                      ? `الأغاني (${sectionSongs.length})`
                      : `Songs (${sectionSongs.length})`}
                  </button>
                )}
                {sectionSublinks.length > 0 && (
                  <button
                    onClick={() => setSubTab('ARTISTS')}
                    className="px-2.5 py-1 rounded-lg text-xs font-bold border flex items-center gap-1"
                    style={{
                      backgroundColor:
                        subTab !== 'SONGS'
                          ? `${themeColors.primary}22`
                          : themeColors.surface,
                      borderColor:
                        subTab !== 'SONGS' ? themeColors.primary : themeColors.border,
                      color:
                        subTab !== 'SONGS' ? themeColors.primary : themeColors.textSecondary,
                    }}
                  >
                    <FolderOpen className="w-3.5 h-3.5" />
                    {isArabic
                      ? `الأقسام والألبومات (${sectionSublinks.length})`
                      : `Items (${sectionSublinks.length})`}
                  </button>
                )}
              </>
            ) : (
              <>
                <button
                  onClick={() => setSubTab('SONGS')}
                  className="px-2.5 py-1 rounded-lg text-xs font-bold border flex items-center gap-1"
                  style={{
                    backgroundColor:
                      subTab === 'SONGS'
                        ? `${themeColors.primary}22`
                        : themeColors.surface,
                    borderColor:
                      subTab === 'SONGS' ? themeColors.primary : themeColors.border,
                    color:
                      subTab === 'SONGS' ? themeColors.primary : themeColors.textSecondary,
                  }}
                >
                  <Music2 className="w-3.5 h-3.5" />
                  {isArabic
                    ? `الأغاني (${activeCatalog.songs.length})`
                    : `Songs (${activeCatalog.songs.length})`}
                </button>
                <button
                  onClick={() => setSubTab('ARTISTS')}
                  className="px-2.5 py-1 rounded-lg text-xs font-bold border flex items-center gap-1"
                  style={{
                    backgroundColor:
                      subTab === 'ARTISTS'
                        ? `${themeColors.primary}22`
                        : themeColors.surface,
                    borderColor:
                      subTab === 'ARTISTS' ? themeColors.primary : themeColors.border,
                    color:
                      subTab === 'ARTISTS'
                        ? themeColors.primary
                        : themeColors.textSecondary,
                  }}
                >
                  <Users className="w-3.5 h-3.5" />
                  {isArabic
                    ? `الفنانون (${activeCatalog.artists.length})`
                    : `Artists (${activeCatalog.artists.length})`}
                </button>
                <button
                  onClick={() => setSubTab('ALBUMS')}
                  className="px-2.5 py-1 rounded-lg text-xs font-bold border flex items-center gap-1"
                  style={{
                    backgroundColor:
                      subTab === 'ALBUMS'
                        ? `${themeColors.primary}22`
                        : themeColors.surface,
                    borderColor:
                      subTab === 'ALBUMS' ? themeColors.primary : themeColors.border,
                    color:
                      subTab === 'ALBUMS' ? themeColors.primary : themeColors.textSecondary,
                  }}
                >
                  <Disc className="w-3.5 h-3.5" />
                  {isArabic
                    ? `الألبومات (${activeCatalog.albums.length})`
                    : `Albums (${activeCatalog.albums.length})`}
                </button>
                <button
                  onClick={() => setSubTab('CATEGORIES')}
                  className="px-2.5 py-1 rounded-lg text-xs font-bold border flex items-center gap-1"
                  style={{
                    backgroundColor:
                      subTab === 'CATEGORIES'
                        ? `${themeColors.primary}22`
                        : themeColors.surface,
                    borderColor:
                      subTab === 'CATEGORIES' ? themeColors.primary : themeColors.border,
                    color:
                      subTab === 'CATEGORIES'
                        ? themeColors.primary
                        : themeColors.textSecondary,
                  }}
                >
                  <FolderOpen className="w-3.5 h-3.5" />
                  {isArabic
                    ? `التصنيفات (${activeCatalog.categories.length})`
                    : `Categories (${activeCatalog.categories.length})`}
                </button>
              </>
            )}
          </div>

          {searchResults && (
            <button
              onClick={() => {
                setSearchResults(null);
                setQuery('');
              }}
              className="px-2 py-1 rounded-lg border text-[11px] font-semibold flex items-center gap-1"
              style={{
                backgroundColor: themeColors.surfaceVariant,
                borderColor: themeColors.border,
              }}
            >
              <X className="w-3 h-3" />
              {isArabic ? 'مسح البحث' : 'Clear Search'}
            </button>
          )}
        </div>
      )}

      {/* Status / Error Banner */}
      {(message || errorMessage) && (
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
          <span className="truncate">{errorMessage || message}</span>
          <button
            onClick={() => {
              setMessage(null);
              setErrorMessage(null);
            }}
            className="text-[11px] opacity-70 hover:opacity-100 ml-2"
          >
            ✕
          </button>
        </div>
      )}

      {/* Adaptive Grid Area (No Vertical or Horizontal Scroll) */}
      <div ref={gridRef} className="flex-1 min-h-0 overflow-hidden flex flex-col">
        {loading ? (
          <div className="flex-1 flex items-center justify-center gap-2 text-xs opacity-70">
            <RefreshCw className="w-4 h-4 animate-spin" style={{ color: themeColors.primary }} />
            <span>{isArabic ? 'جاري التحميل...' : 'Loading...'}</span>
          </div>
        ) : pagedItems.length === 0 ? (
          <div className="flex-1 flex items-center justify-center text-xs opacity-60">
            {isArabic ? 'لا توجد عناصر متاحة حالياً' : 'No items available'}
          </div>
        ) : (
          <div
            className="grid gap-2 content-start flex-1 min-h-0 overflow-hidden"
            style={{
              gridTemplateColumns: `repeat(${gridCols}, minmax(0, 1fr))`,
            }}
          >
            {pagedItems.map((entry, idx) => {
              if (entry.kind === 'AUDIUS') {
                const track = entry.track;
                const audioItem = onlineMusicService.convertAudiusToAudioItem(track);
                return (
                  <div
                    key={`${track.id}_${idx}`}
                    className="rounded-xl border p-2 flex items-center gap-2.5 h-[62px]"
                    style={{
                      backgroundColor: themeColors.surface,
                      borderColor: themeColors.border,
                    }}
                  >
                    <div
                      className="w-10 h-10 rounded-lg flex items-center justify-center shrink-0 overflow-hidden border"
                      style={{
                        backgroundColor: themeColors.surfaceVariant,
                        borderColor: themeColors.border,
                      }}
                    >
                      {track.artworkUrl ? (
                        <img
                          src={track.artworkUrl}
                          alt={track.title}
                          className="w-full h-full object-cover"
                        />
                      ) : (
                        <Music2 className="w-4 h-4" style={{ color: themeColors.primary }} />
                      )}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="font-bold text-xs truncate">{track.title}</div>
                      <div className="text-[10px] opacity-60 truncate">
                        {track.artist} • {formatMs(track.duration)}
                      </div>
                    </div>
                    <div className="flex items-center gap-1 shrink-0">
                      <button
                        onClick={() => onPlayOnlineTrack(audioItem)}
                        className="p-1.5 rounded-lg text-white"
                        style={{ backgroundColor: themeColors.primary }}
                        title={isArabic ? 'تشغيل' : 'Play'}
                      >
                        <Play className="w-3 h-3 fill-current" />
                      </button>
                      <button
                        onClick={() => {
                          onEnqueueTrack(audioItem);
                          setMessage(
                            isArabic
                              ? `تمت إضافة ${track.title} إلى قائمة الانتظار`
                              : `Added ${track.title} to Queue`
                          );
                        }}
                        className="p-1.5 rounded-lg border"
                        style={{
                          backgroundColor: themeColors.surfaceVariant,
                          borderColor: themeColors.border,
                        }}
                        title="Queue"
                      >
                        <ListMusic className="w-3 h-3" />
                      </button>
                      <button
                        onClick={() => {
                          setPendingPlaylistTrack(audioItem);
                          setShowPlaylistPicker(true);
                        }}
                        className="p-1.5 rounded-lg border"
                        style={{
                          backgroundColor: themeColors.surfaceVariant,
                          borderColor: themeColors.border,
                        }}
                        title="Playlist"
                      >
                        <ListPlus className="w-3 h-3" />
                      </button>
                      <button
                        onClick={() => downloadAudius(track)}
                        className="p-1.5 rounded-lg border"
                        style={{
                          backgroundColor: themeColors.surfaceVariant,
                          borderColor: themeColors.border,
                        }}
                        title={isArabic ? 'تنزيل' : 'Download'}
                      >
                        <Download className="w-3 h-3" />
                      </button>
                      <button
                        onClick={() => onSendToDeckA(audioItem)}
                        className="px-1.5 py-1 rounded text-[10px] font-bold border"
                        style={{
                          borderColor: themeColors.accentA,
                          color: themeColors.accentA,
                        }}
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
                      >
                        B
                      </button>
                    </div>
                  </div>
                );
              }

              if (entry.kind === 'SECTION') {
                const link = entry.link;
                return (
                  <button
                    key={`${link.url}_${idx}`}
                    onClick={() => openSection(link)}
                    className="rounded-xl border p-2.5 flex items-center gap-2.5 text-left transition-all hover:scale-[1.01] h-[62px]"
                    style={{
                      backgroundColor: themeColors.surface,
                      borderColor: themeColors.border,
                    }}
                  >
                    <div
                      className="w-9 h-9 rounded-lg flex items-center justify-center shrink-0"
                      style={{
                        backgroundColor: `${themeColors.primary}20`,
                        color: themeColors.primary,
                      }}
                    >
                      <Disc className="w-4 h-4" />
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="font-bold text-xs truncate">{link.title}</div>
                    </div>
                  </button>
                );
              }

              const link = entry.link;
              return (
                <div
                  key={`${link.url}_${idx}`}
                  className="rounded-xl border p-2 flex items-center gap-2 h-[62px]"
                  style={{
                    backgroundColor: themeColors.surface,
                    borderColor: themeColors.border,
                  }}
                >
                  <div
                    onClick={() => playCatalogLink(link)}
                    className="w-9 h-9 rounded-lg flex items-center justify-center shrink-0 cursor-pointer"
                    style={{
                      backgroundColor: `${themeColors.primary}20`,
                      color: themeColors.primary,
                    }}
                  >
                    <Music2 className="w-4 h-4" />
                  </div>
                  <div
                    onClick={() => playCatalogLink(link)}
                    className="min-w-0 flex-1 cursor-pointer"
                  >
                    <div className="font-bold text-xs truncate" title={link.title}>
                      {link.title}
                    </div>
                  </div>
                  <div className="flex items-center gap-1 shrink-0">
                    <button
                      onClick={() => playCatalogLink(link)}
                      className="p-1.5 rounded-lg text-white"
                      style={{ backgroundColor: themeColors.primary }}
                      title={isArabic ? 'تشغيل' : 'Play'}
                    >
                      <Play className="w-3 h-3 fill-current" />
                    </button>
                    <button
                      onClick={() => enqueueCatalogLink(link)}
                      className="p-1.5 rounded-lg border"
                      style={{
                        backgroundColor: themeColors.surfaceVariant,
                        borderColor: themeColors.border,
                      }}
                      title="Queue"
                    >
                      <ListMusic className="w-3 h-3" />
                    </button>
                    <button
                      onClick={() => openPlaylistModalForCatalogLink(link)}
                      className="p-1.5 rounded-lg border"
                      style={{
                        backgroundColor: themeColors.surfaceVariant,
                        borderColor: themeColors.border,
                      }}
                      title="Playlist"
                    >
                      <ListPlus className="w-3 h-3" />
                    </button>
                    <button
                      onClick={() => downloadCatalogLink(link)}
                      className="p-1.5 rounded-lg border"
                      style={{
                        backgroundColor: themeColors.surfaceVariant,
                        borderColor: themeColors.border,
                      }}
                      title={isArabic ? 'تنزيل' : 'Download'}
                    >
                      <Download className="w-3 h-3" />
                    </button>
                    <button
                      onClick={() => sendCatalogLinkToDeck(link, 'A')}
                      className="px-1.5 py-1 rounded text-[10px] font-bold border"
                      style={{
                        borderColor: themeColors.accentA,
                        color: themeColors.accentA,
                      }}
                    >
                      A
                    </button>
                    <button
                      onClick={() => sendCatalogLinkToDeck(link, 'B')}
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
            ? `العناصر: ${currentItems.length} • صفحة ${safePage} من ${totalPages}`
            : `Items: ${currentItems.length} • Page ${safePage} of ${totalPages}`}
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

      {/* Playlist Picker Modal */}
      {showPlaylistPicker && pendingPlaylistTrack && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
          <div
            className="w-full max-w-md rounded-2xl border p-4 space-y-3"
            style={{
              backgroundColor: themeColors.surface,
              borderColor: themeColors.border,
              color: themeColors.textPrimary,
            }}
          >
            <div className="flex items-center justify-between">
              <div className="font-bold text-sm">
                {isArabic ? 'إضافة إلى قائمة تشغيل' : 'Add to Playlist'}
              </div>
              <button
                onClick={() => {
                  setShowPlaylistPicker(false);
                  setPendingPlaylistTrack(null);
                }}
                className="p-1 rounded-lg"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="text-xs opacity-70 truncate">{pendingPlaylistTrack.title}</div>
            <div className="flex gap-2">
              <input
                type="text"
                value={newPlaylistName}
                onChange={(e) => setNewPlaylistName(e.target.value)}
                placeholder={isArabic ? 'اسم قائمة جديدة...' : 'New playlist name...'}
                className="flex-1 px-2.5 py-1.5 rounded-lg border text-xs outline-none"
                style={{
                  backgroundColor: themeColors.surfaceVariant,
                  borderColor: themeColors.border,
                  color: themeColors.textPrimary,
                }}
              />
              <button
                onClick={handleCreateAndAdd}
                disabled={playlistBusy || !newPlaylistName.trim()}
                className="px-3 py-1.5 rounded-lg text-xs font-bold text-white flex items-center gap-1 disabled:opacity-40"
                style={{ backgroundColor: themeColors.primary }}
              >
                <Plus className="w-3.5 h-3.5" />
                {isArabic ? 'إنشاء' : 'Create'}
              </button>
            </div>
            <div className="max-h-48 overflow-y-auto space-y-1.5">
              {playlists.map((pl) => (
                <button
                  key={pl.id}
                  onClick={() => handleSelectPlaylist(pl.id)}
                  disabled={playlistBusy}
                  className="w-full px-3 py-2 rounded-lg border text-left flex items-center justify-between text-xs font-semibold"
                  style={{
                    backgroundColor: themeColors.surfaceVariant,
                    borderColor: themeColors.border,
                  }}
                >
                  <span className="truncate">{pl.name}</span>
                  <span className="opacity-60">{pl.songCount}</span>
                </button>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
