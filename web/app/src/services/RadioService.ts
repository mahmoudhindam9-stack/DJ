import { RadioStation } from '../types';

export const EGYPT_QURAN_STATION: RadioStation = {
  id: 'builtin-eg-quran-cairo',
  name: 'إذاعة القرآن الكريم من القاهرة',
  streamUrls: [
    'https://stream.radiojar.com/8s5u5tpdtwzuv',
    'https://n0e.radiojar.com/8s5u5tpdtwzuv',
    'https://n05.radiojar.com/8s5u5tpdtwzuv',
    `/api/radio/stream?url=${encodeURIComponent('https://stream.radiojar.com/8s5u5tpdtwzuv')}`,
  ],
  tags: 'اسلامي • دين • قرآن • القاهرة',
  codec: 'MP3',
  bitrate: 128,
  countryCode: 'EG',
};

// Verified working Egypt & Arabic stations with tested live streams + failover mirrors
const CURATED_EGYPT_STATIONS: RadioStation[] = [
  EGYPT_QURAN_STATION,
  {
    id: 'builtin-eg-9090-fm',
    name: 'الراديو 9090 — El-Radio 9090 FM',
    streamUrls: [
      `/api/radio/stream?url=${encodeURIComponent('http://9090streaming.mobtada.com/9090FMEGYPT')}`,
      'http://9090streaming.mobtada.com/9090FMEGYPT',
    ],
    tags: 'مصر • منوعات • أغاني • أخبار',
    codec: 'MP3',
    bitrate: 128,
    countryCode: 'EG',
  },
  {
    id: 'builtin-eg-nrj-egypt',
    name: 'NRJ Egypt 92.1 FM',
    streamUrls: [
      `/api/radio/stream?url=${encodeURIComponent('http://nrjstreaming.ahmed-melege.com/nrjegypt')}`,
      'http://nrjstreaming.ahmed-melege.com/nrjegypt',
    ],
    tags: 'Hits • Pop • Arabic • Youth',
    codec: 'MP3',
    bitrate: 128,
    countryCode: 'EG',
  },
  {
    id: 'builtin-eg-nogoum-fm',
    name: 'نجوم إف إم — Nogoum FM 100.6',
    streamUrls: [
      'https://audio.nrpstream.com/listen/nogoumfm/radio.mp3',
      `/api/radio/stream?url=${encodeURIComponent('https://audio.nrpstream.com/listen/nogoumfm/radio.mp3')}`,
    ],
    tags: 'Hits • Pop • Talk • Cairo',
    codec: 'AAC',
    bitrate: 128,
    countryCode: 'EG',
  },
  {
    id: 'builtin-eg-nile-fm',
    name: 'Nile FM 104.2',
    streamUrls: [
      'https://audio.nrpstream.com/listen/nile_fm/radio.mp3',
      `/api/radio/stream?url=${encodeURIComponent('https://audio.nrpstream.com/listen/nile_fm/radio.mp3')}`,
    ],
    tags: 'Top 40 • English Hits • Cairo',
    codec: 'MP3',
    bitrate: 128,
    countryCode: 'EG',
  },
  {
    id: 'builtin-eg-90s-fm',
    name: 'تسعينات إف إم — 90s FM',
    streamUrls: [
      'https://eu1.fastcast4u.com/proxy/princesb?mp=/1',
      `/api/radio/stream?url=${encodeURIComponent('https://eu1.fastcast4u.com/proxy/princesb?mp=/1')}`,
    ],
    tags: 'كلاسيكيات • تسعينات • طرب',
    codec: 'MP3',
    bitrate: 128,
    countryCode: 'EG',
  },
  {
    id: 'builtin-eg-on-sport',
    name: 'On Sport FM 93.7',
    streamUrls: [
      'https://carina.streamerr.co:2020/stream/OnSportFM',
      `/api/radio/stream?url=${encodeURIComponent('https://carina.streamerr.co:2020/stream/OnSportFM')}`,
    ],
    tags: 'رياضة • كرة قدم • مصر',
    codec: 'MP3',
    bitrate: 128,
    countryCode: 'EG',
  },
  {
    id: 'builtin-eg-minshawi',
    name: 'إذاعة الشيخ محمد صديق المنشاوي',
    streamUrls: [
      'https://backup.qurango.net/radio/mohammed_siddiq_alminshawi',
      'https://backup.qurango.net/radio/mohammed_siddiq_alminshawi_mojawwad',
    ],
    tags: 'قرآن كريم • تجويد • ترتيل',
    codec: 'MP3',
    bitrate: 128,
    countryCode: 'EG',
  },
  {
    id: 'builtin-eg-abdulbasit',
    name: 'إذاعة الشيخ عبد الباسط عبد الصمد',
    streamUrls: [
      'https://backup.qurango.net/radio/abdulbasit_abdulsamad_mojawwad',
      'https://backup.qurango.net/radio/abdulbasit_abdulsamad',
    ],
    tags: 'قرآن كريم • تجويد • ترتيل',
    codec: 'MP3',
    bitrate: 128,
    countryCode: 'EG',
  },
  {
    id: 'builtin-eg-hussary',
    name: 'إذاعة الشيخ محمود خليل الحصري',
    streamUrls: [
      'https://backup.qurango.net/radio/mahmoud_khalil_alhussary',
      'https://backup.qurango.net/radio/mahmoud_khalil_alhussary_mojawwad',
    ],
    tags: 'قرآن كريم • ترتيل • تجويد',
    codec: 'MP3',
    bitrate: 128,
    countryCode: 'EG',
  },
  {
    id: 'builtin-eg-mustafa-ismail',
    name: 'إذاعة الشيخ مصطفى إسماعيل',
    streamUrls: [
      'https://backup.qurango.net/radio/mustafa_ismail',
    ],
    tags: 'قرآن كريم • تلاوات خاشعة',
    codec: 'MP3',
    bitrate: 128,
    countryCode: 'EG',
  },
];

// Single primary directory host (verified online & fastest) + round-robin DNS failover
const PRIMARY_RADIO_HOST = 'de1.api.radio-browser.info';
const FAILOVER_RADIO_HOSTS = [
  PRIMARY_RADIO_HOST,
  'all.api.radio-browser.info',
];

function sanitizeAndRepairStreamUrl(rawUrl: string): string | null {
  if (!rawUrl) return null;
  let cleaned = rawUrl
    .trim()
    .replace(/&amp;/gi, '&')
    .replace(/(?:%22|"|'|`|<|>|\s).*$/g, '')
    .trim();

  if (!cleaned.startsWith('http://') && !cleaned.startsWith('https://')) {
    return null;
  }

  try {
    const parsed = new URL(cleaned);
    const host = parsed.hostname.toLowerCase();

    // Filter out dead Zeno.fm unauthenticated streams (return HTTP 401)
    if (host === 'zeno.fm' || host.endsWith('.zeno.fm')) {
      return null;
    }

    // Upgrade qurango.net/radio/* (502 Bad Gateway) to backup.qurango.net/radio/* (200 OK)
    if (host === 'qurango.net' || host === 'www.qurango.net') {
      parsed.protocol = 'https:';
      parsed.hostname = 'backup.qurango.net';
      return parsed.toString();
    }

    // Strip expired transient Radiojar tokens (?rj-ttl=...&rj-tok=...) so the stream mount works reliably
    if (host.endsWith('.radiojar.com')) {
      parsed.protocol = 'https:';
      parsed.searchParams.delete('rj-ttl');
      parsed.searchParams.delete('rj-tok');
      parsed.searchParams.delete('autoplay');
      return parsed.toString();
    }

    return parsed.toString();
  } catch {
    return null;
  }
}

function buildFailoverStreamList(rawUrls: string[]): string[] {
  const directValid: string[] = [];
  for (const raw of rawUrls) {
    const repaired = sanitizeAndRepairStreamUrl(raw);
    if (repaired && !directValid.includes(repaired)) {
      directValid.push(repaired);
    }
  }

  const ordered: string[] = [];
  for (const url of directValid) {
    if (url.startsWith('/api/')) {
      if (!ordered.includes(url)) ordered.push(url);
      continue;
    }
    const proxyUrl = `/api/radio/stream?url=${encodeURIComponent(url)}`;
    // If stream is plain HTTP or known to omit CORS headers (like xecod / mobtada / melege),
    // try the local proxy first so Web Audio API (crossOrigin="anonymous") and HTTPS pages work cleanly.
    const prefersProxyFirst =
      url.startsWith('http://') ||
      url.includes('radio.xecod.com') ||
      url.includes('mobtada.com') ||
      url.includes('ahmed-melege.com') ||
      url.includes('streamerr.co');

    if (prefersProxyFirst) {
      if (!ordered.includes(proxyUrl)) ordered.push(proxyUrl);
      if (!ordered.includes(url)) ordered.push(url);
    } else {
      if (!ordered.includes(url)) ordered.push(url);
      if (!ordered.includes(proxyUrl)) ordered.push(proxyUrl);
    }
  }

  return ordered;
}

function normalizeStationKey(name: string, streamUrls: string[]): string {
  // Check if any stream URL is the Cairo Quran Radio mount
  for (const u of streamUrls) {
    if (u.includes('8s5u5tpdtwzuv')) {
      return 'canonical-eg-quran-cairo';
    }
  }

  const normalizedName = String(name || '')
    .normalize('NFKC')
    .toLowerCase()
    .replace(/^omniradio\s*[-–—:]\s*/i, '')
    .replace(/[\u064B-\u065F\u0670\u0640]/g, '')
    .replace(/[أإآٱ]/g, 'ا')
    .replace(/ة/g, 'ه')
    .replace(/ى/g, 'ي')
    .replace(/\b(?:radio|fm|station|live|online|stream|broadcast)\b/g, ' ')
    .replace(/(?:اذاعه|الراديو|راديو)/g, ' ')
    .replace(/[^\p{L}\p{N}]+/gu, ' ')
    .replace(/\s+/g, ' ')
    .trim();

  // Detect all Cairo Quran Radio variations
  if (
    normalizedName.includes('القران الكريم من القاهره') ||
    normalizedName.includes('القران الكريم القاهره') ||
    normalizedName === 'القران الكريم' ||
    normalizedName === 'quran' ||
    normalizedName.includes('holy quran cairo') ||
    normalizedName.includes('holy quran radio cairo')
  ) {
    return 'canonical-eg-quran-cairo';
  }

  // Strip trailing copy digits like "القران الكريم من القاهره 1"
  const cleanNameKey = normalizedName.replace(/\s+\d+$/, '').trim();
  return cleanNameKey || streamUrls[0] || 'unknown-station';
}

function normalizeCanonicalStreamMount(url: string): string {
  try {
    const raw = url.startsWith('/api/radio/stream?url=')
      ? decodeURIComponent(url.slice('/api/radio/stream?url='.length))
      : url;
    const parsed = new URL(raw);
    const host = parsed.hostname.toLowerCase().replace(/^(?:www|stream|n\d+[a-z0-9]*)\./, '');
    const pathKey = parsed.pathname.replace(/\/+$/, '').toLowerCase();
    return `${host}${pathKey}`;
  } catch {
    return url.toLowerCase();
  }
}

function cleanDisplayStationName(name: string): string {
  return String(name || 'Radio')
    .replace(/^OmniRadio\s*[-–—:]\s*/i, '')
    .replace(/\s+/g, ' ')
    .trim();
}

function deduplicateStations(stations: RadioStation[]): RadioStation[] {
  const byNameKey = new Map<string, RadioStation>();
  const mountToNameKey = new Map<string, string>();

  for (const station of stations) {
    const validStreams = buildFailoverStreamList(station.streamUrls);
    if (validStreams.length === 0) continue;

    const cleanName = cleanDisplayStationName(station.name);
    let key = normalizeStationKey(cleanName, validStreams);

    // Check if any of the station's underlying stream mounts already belongs to an existing station
    for (const u of validStreams) {
      if (u.startsWith('/api/')) continue;
      const mountKey = normalizeCanonicalStreamMount(u);
      const existingKey = mountToNameKey.get(mountKey);
      if (existingKey) {
        key = existingKey;
        break;
      }
    }

    const existing = byNameKey.get(key);
    if (!existing) {
      const entry: RadioStation = {
        ...station,
        name: key === 'canonical-eg-quran-cairo' ? EGYPT_QURAN_STATION.name : cleanName,
        streamUrls: validStreams,
      };
      byNameKey.set(key, entry);
      for (const u of validStreams) {
        if (!u.startsWith('/api/')) {
          mountToNameKey.set(normalizeCanonicalStreamMount(u), key);
        }
      }
    } else {
      // Merge failover stream URLs
      const mergedUrls = [...existing.streamUrls];
      for (const u of validStreams) {
        if (!mergedUrls.includes(u)) mergedUrls.push(u);
        if (!u.startsWith('/api/')) {
          mountToNameKey.set(normalizeCanonicalStreamMount(u), key);
        }
      }
      existing.streamUrls = mergedUrls;

      // Prefer non-OmniRadio name and higher bitrate
      if (station.name && !/^OmniRadio/i.test(station.name) && /^OmniRadio/i.test(existing.name)) {
        existing.name = cleanName;
      }
      if ((station.bitrate || 0) > (existing.bitrate || 0)) {
        existing.bitrate = station.bitrate;
      }
      if (!existing.tags && station.tags) {
        existing.tags = station.tags;
      }
    }
  }

  return Array.from(byNameKey.values());
}

export class RadioService {
  private activeHostIndex = 0;

  private getHost(): string {
    return FAILOVER_RADIO_HOSTS[this.activeHostIndex % FAILOVER_RADIO_HOSTS.length];
  }

  private rotateHost() {
    this.activeHostIndex = (this.activeHostIndex + 1) % FAILOVER_RADIO_HOSTS.length;
  }

  async getStations(countryCode: string | null, query = ''): Promise<RadioStation[]> {
    const isWorld = !countryCode || countryCode.trim() === '';
    const isEgypt = countryCode?.toUpperCase() === 'EG';
    const trimmedQuery = query.trim();
    const useTopVotes = isWorld && trimmedQuery === '';
    const endpoint = useTopVotes ? '/json/stations/topvote' : '/json/stations/search';

    const params = new URLSearchParams({
      hidebroken: 'true',
      lastcheckok: '1',
      order: 'votes',
      reverse: 'true',
      limit: '120',
    });

    if (!isWorld && countryCode) {
      params.append('countrycode', countryCode);
    }
    if (trimmedQuery !== '') {
      params.append('name', trimmedQuery);
    }

    const filterCuratedEgypt = (): RadioStation[] => {
      if (!isEgypt) return [];
      if (!trimmedQuery) return CURATED_EGYPT_STATIONS;
      const q = trimmedQuery.toLowerCase();
      return CURATED_EGYPT_STATIONS.filter(
        (s) =>
          s.name.toLowerCase().includes(q) ||
          s.tags.toLowerCase().includes(q)
      );
    };

    // Query primary directory host first, rotating to failover only on network failure
    for (let attempt = 0; attempt < FAILOVER_RADIO_HOSTS.length; attempt++) {
      const host = this.getHost();
      const url = `https://${host}${endpoint}?${params.toString()}`;

      try {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 7500);

        const res = await fetch(url, {
          signal: controller.signal,
          headers: {
            Accept: 'application/json',
          },
        });
        clearTimeout(timeoutId);

        if (res.ok) {
          const data = await res.json();
          const fetchedStations: RadioStation[] = [];

          for (const item of Array.isArray(data) ? data : []) {
            if (item.lastcheckok === 0) continue;
            const resolved = String(item.url_resolved || '').trim();
            const raw = String(item.url || '').trim();
            const rawCandidates = [resolved, raw].filter(Boolean);
            if (rawCandidates.length === 0) continue;

            fetchedStations.push({
              id: item.stationuuid || rawCandidates[0],
              name: item.name || 'Radio',
              streamUrls: rawCandidates,
              tags: item.tags || '',
              codec: String(item.codec || 'MP3').toUpperCase(),
              bitrate: Number(item.bitrate) || 128,
              countryCode: String(item.countrycode || countryCode || '').toUpperCase(),
            });
          }

          const combined = isEgypt
            ? [...filterCuratedEgypt(), ...fetchedStations]
            : fetchedStations;

          return deduplicateStations(combined);
        }
      } catch (e) {
        console.warn(`Radio directory fetch failed on ${host}:`, e);
        this.rotateHost();
      }
    }

    // Offline / failover curated fallback
    if (isEgypt) {
      return deduplicateStations(filterCuratedEgypt());
    }

    return [];
  }
}

export const radioService = new RadioService();
