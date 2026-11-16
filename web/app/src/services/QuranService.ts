import { AudioItem, QuranMoshaf, QuranReciter, QuranSurah } from '../types';

const QURAN_API = '/api/quran';
const MP3QURAN_DIRECT_API = 'https://mp3quran.net/api/v3';

const BUILTIN_SUWAR_AR: Array<{ id: number; name: string; makkia: boolean; startPage: number; endPage: number }> = [
  { id: 1, name: 'الفاتحة', makkia: true, startPage: 1, endPage: 1 },
  { id: 2, name: 'البقرة', makkia: false, startPage: 2, endPage: 49 },
  { id: 3, name: 'آل عمران', makkia: false, startPage: 50, endPage: 76 },
  { id: 4, name: 'النساء', makkia: false, startPage: 77, endPage: 106 },
  { id: 5, name: 'المائدة', makkia: false, startPage: 106, endPage: 127 },
  { id: 6, name: 'الأنعام', makkia: true, startPage: 128, endPage: 150 },
  { id: 7, name: 'الأعراف', makkia: true, startPage: 151, endPage: 176 },
  { id: 8, name: 'الأنفال', makkia: false, startPage: 177, endPage: 186 },
  { id: 9, name: 'التوبة', makkia: false, startPage: 187, endPage: 207 },
  { id: 10, name: 'يونس', makkia: true, startPage: 208, endPage: 221 },
  { id: 11, name: 'هود', makkia: true, startPage: 221, endPage: 235 },
  { id: 12, name: 'يوسف', makkia: true, startPage: 235, endPage: 248 },
  { id: 13, name: 'الرعد', makkia: false, startPage: 249, endPage: 255 },
  { id: 14, name: 'إبراهيم', makkia: true, startPage: 255, endPage: 261 },
  { id: 15, name: 'الحجر', makkia: true, startPage: 262, endPage: 267 },
  { id: 16, name: 'النحل', makkia: true, startPage: 267, endPage: 281 },
  { id: 17, name: 'الإسراء', makkia: true, startPage: 282, endPage: 293 },
  { id: 18, name: 'الكهف', makkia: true, startPage: 293, endPage: 304 },
  { id: 19, name: 'مريم', makkia: true, startPage: 305, endPage: 312 },
  { id: 20, name: 'طه', makkia: true, startPage: 312, endPage: 321 },
  { id: 21, name: 'الأنبياء', makkia: true, startPage: 322, endPage: 331 },
  { id: 22, name: 'الحج', makkia: false, startPage: 332, endPage: 341 },
  { id: 23, name: 'المؤمنون', makkia: true, startPage: 342, endPage: 349 },
  { id: 24, name: 'النور', makkia: false, startPage: 350, endPage: 359 },
  { id: 25, name: 'الفرقان', makkia: true, startPage: 359, endPage: 366 },
  { id: 26, name: 'الشعراء', makkia: true, startPage: 367, endPage: 376 },
  { id: 27, name: 'النمل', makkia: true, startPage: 377, endPage: 385 },
  { id: 28, name: 'القصص', makkia: true, startPage: 385, endPage: 396 },
  { id: 29, name: 'العنكبوت', makkia: true, startPage: 396, endPage: 404 },
  { id: 30, name: 'الروم', makkia: true, startPage: 404, endPage: 410 },
  { id: 31, name: 'لقمان', makkia: true, startPage: 411, endPage: 414 },
  { id: 32, name: 'السجدة', makkia: true, startPage: 415, endPage: 417 },
  { id: 33, name: 'الأحزاب', makkia: false, startPage: 418, endPage: 427 },
  { id: 34, name: 'سبأ', makkia: true, startPage: 428, endPage: 434 },
  { id: 35, name: 'فاطر', makkia: true, startPage: 434, endPage: 440 },
  { id: 36, name: 'يس', makkia: true, startPage: 440, endPage: 445 },
  { id: 37, name: 'الصافات', makkia: true, startPage: 446, endPage: 452 },
  { id: 38, name: 'ص', makkia: true, startPage: 453, endPage: 458 },
  { id: 39, name: 'الزمر', makkia: true, startPage: 458, endPage: 467 },
  { id: 40, name: 'غافر', makkia: true, startPage: 467, endPage: 476 },
  { id: 41, name: 'فصلت', makkia: true, startPage: 477, endPage: 482 },
  { id: 42, name: 'الشورى', makkia: true, startPage: 483, endPage: 489 },
  { id: 43, name: 'الزخرف', makkia: true, startPage: 489, endPage: 495 },
  { id: 44, name: 'الدخان', makkia: true, startPage: 496, endPage: 498 },
  { id: 45, name: 'الجاثية', makkia: true, startPage: 499, endPage: 502 },
  { id: 46, name: 'الأحقاف', makkia: true, startPage: 502, endPage: 506 },
  { id: 47, name: 'محمد', makkia: false, startPage: 507, endPage: 510 },
  { id: 48, name: 'الفتح', makkia: false, startPage: 511, endPage: 515 },
  { id: 49, name: 'الحجرات', makkia: false, startPage: 515, endPage: 517 },
  { id: 50, name: 'ق', makkia: true, startPage: 518, endPage: 520 },
  { id: 51, name: 'الذاريات', makkia: true, startPage: 520, endPage: 523 },
  { id: 52, name: 'الطور', makkia: true, startPage: 523, endPage: 525 },
  { id: 53, name: 'النجم', makkia: true, startPage: 526, endPage: 528 },
  { id: 54, name: 'القمر', makkia: true, startPage: 528, endPage: 531 },
  { id: 55, name: 'الرحمن', makkia: false, startPage: 531, endPage: 534 },
  { id: 56, name: 'الواقعة', makkia: true, startPage: 534, endPage: 537 },
  { id: 57, name: 'الحديد', makkia: false, startPage: 537, endPage: 541 },
  { id: 58, name: 'المجادلة', makkia: false, startPage: 542, endPage: 545 },
  { id: 59, name: 'الحشر', makkia: false, startPage: 545, endPage: 548 },
  { id: 60, name: 'الممتحنة', makkia: false, startPage: 549, endPage: 551 },
  { id: 61, name: 'الصف', makkia: false, startPage: 551, endPage: 552 },
  { id: 62, name: 'الجمعة', makkia: false, startPage: 553, endPage: 554 },
  { id: 63, name: 'المنافقون', makkia: false, startPage: 554, endPage: 555 },
  { id: 64, name: 'التغابن', makkia: false, startPage: 556, endPage: 557 },
  { id: 65, name: 'الطلاق', makkia: false, startPage: 558, endPage: 559 },
  { id: 66, name: 'التحريم', makkia: false, startPage: 560, endPage: 561 },
  { id: 67, name: 'الملك', makkia: true, startPage: 562, endPage: 564 },
  { id: 68, name: 'القلم', makkia: true, startPage: 564, endPage: 566 },
  { id: 69, name: 'الحاقة', makkia: true, startPage: 566, endPage: 568 },
  { id: 70, name: 'المعارج', makkia: true, startPage: 568, endPage: 570 },
  { id: 71, name: 'نوح', makkia: true, startPage: 570, endPage: 571 },
  { id: 72, name: 'الجن', makkia: true, startPage: 572, endPage: 573 },
  { id: 73, name: 'المزمل', makkia: true, startPage: 574, endPage: 575 },
  { id: 74, name: 'المدثر', makkia: true, startPage: 575, endPage: 577 },
  { id: 75, name: 'القيامة', makkia: true, startPage: 577, endPage: 578 },
  { id: 76, name: 'الإنسان', makkia: false, startPage: 578, endPage: 580 },
  { id: 77, name: 'المرسلات', makkia: true, startPage: 580, endPage: 581 },
  { id: 78, name: 'النبأ', makkia: true, startPage: 582, endPage: 583 },
  { id: 79, name: 'النازعات', makkia: true, startPage: 583, endPage: 584 },
  { id: 80, name: 'عبس', makkia: true, startPage: 585, endPage: 585 },
  { id: 81, name: 'التكوير', makkia: true, startPage: 586, endPage: 586 },
  { id: 82, name: 'الانفطار', makkia: true, startPage: 587, endPage: 587 },
  { id: 83, name: 'المطففين', makkia: true, startPage: 587, endPage: 589 },
  { id: 84, name: 'الانشقاق', makkia: true, startPage: 589, endPage: 589 },
  { id: 85, name: 'البروج', makkia: true, startPage: 590, endPage: 590 },
  { id: 86, name: 'الطارق', makkia: true, startPage: 591, endPage: 591 },
  { id: 87, name: 'الأعلى', makkia: true, startPage: 591, endPage: 592 },
  { id: 88, name: 'الغاشية', makkia: true, startPage: 592, endPage: 592 },
  { id: 89, name: 'الفجر', makkia: true, startPage: 593, endPage: 594 },
  { id: 90, name: 'البلد', makkia: true, startPage: 594, endPage: 594 },
  { id: 91, name: 'الشمس', makkia: true, startPage: 595, endPage: 595 },
  { id: 92, name: 'الليل', makkia: true, startPage: 595, endPage: 596 },
  { id: 93, name: 'الضحى', makkia: true, startPage: 596, endPage: 596 },
  { id: 94, name: 'الشرح', makkia: true, startPage: 596, endPage: 596 },
  { id: 95, name: 'التين', makkia: true, startPage: 597, endPage: 597 },
  { id: 96, name: 'العلق', makkia: true, startPage: 597, endPage: 597 },
  { id: 97, name: 'القدر', makkia: true, startPage: 598, endPage: 598 },
  { id: 98, name: 'البينة', makkia: false, startPage: 598, endPage: 599 },
  { id: 99, name: 'الزلزلة', makkia: false, startPage: 599, endPage: 599 },
  { id: 100, name: 'العاديات', makkia: true, startPage: 599, endPage: 600 },
  { id: 101, name: 'القارعة', makkia: true, startPage: 600, endPage: 600 },
  { id: 102, name: 'التكاثر', makkia: true, startPage: 600, endPage: 600 },
  { id: 103, name: 'العصر', makkia: true, startPage: 601, endPage: 601 },
  { id: 104, name: 'الهمزة', makkia: true, startPage: 601, endPage: 601 },
  { id: 105, name: 'الفيل', makkia: true, startPage: 601, endPage: 601 },
  { id: 106, name: 'قريش', makkia: true, startPage: 602, endPage: 602 },
  { id: 107, name: 'الماعون', makkia: true, startPage: 602, endPage: 602 },
  { id: 108, name: 'الكوثر', makkia: true, startPage: 602, endPage: 602 },
  { id: 109, name: 'الكافرون', makkia: true, startPage: 603, endPage: 603 },
  { id: 110, name: 'النصر', makkia: false, startPage: 603, endPage: 603 },
  { id: 111, name: 'المسد', makkia: true, startPage: 603, endPage: 603 },
  { id: 112, name: 'الإخلاص', makkia: true, startPage: 604, endPage: 604 },
  { id: 113, name: 'الفلق', makkia: true, startPage: 604, endPage: 604 },
  { id: 114, name: 'الناس', makkia: true, startPage: 604, endPage: 604 },
];

interface RawMoshaf {
  id: number;
  name: string;
  server: string;
  surah_total?: number;
  surahTotal?: number;
  moshaf_type?: number;
  moshafType?: number;
  surah_list?: string | number[];
  surahList?: number[];
}

interface RawReciter {
  id: number;
  name: string;
  letter?: string;
  date?: string;
  moshaf?: RawMoshaf[];
}

interface RawSurah {
  id: number;
  name: string;
  start_page?: number;
  startPage?: number;
  end_page?: number;
  endPage?: number;
  makkia?: number | boolean;
  type?: number;
}

function normalizeMoshaf(raw: RawMoshaf): QuranMoshaf {
  const listRaw = raw.surahList ?? raw.surah_list;
  const surahList = Array.isArray(listRaw)
    ? listRaw.map((n) => Number(n)).filter((n) => Number.isFinite(n) && n >= 1 && n <= 114)
    : String(listRaw || '')
        .split(',')
        .map((s) => parseInt(s.trim(), 10))
        .filter((n) => Number.isFinite(n) && n >= 1 && n <= 114);
  return {
    id: Number(raw.id),
    name: String(raw.name || ''),
    server: String(raw.server || ''),
    surahTotal: Number(raw.surahTotal ?? raw.surah_total ?? surahList.length),
    moshafType: raw.moshafType ?? raw.moshaf_type,
    surahList,
  };
}

function normalizeReciter(raw: RawReciter): QuranReciter {
  return {
    id: Number(raw.id),
    name: String(raw.name || ''),
    letter: raw.letter,
    date: raw.date,
    moshaf: Array.isArray(raw.moshaf) ? raw.moshaf.map(normalizeMoshaf) : [],
  };
}

function normalizeSurah(raw: RawSurah): QuranSurah {
  return {
    id: Number(raw.id),
    name: String(raw.name || '').trim(),
    startPage: raw.startPage ?? raw.start_page,
    endPage: raw.endPage ?? raw.end_page,
    makkia: raw.makkia === true || raw.makkia === 1,
    type: raw.type,
  };
}

class QuranService {
  private recitersCache: Map<string, QuranReciter[]> = new Map();
  private suwarCache: Map<string, QuranSurah[]> = new Map();

  async getReciters(language: 'ar' | 'eng' = 'ar', forceRefresh = false): Promise<QuranReciter[]> {
    const cacheKey = language;
    if (!forceRefresh && this.recitersCache.has(cacheKey)) {
      return this.recitersCache.get(cacheKey)!;
    }

    let rawList: RawReciter[] = [];
    try {
      const response = await fetch(`${QURAN_API}/reciters?language=${language}`);
      if (response.ok) {
        const data = (await response.json()) as { reciters?: RawReciter[] };
        if (Array.isArray(data.reciters)) {
          rawList = data.reciters;
        }
      }
    } catch {
      // Fallback to direct API below
    }

    if (rawList.length === 0) {
      const directRes = await fetch(`${MP3QURAN_DIRECT_API}/reciters?language=${language}`);
      if (!directRes.ok) {
        throw new Error(`Failed to load Quran reciters (HTTP ${directRes.status})`);
      }
      const data = (await directRes.json()) as { reciters?: RawReciter[] };
      rawList = Array.isArray(data.reciters) ? data.reciters : [];
    }

    const reciters = rawList
      .map(normalizeReciter)
      .filter((r) => r.moshaf.length > 0)
      .sort((a, b) => a.name.localeCompare(b.name, language === 'ar' ? 'ar' : 'en'));

    this.recitersCache.set(cacheKey, reciters);
    return reciters;
  }

  async getSuwar(language: 'ar' | 'eng' = 'ar'): Promise<QuranSurah[]> {
    const cacheKey = language;
    if (this.suwarCache.has(cacheKey)) {
      return this.suwarCache.get(cacheKey)!;
    }

    try {
      const response = await fetch(`${QURAN_API}/suwar?language=${language}`);
      if (response.ok) {
        const data = (await response.json()) as { suwar?: RawSurah[] };
        if (Array.isArray(data.suwar) && data.suwar.length > 0) {
          const normalized = data.suwar.map(normalizeSurah);
          this.suwarCache.set(cacheKey, normalized);
          return normalized;
        }
      }
    } catch {
      // Fallback to direct API below
    }

    try {
      const directRes = await fetch(`${MP3QURAN_DIRECT_API}/suwar?language=${language}`);
      if (directRes.ok) {
        const data = (await directRes.json()) as { suwar?: RawSurah[] };
        if (Array.isArray(data.suwar) && data.suwar.length > 0) {
          const normalized = data.suwar.map(normalizeSurah);
          this.suwarCache.set(cacheKey, normalized);
          return normalized;
        }
      }
    } catch {
      // Fallback to builtin
    }

    return BUILTIN_SUWAR_AR;
  }

  getBuiltinSuwar(): QuranSurah[] {
    return BUILTIN_SUWAR_AR;
  }

  buildSurahMp3Url(serverUrl: string, surahId: number): string {
    const base = serverUrl.endsWith('/') ? serverUrl : `${serverUrl}/`;
    const padded = String(surahId).padStart(3, '0');
    return `${base}${padded}.mp3`;
  }

  getStreamEndpoint(mp3Url: string): string {
    return `${QURAN_API}/stream?url=${encodeURIComponent(mp3Url)}`;
  }

  getDownloadEndpoint(mp3Url: string, title: string): string {
    return `${QURAN_API}/download?url=${encodeURIComponent(mp3Url)}&name=${encodeURIComponent(title)}.mp3`;
  }

  convertSurahToAudioItem(
    reciter: QuranReciter,
    moshaf: QuranMoshaf,
    surah: QuranSurah,
    isArabic = true
  ): AudioItem {
    const directMp3Url = this.buildSurahMp3Url(moshaf.server, surah.id);
    const surahPrefix = isArabic ? 'سورة' : 'Surah';
    const cleanSurahName = surah.name.trim();
    const displayTitle = cleanSurahName.startsWith('سورة')
      ? cleanSurahName
      : `${surahPrefix} ${cleanSurahName}`;
    const fullLabel = `${displayTitle} - ${reciter.name} (${moshaf.name})`;

    return {
      id: `quran_${reciter.id}_${moshaf.id}_${surah.id}`,
      title: displayTitle,
      artist: reciter.name,
      album: moshaf.name,
      duration: 0,
      uri: directMp3Url,
      fallbackUris: [this.getStreamEndpoint(directMp3Url)],
      addedDate: Date.now(),
      onlineSource: 'QURAN',
      sourceUrl: directMp3Url,
      downloadUri: this.getDownloadEndpoint(directMp3Url, fullLabel),
    };
  }
}

export const quranService = new QuranService();
