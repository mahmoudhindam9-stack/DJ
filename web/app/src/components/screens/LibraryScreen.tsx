import React, { useState, useMemo, useRef, useEffect } from 'react';
import {
  Search,
  Plus,
  FolderOpen,
  Music,
  Heart,
  Play,
  ListPlus,
  Disc,
  Trash2,
  User,
  Disc3,
  FolderPlus,
  FileAudio,
  Globe,
  Loader2,
  X,
  Download,
  ChevronLeft,
  ChevronRight,
  ArrowLeft,
} from 'lucide-react';
import { AudioItem, Playlist, LibrarySubTab, ThemeColors } from '../../types';
import {
  downloadOnlineItem,
  downloadOnlineItemAsync,
  getOnlineDownloadEndpoint,
} from '../../services/OnlineMusicService';
import {
  AUDIO_INPUT_ACCEPT,
  extractFilesFromDataTransfer,
  pickLocalAudioFolder,
} from '../../utils/fileImporter';

interface LibraryScreenProps {
  library: AudioItem[];
  playlists: Playlist[];
  currentSong: AudioItem | null;
  isPlaying: boolean;
  themeColors: ThemeColors;
  isArabic: boolean;
  onPlaySong: (song: AudioItem, queue?: AudioItem[]) => void;
  onToggleFavorite: (song: AudioItem) => void;
  onDeleteSong: (id: string) => void;
  onClearLibrary?: () => void;
  onImportFiles: (files: FileList | File[]) => Promise<number>;
  onNavigateToOnline?: () => void;
  onCreatePlaylist: (name: string) => void;
  onDeletePlaylist: (id: string) => void;
  onAddSongToPlaylist: (playlistId: string, song: AudioItem) => void;
  onLoadPlaylistSongs: (playlistId: string) => Promise<AudioItem[]>;
  onEnqueueSong: (song: AudioItem) => void;
  onSendToDeckA: (song: AudioItem) => void;
  onSendToDeckB: (song: AudioItem) => void;
}

export const LibraryScreen: React.FC<LibraryScreenProps> = ({
  library,
  playlists,
  currentSong,
  isPlaying,
  themeColors,
  isArabic,
  onPlaySong,
  onToggleFavorite,
  onDeleteSong,
  onClearLibrary,
  onImportFiles,
  onNavigateToOnline,
  onCreatePlaylist,
  onDeletePlaylist,
  onAddSongToPlaylist,
  onLoadPlaylistSongs,
  onEnqueueSong,
  onSendToDeckA,
  onSendToDeckB,
}) => {
  const [subTab, setSubTab] = useState<LibrarySubTab>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedPlaylist, setSelectedPlaylist] = useState<Playlist | null>(null);
  const [playlistSongs, setPlaylistSongs] = useState<AudioItem[]>([]);
  const [selectedGroupKey, setSelectedGroupKey] = useState<string | null>(null);
  const [showNewPlaylistModal, setShowNewPlaylistModal] = useState(false);
  const [newPlaylistName, setNewPlaylistName] = useState('');
  const [addToPlaylistModalSong, setAddToPlaylistModalSong] = useState<AudioItem | null>(null);
  const [isImporting, setIsImporting] = useState(false);
  const [importFeedback, setImportFeedback] = useState<string | null>(null);
  const [isDraggingOver, setIsDraggingOver] = useState(false);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const folderInputRef = useRef<HTMLInputElement>(null);
  const gridContainerRef = useRef<HTMLDivElement>(null);

  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(10);
  const [gridCols, setGridCols] = useState(2);

  useEffect(() => {
    if (folderInputRef.current) {
      folderInputRef.current.setAttribute('webkitdirectory', '');
      folderInputRef.current.setAttribute('directory', '');
    }
  }, []);

  useEffect(() => {
    const updateMetrics = () => {
      const el = gridContainerRef.current;
      const width = el?.clientWidth || window.innerWidth - 64;
      const height = el?.clientHeight || window.innerHeight - 250;
      const cols = width >= 1320 ? 3 : width >= 780 ? 2 : 1;
      const rowHeight = 64;
      const rows = Math.max(2, Math.floor((height + 8) / rowHeight));
      setGridCols(cols);
      setItemsPerPage(Math.max(cols * 2, cols * rows));
    };
    updateMetrics();
    const obs = new ResizeObserver(updateMetrics);
    if (gridContainerRef.current) obs.observe(gridContainerRef.current);
    window.addEventListener('resize', updateMetrics);
    return () => {
      obs.disconnect();
      window.removeEventListener('resize', updateMetrics);
    };
  }, [subTab, selectedPlaylist, selectedGroupKey]);

  useEffect(() => {
    setCurrentPage(1);
  }, [subTab, searchQuery, selectedPlaylist, selectedGroupKey, itemsPerPage]);

  const handleFilesSelected = async (files: FileList | File[] | null) => {
    if (!files || (Array.isArray(files) ? files.length === 0 : files.length === 0)) return;
    setIsImporting(true);
    setImportFeedback(null);
    try {
      const count = await onImportFiles(files);
      setImportFeedback(
        isArabic ? `تم استيراد ${count} ملف صوتي` : `Imported ${count} audio file(s)`
      );
      setTimeout(() => setImportFeedback(null), 3500);
    } finally {
      setIsImporting(false);
    }
  };

  const handlePickFolder = async () => {
    const files = await pickLocalAudioFolder();
    if (files && files.length > 0) {
      await handleFilesSelected(files);
      return;
    }
    folderInputRef.current?.click();
  };

  const handleOpenPlaylist = async (pl: Playlist) => {
    setSelectedPlaylist(pl);
    const songs = await onLoadPlaylistSongs(pl.id);
    setPlaylistSongs(songs);
  };

  const filteredSongs = useMemo(() => {
    let base: AudioItem[] = library;
    if (subTab === 'FAVORITES') {
      base = library.filter((s) => s.isFavorite);
    } else if (subTab === 'PLAYLISTS' && selectedPlaylist) {
      base = playlistSongs;
    } else if (subTab === 'ARTISTS' && selectedGroupKey) {
      base = library.filter((s) => (s.artist || 'Unknown Artist') === selectedGroupKey);
    } else if (subTab === 'ALBUMS' && selectedGroupKey) {
      base = library.filter((s) => (s.album || 'Unknown Album') === selectedGroupKey);
    } else if (subTab === 'FOLDERS' && selectedGroupKey) {
      base = library.filter((s) => (s.album || 'Local Folder') === selectedGroupKey);
    }

    const q = searchQuery.trim().toLowerCase();
    if (!q) return base;
    return base.filter(
      (s) =>
        s.title.toLowerCase().includes(q) ||
        s.artist.toLowerCase().includes(q) ||
        s.album.toLowerCase().includes(q)
    );
  }, [library, subTab, selectedPlaylist, playlistSongs, selectedGroupKey, searchQuery]);

  const groupedEntries = useMemo(() => {
    const map = new Map<string, AudioItem[]>();
    if (subTab === 'ARTISTS') {
      for (const s of library) {
        const k = s.artist || 'Unknown Artist';
        map.set(k, [...(map.get(k) || []), s]);
      }
    } else if (subTab === 'ALBUMS' || subTab === 'FOLDERS') {
      for (const s of library) {
        const k = s.album || (subTab === 'FOLDERS' ? 'Local Folder' : 'Unknown Album');
        map.set(k, [...(map.get(k) || []), s]);
      }
    }
    const q = searchQuery.trim().toLowerCase();
    return Array.from(map.entries())
      .filter(([name]) => !q || name.toLowerCase().includes(q))
      .map(([name, items]) => ({ name, count: items.length, items }));
  }, [library, subTab, searchQuery]);

  const formatDuration = (ms: number) => {
    if (!ms || ms <= 0) return '--:--';
    const totalSec = Math.floor(ms / 1000);
    const min = Math.floor(totalSec / 60);
    const sec = totalSec % 60;
    return `${min}:${sec < 10 ? '0' : ''}${sec}`;
  };

  const handleDownloadTrack = async (song: AudioItem) => {
    try {
      await downloadOnlineItemAsync(song, (msg) => setImportFeedback(msg));
      setImportFeedback(
        isArabic ? `تم تنزيل "${song.title}"` : `Downloaded "${song.title}"`
      );
    } catch {
      downloadOnlineItem(song);
    }
  };

  const isShowingGroups =
    (subTab === 'ARTISTS' || subTab === 'ALBUMS' || subTab === 'FOLDERS') &&
    !selectedGroupKey;
  const isShowingPlaylistsList = subTab === 'PLAYLISTS' && !selectedPlaylist;

  const activeTotalCount = isShowingPlaylistsList
    ? playlists.length
    : isShowingGroups
    ? groupedEntries.length
    : filteredSongs.length;

  const totalPages = Math.max(1, Math.ceil(activeTotalCount / itemsPerPage));
  const safePage = Math.min(currentPage, totalPages);
  const startIdx = (safePage - 1) * itemsPerPage;

  const pagedSongs = filteredSongs.slice(startIdx, startIdx + itemsPerPage);
  const pagedGroups = groupedEntries.slice(startIdx, startIdx + itemsPerPage);
  const pagedPlaylists = playlists.slice(startIdx, startIdx + itemsPerPage);

  return (
    <div
      className="h-full min-h-0 flex flex-col overflow-hidden p-3 select-none relative"
      style={{ color: themeColors.textPrimary }}
      onDragOver={(e) => {
        e.preventDefault();
        setIsDraggingOver(true);
      }}
      onDragLeave={() => setIsDraggingOver(false)}
      onDrop={async (e) => {
        e.preventDefault();
        setIsDraggingOver(false);
        const extracted = await extractFilesFromDataTransfer(e.dataTransfer);
        if (extracted.length > 0) {
          await handleFilesSelected(extracted);
        }
      }}
    >
      {/* Hidden File Inputs */}
      <input
        ref={fileInputRef}
        type="file"
        multiple
        accept={AUDIO_INPUT_ACCEPT}
        className="hidden"
        onChange={(e) => handleFilesSelected(e.target.files)}
      />
      <input
        ref={folderInputRef}
        type="file"
        multiple
        className="hidden"
        onChange={(e) => handleFilesSelected(e.target.files)}
      />

      {/* Top Header & Actions Bar (No Subtitles) */}
      <div
        className="rounded-xl p-2.5 border mb-2 shrink-0 flex flex-wrap items-center justify-between gap-2"
        style={{
          backgroundColor: themeColors.surface,
          borderColor: themeColors.border,
        }}
      >
        <div className="flex items-center gap-2 min-w-0">
          {(selectedPlaylist || selectedGroupKey) && (
            <button
              onClick={() => {
                setSelectedPlaylist(null);
                setSelectedGroupKey(null);
              }}
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
            <Music className="w-4 h-4" style={{ color: themeColors.primary }} />
          </div>
          <h1 className="text-sm font-bold tracking-tight truncate">
            {selectedPlaylist
              ? selectedPlaylist.name
              : selectedGroupKey
              ? selectedGroupKey
              : isArabic
              ? 'مكتبة الموسيقى'
              : 'Music Library'}
          </h1>
        </div>

        {/* Sub-Tabs */}
        <div className="flex items-center gap-1 flex-wrap">
          {(
            [
              { id: 'ALL', label: isArabic ? 'الكل' : 'All', icon: Music },
              { id: 'PLAYLISTS', label: isArabic ? 'القوائم' : 'Playlists', icon: ListPlus },
              { id: 'FAVORITES', label: isArabic ? 'المفضلة' : 'Favorites', icon: Heart },
              { id: 'ARTISTS', label: isArabic ? 'الفنانون' : 'Artists', icon: User },
              { id: 'ALBUMS', label: isArabic ? 'الألبومات' : 'Albums', icon: Disc3 },
              { id: 'FOLDERS', label: isArabic ? 'المجلدات' : 'Folders', icon: FolderOpen },
            ] as { id: LibrarySubTab; label: string; icon: React.ElementType }[]
          ).map((tab) => {
            const Icon = tab.icon;
            const active = subTab === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => {
                  setSubTab(tab.id);
                  setSelectedPlaylist(null);
                  setSelectedGroupKey(null);
                }}
                className="px-2.5 py-1 rounded-lg text-xs font-bold flex items-center gap-1 border transition-all"
                style={{
                  backgroundColor: active ? themeColors.primary : themeColors.surfaceVariant,
                  borderColor: active ? themeColors.primary : themeColors.border,
                  color: active ? '#fff' : themeColors.textSecondary,
                }}
              >
                <Icon className="w-3.5 h-3.5" />
                {tab.label}
              </button>
            );
          })}
        </div>

        {/* Search & Import Controls */}
        <div className="flex items-center gap-1.5 flex-1 justify-end min-w-[240px]">
          <div className="relative w-44 sm:w-56">
            <Search className="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 opacity-50" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder={isArabic ? 'بحث في المكتبة...' : 'Search library...'}
              className="w-full pl-8 pr-2.5 py-1.5 rounded-lg border text-xs outline-none"
              style={{
                backgroundColor: themeColors.background,
                borderColor: themeColors.border,
                color: themeColors.textPrimary,
              }}
            />
          </div>

          <button
            onClick={() => fileInputRef.current?.click()}
            disabled={isImporting}
            className="px-2.5 py-1.5 rounded-lg text-xs font-bold text-white flex items-center gap-1 shrink-0"
            style={{ backgroundColor: themeColors.primary }}
          >
            {isImporting ? (
              <Loader2 className="w-3.5 h-3.5 animate-spin" />
            ) : (
              <FileAudio className="w-3.5 h-3.5" />
            )}
            {isArabic ? 'ملفات' : 'Files'}
          </button>

          <button
            onClick={handlePickFolder}
            disabled={isImporting}
            className="px-2.5 py-1.5 rounded-lg border text-xs font-bold flex items-center gap-1 shrink-0"
            style={{
              backgroundColor: themeColors.surfaceVariant,
              borderColor: themeColors.border,
            }}
          >
            <FolderPlus className="w-3.5 h-3.5" />
            {isArabic ? 'مجلد' : 'Folder'}
          </button>

          <button
            onClick={() => setShowNewPlaylistModal(true)}
            className="px-2.5 py-1.5 rounded-lg border text-xs font-bold flex items-center gap-1 shrink-0"
            style={{
              backgroundColor: themeColors.surfaceVariant,
              borderColor: themeColors.border,
            }}
          >
            <Plus className="w-3.5 h-3.5" />
            {isArabic ? 'قائمة' : 'Playlist'}
          </button>

          {onClearLibrary && library.length > 0 && (
            <button
              onClick={onClearLibrary}
              className="p-1.5 rounded-lg border text-red-400 shrink-0"
              style={{
                backgroundColor: themeColors.surfaceVariant,
                borderColor: themeColors.border,
              }}
              title={isArabic ? 'مسح المكتبة' : 'Clear Library'}
            >
              <Trash2 className="w-3.5 h-3.5" />
            </button>
          )}
        </div>
      </div>

      {/* Feedback Banner */}
      {importFeedback && (
        <div
          className="mb-2 px-3 py-1 rounded-lg border text-xs flex items-center justify-between shrink-0"
          style={{
            backgroundColor: `${themeColors.primary}15`,
            borderColor: `${themeColors.primary}40`,
          }}
        >
          <span>{importFeedback}</span>
          <button onClick={() => setImportFeedback(null)} className="text-xs opacity-70">
            ✕
          </button>
        </div>
      )}

      {/* Main Content Area (No Scroll) */}
      <div
        ref={gridContainerRef}
        className="flex-1 min-h-0 overflow-hidden flex flex-col"
      >
        {isShowingPlaylistsList ? (
          pagedPlaylists.length === 0 ? (
            <div className="flex-1 flex flex-col items-center justify-center gap-2 text-xs opacity-60">
              <ListPlus className="w-8 h-8 opacity-40" />
              <span>{isArabic ? 'لا توجد قوائم تشغيل' : 'No playlists yet'}</span>
            </div>
          ) : (
            <div
              className="grid gap-2.5 content-start flex-1 min-h-0 overflow-hidden"
              style={{
                gridTemplateColumns: `repeat(${gridCols}, minmax(0, 1fr))`,
              }}
            >
              {pagedPlaylists.map((pl) => (
                <div
                  key={pl.id}
                  onClick={() => handleOpenPlaylist(pl)}
                  className="p-2.5 rounded-xl border flex items-center justify-between gap-2 cursor-pointer h-[58px]"
                  style={{
                    backgroundColor: themeColors.surface,
                    borderColor: themeColors.border,
                  }}
                >
                  <div className="flex items-center gap-2.5 min-w-0">
                    <div
                      className="w-9 h-9 rounded-lg flex items-center justify-center shrink-0"
                      style={{
                        backgroundColor: `${themeColors.primary}20`,
                        color: themeColors.primary,
                      }}
                    >
                      <ListPlus className="w-4 h-4" />
                    </div>
                    <div className="min-w-0">
                      <div className="font-bold text-xs truncate">{pl.name}</div>
                      <div className="text-[10px] opacity-60">
                        {pl.songCount} {isArabic ? 'أغنية' : 'tracks'}
                      </div>
                    </div>
                  </div>
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      onDeletePlaylist(pl.id);
                    }}
                    className="p-1.5 rounded-lg text-red-400 hover:bg-red-500/10"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              ))}
            </div>
          )
        ) : isShowingGroups ? (
          pagedGroups.length === 0 ? (
            <div className="flex-1 flex items-center justify-center text-xs opacity-60">
              {isArabic ? 'لا توجد عناصر' : 'No items found'}
            </div>
          ) : (
            <div
              className="grid gap-2.5 content-start flex-1 min-h-0 overflow-hidden"
              style={{
                gridTemplateColumns: `repeat(${gridCols}, minmax(0, 1fr))`,
              }}
            >
              {pagedGroups.map((group) => (
                <button
                  key={group.name}
                  onClick={() => setSelectedGroupKey(group.name)}
                  className="p-2.5 rounded-xl border flex items-center justify-between gap-2 text-left h-[58px]"
                  style={{
                    backgroundColor: themeColors.surface,
                    borderColor: themeColors.border,
                  }}
                >
                  <div className="flex items-center gap-2.5 min-w-0">
                    <div
                      className="w-9 h-9 rounded-lg flex items-center justify-center shrink-0"
                      style={{
                        backgroundColor: `${themeColors.primary}20`,
                        color: themeColors.primary,
                      }}
                    >
                      {subTab === 'ARTISTS' ? (
                        <User className="w-4 h-4" />
                      ) : subTab === 'ALBUMS' ? (
                        <Disc3 className="w-4 h-4" />
                      ) : (
                        <FolderOpen className="w-4 h-4" />
                      )}
                    </div>
                    <div className="min-w-0">
                      <div className="font-bold text-xs truncate">{group.name}</div>
                      <div className="text-[10px] opacity-60">
                        {group.count} {isArabic ? 'أغنية' : 'tracks'}
                      </div>
                    </div>
                  </div>
                </button>
              ))}
            </div>
          )
        ) : pagedSongs.length === 0 ? (
          <div className="flex-1 flex flex-col items-center justify-center gap-2 text-xs opacity-70">
            <Music className="w-8 h-8 opacity-40" />
            <span>{isArabic ? 'المكتبة فارغة' : 'No tracks in this view'}</span>
            {onNavigateToOnline && (
              <button
                onClick={onNavigateToOnline}
                className="px-3 py-1.5 rounded-lg text-xs font-bold text-white flex items-center gap-1.5 mt-1"
                style={{ backgroundColor: themeColors.primary }}
              >
                <Globe className="w-3.5 h-3.5" />
                {isArabic ? 'تصفح الأونلاين' : 'Browse Online Music'}
              </button>
            )}
          </div>
        ) : (
          <div
            className="grid gap-2 content-start flex-1 min-h-0 overflow-hidden"
            style={{
              gridTemplateColumns: `repeat(${gridCols}, minmax(0, 1fr))`,
            }}
          >
            {pagedSongs.map((song) => {
              const isCurrent = currentSong?.id === song.id;
              const canDownload = Boolean(getOnlineDownloadEndpoint(song));
              return (
                <div
                  key={song.id}
                  className="p-2 rounded-xl border flex items-center justify-between gap-2 h-[58px]"
                  style={{
                    backgroundColor: isCurrent
                      ? `${themeColors.primary}18`
                      : themeColors.surface,
                    borderColor: isCurrent ? themeColors.primary : themeColors.border,
                  }}
                >
                  <div
                    onClick={() => onPlaySong(song, filteredSongs)}
                    className="flex items-center gap-2 min-w-0 flex-1 cursor-pointer"
                  >
                    <div
                      className="w-9 h-9 rounded-lg flex items-center justify-center shrink-0 overflow-hidden"
                      style={{
                        backgroundColor: isCurrent
                          ? themeColors.primary
                          : themeColors.surfaceVariant,
                        color: isCurrent ? '#fff' : themeColors.textPrimary,
                      }}
                    >
                      {song.coverUri ? (
                        <img
                          src={song.coverUri}
                          alt={song.title}
                          className="w-full h-full object-cover"
                        />
                      ) : (
                        <Disc className={`w-4 h-4 ${isCurrent && isPlaying ? 'animate-spin' : ''}`} />
                      )}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="font-bold text-xs truncate">{song.title}</div>
                      <div className="text-[10px] opacity-60 truncate">
                        {song.artist} • {formatDuration(song.duration)}
                      </div>
                    </div>
                  </div>

                  {/* Track Controls */}
                  <div className="flex items-center gap-1 shrink-0">
                    <button
                      onClick={() => onPlaySong(song, filteredSongs)}
                      className="p-1.5 rounded-lg text-white"
                      style={{ backgroundColor: themeColors.primary }}
                      title={isArabic ? 'تشغيل' : 'Play'}
                    >
                      <Play className="w-3 h-3 fill-current" />
                    </button>
                    <button
                      onClick={() => onToggleFavorite(song)}
                      className="p-1.5 rounded-lg border"
                      style={{
                        backgroundColor: themeColors.surfaceVariant,
                        borderColor: themeColors.border,
                        color: song.isFavorite ? '#ef4444' : themeColors.textPrimary,
                      }}
                      title={isArabic ? 'المفضلة' : 'Favorite'}
                    >
                      <Heart className={`w-3 h-3 ${song.isFavorite ? 'fill-current' : ''}`} />
                    </button>
                    <button
                      onClick={() => setAddToPlaylistModalSong(song)}
                      className="p-1.5 rounded-lg border"
                      style={{
                        backgroundColor: themeColors.surfaceVariant,
                        borderColor: themeColors.border,
                      }}
                      title={isArabic ? 'إضافة لقائمة' : 'Add to Playlist'}
                    >
                      <ListPlus className="w-3 h-3" />
                    </button>
                    {canDownload && (
                      <button
                        onClick={() => handleDownloadTrack(song)}
                        className="p-1.5 rounded-lg border"
                        style={{
                          backgroundColor: themeColors.surfaceVariant,
                          borderColor: themeColors.border,
                        }}
                        title={isArabic ? 'تنزيل' : 'Download'}
                      >
                        <Download className="w-3 h-3" />
                      </button>
                    )}
                    <button
                      onClick={() => onSendToDeckA(song)}
                      className="px-1.5 py-1 rounded text-[10px] font-bold border"
                      style={{
                        borderColor: themeColors.accentA,
                        color: themeColors.accentA,
                      }}
                    >
                      A
                    </button>
                    <button
                      onClick={() => onSendToDeckB(song)}
                      className="px-1.5 py-1 rounded text-[10px] font-bold border"
                      style={{
                        borderColor: themeColors.accentB,
                        color: themeColors.accentB,
                      }}
                    >
                      B
                    </button>
                    <button
                      onClick={() => onDeleteSong(song.id)}
                      className="p-1.5 rounded-lg text-red-400 hover:bg-red-500/10"
                      title={isArabic ? 'حذف' : 'Delete'}
                    >
                      <Trash2 className="w-3 h-3" />
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Pagination Footer */}
      <div
        className="mt-2 pt-2 border-t flex items-center justify-between text-xs shrink-0"
        style={{ borderColor: themeColors.border }}
      >
        <div className="opacity-70 text-[11px]">
          {isArabic
            ? `الإجمالي: ${activeTotalCount} • صفحة ${safePage} من ${totalPages}`
            : `Total: ${activeTotalCount} • Page ${safePage} of ${totalPages}`}
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

      {/* Drag & Drop Overlay */}
      {isDraggingOver && (
        <div
          className="absolute inset-2 rounded-2xl border-2 border-dashed flex items-center justify-center bg-black/60 backdrop-blur-sm z-40"
          style={{ borderColor: themeColors.primary }}
        >
          <div className="text-sm font-bold text-white">
            {isArabic ? 'أفلت الملفات الصوتية هنا' : 'Drop audio files here'}
          </div>
        </div>
      )}

      {/* New Playlist Modal */}
      {showNewPlaylistModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
          <div
            className="w-full max-w-sm rounded-2xl border p-4 space-y-3"
            style={{
              backgroundColor: themeColors.surface,
              borderColor: themeColors.border,
            }}
          >
            <div className="flex items-center justify-between">
              <h3 className="font-bold text-sm">
                {isArabic ? 'قائمة تشغيل جديدة' : 'New Playlist'}
              </h3>
              <button onClick={() => setShowNewPlaylistModal(false)}>
                <X className="w-4 h-4" />
              </button>
            </div>
            <input
              type="text"
              value={newPlaylistName}
              onChange={(e) => setNewPlaylistName(e.target.value)}
              placeholder={isArabic ? 'اسم القائمة...' : 'Playlist name...'}
              className="w-full px-3 py-1.5 rounded-lg border text-xs outline-none"
              style={{
                backgroundColor: themeColors.background,
                borderColor: themeColors.border,
                color: themeColors.textPrimary,
              }}
            />
            <div className="flex justify-end gap-2">
              <button
                onClick={() => {
                  const trimmed = newPlaylistName.trim();
                  if (!trimmed) return;
                  onCreatePlaylist(trimmed);
                  setNewPlaylistName('');
                  setShowNewPlaylistModal(false);
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

      {/* Add to Playlist Modal */}
      {addToPlaylistModalSong && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
          <div
            className="w-full max-w-sm rounded-2xl border p-4 space-y-3"
            style={{
              backgroundColor: themeColors.surface,
              borderColor: themeColors.border,
            }}
          >
            <div className="flex items-center justify-between">
              <h3 className="font-bold text-sm">
                {isArabic ? 'إضافة إلى قائمة تشغيل' : 'Add to Playlist'}
              </h3>
              <button onClick={() => setAddToPlaylistModalSong(null)}>
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="text-xs opacity-70 truncate">{addToPlaylistModalSong.title}</div>
            <div className="max-h-44 overflow-y-auto space-y-1.5">
              {playlists.map((pl) => (
                <button
                  key={pl.id}
                  onClick={() => {
                    onAddSongToPlaylist(pl.id, addToPlaylistModalSong);
                    setAddToPlaylistModalSong(null);
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
          </div>
        </div>
      )}
    </div>
  );
};
