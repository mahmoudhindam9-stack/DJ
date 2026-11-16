import { AudioItem } from '../types';

export const SUPPORTED_AUDIO_EXTENSIONS = [
  '.mp3',
  '.wav',
  '.ogg',
  '.flac',
  '.m4a',
  '.aac',
  '.opus',
  '.weba',
  '.wma',
];

export const AUDIO_INPUT_ACCEPT =
  'audio/*,.mp3,.wav,.ogg,.flac,.m4a,.aac,.opus,.weba,.wma';

/**
 * Checks if a file is an audio file by extension or MIME type
 */
export function isAudioFile(file: File): boolean {
  if (file.type && file.type.startsWith('audio/')) {
    return true;
  }
  const name = file.name.toLowerCase();
  return SUPPORTED_AUDIO_EXTENSIONS.some((ext) => name.endsWith(ext));
}

/**
 * Extract audio duration asynchronously in milliseconds
 */
export function getAudioDurationMs(file: File): Promise<number> {
  return new Promise((resolve) => {
    try {
      const url = URL.createObjectURL(file);
      const audio = new Audio();
      audio.preload = 'metadata';
      let resolved = false;
      let timeoutId: number | undefined;
      const cleanup = () => {
        if (resolved) return;
        resolved = true;
        if (timeoutId !== undefined) window.clearTimeout(timeoutId);
        URL.revokeObjectURL(url);
        audio.removeAttribute('src');
      };

      audio.onloadedmetadata = () => {
        const duration = audio.duration;
        cleanup();
        resolve(Number.isFinite(duration) && duration > 0 ? Math.round(duration * 1000) : 0);
      };

      audio.onerror = () => {
        cleanup();
        resolve(0);
      };

      audio.src = url;
      timeoutId = window.setTimeout(() => {
        cleanup();
        resolve(0);
      }, 3000);
    } catch {
      resolve(0);
    }
  });
}

/**
 * Parse clean title, artist, and album from filename and relative path
 */
export function parseAudioMetadata(file: File): {
  title: string;
  artist: string;
  album: string;
} {
  // Strip extension
  let baseName = file.name.replace(/\.[^/.]+$/, '').trim();

  // Strip leading track numbers like "01. ", "01 - ", "01 ", "(01) ", "[01] "
  baseName = baseName.replace(/^[0-9]+[\s.\-_)]+/, '').trim();

  let artist = 'Local Artist';
  let title = baseName;
  let album = 'Local Audio';

  // Check for "Artist - Title" format
  if (baseName.includes(' - ')) {
    const parts = baseName.split(' - ');
    if (parts.length >= 2) {
      artist = parts[0].trim();
      title = parts.slice(1).join(' - ').trim();
    }
  } else if (baseName.includes(' – ')) {
    // en-dash
    const parts = baseName.split(' – ');
    if (parts.length >= 2) {
      artist = parts[0].trim();
      title = parts.slice(1).join(' – ').trim();
    }
  }

  // Extract album/folder name from relative path if available
  const pathMetadata = file as File & { webkitRelativePath?: string; relativePath?: string };
  const relativePath = pathMetadata.webkitRelativePath || pathMetadata.relativePath;
  if (relativePath && relativePath.includes('/')) {
    const segments = relativePath.split('/');
    if (segments.length >= 2) {
      // The direct parent folder can be the album or artist folder
      album = segments[segments.length - 2];
      if (segments.length >= 3 && artist === 'Local Artist') {
        artist = segments[segments.length - 3];
      }
    }
  }

  return {
    title: title || file.name,
    artist: artist || 'Local Artist',
    album: album || 'Local Audio',
  };
}

/**
 * Parse an individual File into an AudioItem
 */
export async function parseAudioFile(
  file: File,
  index = 0
): Promise<AudioItem> {
  const { title, artist, album } = parseAudioMetadata(file);
  const duration = await getAudioDurationMs(file);
  const uri = URL.createObjectURL(file);

  return {
    id: `local_${Date.now()}_${index}_${Math.random().toString(36).substring(2, 7)}`,
    title,
    artist,
    album,
    duration,
    uri,
    addedDate: Date.now(),
    // Preserve bytes because this Blob URL expires when the application closes.
    audioBlob: file.slice(0, file.size, file.type || 'application/octet-stream'),
  };
}

/**
 * Attach a relative path to browser File objects so folder/album metadata survives import.
 * This is metadata only; the original file bytes are not copied into another allocation.
 */
function attachRelativePath(file: File, relativePath: string): File {
  try {
    Object.defineProperty(file, 'relativePath', {
      configurable: true,
      enumerable: false,
      value: relativePath,
    });
  } catch {
    // File input's webkitRelativePath remains available as a fallback where present.
  }
  return file;
}

/**
 * Recursively extracts files from a dropped directory, including the relative path.
 */
async function readEntriesRecursively(
  entry: FileSystemEntry,
  relativeDirectory = ''
): Promise<File[]> {
  if (entry.isFile) {
    return new Promise((resolve) => {
      (entry as FileSystemFileEntry).file(
        (file) => {
          if (!isAudioFile(file)) {
            resolve([]);
            return;
          }
          const relativePath = relativeDirectory ? relativeDirectory + '/' + file.name : file.name;
          resolve([attachRelativePath(file, relativePath)]);
        },
        () => resolve([])
      );
    });
  }
  if (!entry.isDirectory) return [];

  const dirReader = (entry as FileSystemDirectoryEntry).createReader();
  const files: File[] = [];
  const readBatch = async (): Promise<FileSystemEntry[]> => new Promise((resolve) => {
    dirReader.readEntries((entries) => resolve(entries || []), () => resolve([]));
  });

  let batch = await readBatch();
  while (batch.length > 0) {
    for (const childEntry of batch) {
      const childDirectory = childEntry.isDirectory
        ? (relativeDirectory ? relativeDirectory + '/' : '') + childEntry.name
        : relativeDirectory;
      files.push(...await readEntriesRecursively(childEntry, childDirectory));
    }
    batch = await readBatch();
  }
  return files;
}

interface LocalFileHandle {
  kind: 'file';
  name: string;
  getFile(): Promise<File>;
}

interface LocalDirectoryHandle {
  kind: 'directory';
  name: string;
  values(): AsyncIterable<LocalDirectoryHandle | LocalFileHandle>;
}

type LocalPickerWindow = Window & {
  showDirectoryPicker?: () => Promise<LocalDirectoryHandle>;
};

export function supportsLocalFolderPicker(): boolean {
  return typeof (window as LocalPickerWindow).showDirectoryPicker === 'function';
}

/**
 * Use the native Chromium directory picker when available, then recursively read
 * supported audio files. Returns undefined when the API is unavailable and null
 * when the user cancels; both cases allow the UI to handle fallback/cancellation.
 */
export async function pickLocalAudioFolder(): Promise<File[] | null | undefined> {
  const picker = (window as LocalPickerWindow).showDirectoryPicker;
  if (!picker) return undefined;

  let root: LocalDirectoryHandle;
  try {
    root = await picker.call(window);
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') return null;
    throw error;
  }

  const files: File[] = [];
  const visit = async (directory: LocalDirectoryHandle, relativeDirectory: string): Promise<void> => {
    for await (const entry of directory.values()) {
      if (entry.kind === 'directory') {
        const childPath = relativeDirectory ? relativeDirectory + '/' + entry.name : entry.name;
        await visit(entry, childPath);
      } else {
        try {
          const file = await entry.getFile();
          if (!isAudioFile(file)) continue;
          const relativePath = relativeDirectory ? relativeDirectory + '/' + file.name : file.name;
          files.push(attachRelativePath(file, relativePath));
        } catch (error) {
          console.warn('Skipping an unreadable local audio file:', entry.name, error);
        }
      }
    }
  };

  await visit(root, '');
  return files;
}

/**
 * Extract files from DragEvent's dataTransfer supporting both individual files and dropped folders
 */
export async function extractFilesFromDataTransfer(
  dataTransfer: DataTransfer
): Promise<File[]> {
  const items = dataTransfer.items;
  if (items && items.length > 0) {
    const collectedFiles: File[] = [];
    const promises: Promise<File[]>[] = [];

    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      if (item.kind === 'file') {
        const entry = item.webkitGetAsEntry ? item.webkitGetAsEntry() : null;
        if (entry) {
          promises.push(readEntriesRecursively(entry));
        } else {
          const f = item.getAsFile();
          if (f && isAudioFile(f)) {
            collectedFiles.push(f);
          }
        }
      }
    }

    if (promises.length > 0) {
      const results = await Promise.all(promises);
      for (const res of results) {
        collectedFiles.push(...res);
      }
      return collectedFiles;
    }
  }

  // Fallback to dataTransfer.files
  const files: File[] = [];
  if (dataTransfer.files) {
    for (let i = 0; i < dataTransfer.files.length; i++) {
      const f = dataTransfer.files[i];
      if (isAudioFile(f)) {
        files.push(f);
      }
    }
  }
  return files;
}

/**
 * Batch import files with progress callback
 */
export async function batchImportAudioFiles(
  files: File[] | FileList,
  onProgress?: (processed: number, total: number) => void
): Promise<AudioItem[]> {
  const rawList: File[] = Array.isArray(files) ? files : Array.from(files);
  const audioFiles = rawList.filter(isAudioFile);

  const items = new Array<AudioItem>(audioFiles.length);
  let nextIndex = 0;
  let processed = 0;
  const worker = async (): Promise<void> => {
    while (true) {
      const index = nextIndex;
      nextIndex += 1;
      if (index >= audioFiles.length) return;
      try {
        items[index] = await parseAudioFile(audioFiles[index], index);
      } catch (error) {
        console.warn('Could not import audio file:', audioFiles[index].name, error);
      } finally {
        processed += 1;
        onProgress?.(processed, audioFiles.length);
      }
    }
  };

  // Read metadata in small batches instead of waiting up to 3 seconds per song
  // sequentially for large folders. Keep the results ordered by the original file list.
  const concurrency = Math.min(4, audioFiles.length);
  await Promise.all(Array.from({ length: concurrency }, () => worker()));
  return items.filter((item): item is AudioItem => Boolean(item));
}
