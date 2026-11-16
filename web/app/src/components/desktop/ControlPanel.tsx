import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  CalendarDays, Clock3, Cloud, CloudDrizzle, CloudFog, CloudLightning, CloudRain,
  CloudSnow, Droplets, LocateFixed, MapPin, Moon, Music2, Pause, Pin, Play,
  RefreshCw, SkipBack, SkipForward, Sun, Thermometer, Volume2, Wind,
  type LucideIcon,
} from 'lucide-react';
import { getCurrentWindow } from '@tauri-apps/api/window';
import { isTauri } from '@tauri-apps/api/core';
import {
  CONTROL_CHANNEL_NAME,
  type ControlPlayerState,
  type DesktopControlCommand,
  type DesktopControlMessage,
} from '../../services/ControlChannel';

interface SavedWeatherLocation {
  latitude: number;
  longitude: number;
  label: string;
  source: 'city' | 'gps';
}

interface WeatherResponse {
  current: {
    time: string;
    temperature_2m: number;
    apparent_temperature: number;
    relative_humidity_2m: number;
    precipitation: number;
    weather_code: number;
    wind_speed_10m: number;
    is_day: number;
  };
  daily: {
    time: string[];
    weather_code: number[];
    temperature_2m_max: number[];
    temperature_2m_min: number[];
    precipitation_probability_max: number[];
  };
  hourly: {
    time: string[];
    temperature_2m: number[];
    precipitation_probability: number[];
    weather_code: number[];
  };
}

function savedLocationFromStorage(): SavedWeatherLocation | null {
  try {
    const value = window.localStorage.getItem('dj-desktop-weather-location');
    if (!value) return null;
    const parsed = JSON.parse(value) as SavedWeatherLocation;
    if (typeof parsed.latitude !== 'number' || typeof parsed.longitude !== 'number' || !parsed.label) return null;
    return parsed;
  } catch {
    return null;
  }
}

function weatherLabel(code: number, isArabic: boolean): string {
  const labels: Record<number, [string, string]> = {
    0: ['Clear sky', 'سماء صافية'], 1: ['Mainly clear', 'صحو غالبًا'],
    2: ['Partly cloudy', 'غائم جزئيًا'], 3: ['Overcast', 'غائم'],
    45: ['Fog', 'ضباب'], 48: ['Dense fog', 'ضباب كثيف'],
    51: ['Light drizzle', 'رذاذ خفيف'], 53: ['Drizzle', 'رذاذ'], 55: ['Dense drizzle', 'رذاذ كثيف'],
    61: ['Light rain', 'مطر خفيف'], 63: ['Rain', 'مطر'], 65: ['Heavy rain', 'مطر غزير'],
    71: ['Light snow', 'ثلوج خفيفة'], 73: ['Snow', 'ثلوج'], 75: ['Heavy snow', 'ثلوج كثيفة'],
    80: ['Rain showers', 'زخات مطر'], 81: ['Rain showers', 'زخات مطر'], 82: ['Heavy showers', 'زخات غزيرة'],
    95: ['Thunderstorm', 'عاصفة رعدية'], 96: ['Thunderstorm with hail', 'عاصفة مع بَرَد'],
    99: ['Severe thunderstorm', 'عاصفة شديدة'],
  };
  const pair = labels[code] || ['Changing conditions', 'أحوال جوية متغيرة'];
  return pair[isArabic ? 1 : 0];
}

function weatherIconForCode(code: number, isDay = true): LucideIcon {
  if (code === 0 || code === 1) return isDay ? Sun : Moon;
  if (code === 45 || code === 48) return CloudFog;
  if (code === 51 || code === 53 || code === 55) return CloudDrizzle;
  if ([61, 63, 65, 80, 81, 82].includes(code)) return CloudRain;
  if ([71, 73, 75, 77].includes(code)) return CloudSnow;
  if ([95, 96, 99].includes(code)) return CloudLightning;
  return Cloud;
}

function formatClockTime(time: string, isArabic: boolean): string {
  const date = new Date(time);
  if (Number.isNaN(date.getTime())) return time.slice(11, 16);
  return new Intl.DateTimeFormat(isArabic ? 'ar-EG' : 'en-GB', {
    hour: '2-digit', minute: '2-digit',
  }).format(date);
}

function formatDay(date: string, isArabic: boolean): string {
  return new Intl.DateTimeFormat(isArabic ? 'ar-EG' : 'en-GB', { weekday: 'short' }).format(new Date(date + 'T12:00:00'));
}

function formatDuration(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  return Math.floor(total / 60) + ':' + String(total % 60).padStart(2, '0');
}

export const ControlPanel: React.FC = () => {
  const [player, setPlayer] = useState<ControlPlayerState | null>(null);
  const [now, setNow] = useState<Date>(new Date());
  const [alwaysOnTop, setAlwaysOnTop] = useState<boolean>(() => {
    try { return window.localStorage.getItem('dj-control-always-on-top') !== 'false'; } catch { return true; }
  });
  const [weatherLocation, setWeatherLocation] = useState<SavedWeatherLocation | null>(() => savedLocationFromStorage());
  const [weather, setWeather] = useState<WeatherResponse | null>(null);
  const [weatherLoading, setWeatherLoading] = useState(false);
  const [weatherError, setWeatherError] = useState<string | null>(null);
  const [cityQuery, setCityQuery] = useState('');
  const [citySearching, setCitySearching] = useState(false);
  const channelRef = useRef<BroadcastChannel | null>(null);

  const isArabic = player?.isArabic ?? document.documentElement.lang === 'ar';
  const colors = player?.themeColors;
  const background = colors?.background || '#080c14';
  const surface = colors?.surface || '#111827';
  const surfaceVariant = colors?.surfaceVariant || '#172235';
  const foreground = colors?.textPrimary || '#f3f6ff';
  const muted = colors?.textMuted || '#9aa7bd';
  const accent = colors?.primary || '#35a7ff';
  const border = colors?.border || '#253249';
  const localDesktopBackend = window.location.protocol === 'http:' &&
    (window.location.hostname === '127.0.0.1' || window.location.hostname === 'localhost');
  const topSupported = isTauri() || localDesktopBackend;

  const applyWindowPin = async (enabled: boolean) => {
    if (isTauri()) {
      await getCurrentWindow().setAlwaysOnTop(enabled);
      return;
    }
    if (!localDesktopBackend) throw new Error('Always-on-top needs the installed desktop launcher.');
    const response = await fetch('/api/window/always-on-top', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ enabled }),
    });
    const result = await response.json() as { ok?: boolean; error?: string };
    if (!response.ok || !result.ok) throw new Error(result.error || 'The Windows window pin could not be changed.');
  };

  const sendCommand = (command: DesktopControlCommand) => {
    channelRef.current?.postMessage({ type: 'command', command } satisfies DesktopControlMessage);
  };

  const loadWeather = async (location: SavedWeatherLocation) => {
    setWeatherLoading(true);
    setWeatherError(null);
    try {
      const query = new URLSearchParams({
        latitude: String(location.latitude),
        longitude: String(location.longitude),
        current: 'temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m,is_day',
        daily: 'weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max',
        hourly: 'temperature_2m,precipitation_probability,weather_code',
        timezone: 'auto',
        forecast_days: '7',
      });
      const response = await fetch('https://api.open-meteo.com/v1/forecast?' + query.toString());
      if (!response.ok) throw new Error('weather');
      const payload = await response.json() as WeatherResponse;
      if (!payload.current || !payload.daily?.time?.length) throw new Error('weather');
      setWeather(payload);
      setWeatherLocation(location);
      try { window.localStorage.setItem('dj-desktop-weather-location', JSON.stringify(location)); } catch { /* optional persistence */ }
      setWeatherError(null);
    } catch {
      setWeatherError('weather');
    } finally {
      setWeatherLoading(false);
    }
  };

  const useMyLocation = () => {
    if (!navigator.geolocation) {
      setWeatherError('unsupported');
      return;
    }
    setWeatherLoading(true);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        const location: SavedWeatherLocation = {
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
          label: isArabic ? 'موقعي الحالي' : 'Current location',
          source: 'gps',
        };
        void loadWeather(location);
      },
      () => {
        setWeatherLoading(false);
        setWeatherError('location-denied');
      },
      { enableHighAccuracy: false, timeout: 12000, maximumAge: 15 * 60 * 1000 }
    );
  };

  const searchCity = async () => {
    const query = cityQuery.trim();
    if (!query) return;
    setCitySearching(true);
    setWeatherError(null);
    try {
      const params = new URLSearchParams({ name: query, count: '1', language: isArabic ? 'ar' : 'en', format: 'json' });
      const response = await fetch('https://geocoding-api.open-meteo.com/v1/search?' + params.toString());
      if (!response.ok) throw new Error('city');
      const payload = await response.json() as { results?: Array<{ name: string; country?: string; latitude: number; longitude: number }> };
      const result = payload.results?.[0];
      if (!result) {
        setWeatherError('city-not-found');
        return;
      }
      await loadWeather({
        latitude: result.latitude,
        longitude: result.longitude,
        label: [result.name, result.country].filter(Boolean).join(', '),
        source: 'city',
      });
    } catch {
      setWeatherError('city');
    } finally {
      setCitySearching(false);
    }
  };

  useEffect(() => {
    document.title = 'DJ Control Center - DJ Desktop Studio';
    document.documentElement.dir = isArabic ? 'rtl' : 'ltr';
    document.documentElement.lang = isArabic ? 'ar' : 'en';
  }, [isArabic]);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    if (!topSupported) return;
    void applyWindowPin(alwaysOnTop).catch((error) => {
      console.warn('Could not apply always-on-top at startup:', error);
      setAlwaysOnTop(false);
    });
  }, []);

  useEffect(() => {
    if (typeof BroadcastChannel === 'undefined') return;
    const channel = new BroadcastChannel(CONTROL_CHANNEL_NAME);
    channelRef.current = channel;
    channel.onmessage = (event: MessageEvent<DesktopControlMessage>) => {
      if (event.data?.type === 'player-state' && event.data.state) setPlayer(event.data.state);
    };
    channel.postMessage({ type: 'request-state' } satisfies DesktopControlMessage);
    return () => {
      channel.close();
      channelRef.current = null;
    };
  }, []);

  useEffect(() => {
    if (weatherLocation) {
      void loadWeather(weatherLocation);
    } else {
      useMyLocation();
    }
    // Initial load only. User-selected locations are handled by the controls below.
  }, []);

  useEffect(() => {
    if (!weatherLocation) return;
    const refresh = window.setInterval(() => { void loadWeather(weatherLocation); }, 30 * 60 * 1000);
    return () => window.clearInterval(refresh);
  }, [weatherLocation]);

  const clockText = useMemo(() => new Intl.DateTimeFormat(isArabic ? 'ar-EG' : undefined, {
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  }).format(now), [now, isArabic]);
  const dateText = useMemo(() => new Intl.DateTimeFormat(isArabic ? 'ar-EG' : undefined, {
    weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
  }).format(now), [now, isArabic]);
  const cardStyle: React.CSSProperties = { backgroundColor: surface, borderColor: border, color: foreground };
  const WeatherIcon = weather ? weatherIconForCode(weather.current.weather_code, weather.current.is_day === 1) : Cloud;

  const toggleAlwaysOnTop = async (checked: boolean) => {
    setAlwaysOnTop(checked);
    try { window.localStorage.setItem('dj-control-always-on-top', String(checked)); } catch { /* optional persistence */ }
    try {
      await applyWindowPin(checked);
    } catch (error) {
      console.error('Could not change always-on-top:', error);
      setAlwaysOnTop(!checked);
    }
  };

  return (
    <main className="min-h-screen w-full overflow-y-auto p-4 space-y-4" style={{ backgroundColor: background, color: foreground, fontFamily: 'Inter, Segoe UI, sans-serif' }}>
      <header className="flex items-center justify-between gap-3 border-b pb-3" style={{ borderColor: border }}>
        <div className="flex items-center gap-3 min-w-0">
          <div className="w-10 h-10 rounded-xl flex items-center justify-center shrink-0" style={{ background: 'linear-gradient(145deg, ' + accent + ', #7957ff)' }}><Music2 className="w-5 h-5 text-white" /></div>
          <div className="min-w-0"><div className="text-xs font-black tracking-[0.18em]" style={{ color: accent }}>DJ DESKTOP</div><h1 className="text-base font-extrabold">{isArabic ? 'مركز التحكم' : 'Control Center'}</h1></div>
        </div>
        <div className="flex items-center gap-2 rounded-xl px-2.5 py-2 border shrink-0" style={{ backgroundColor: surfaceVariant, borderColor: border }}>
          <Pin className="w-4 h-4" style={{ color: alwaysOnTop ? accent : muted }} />
          <label className="flex items-center gap-1.5 cursor-pointer text-[10px] font-semibold">
            <input type="checkbox" checked={alwaysOnTop} onChange={(event) => { void toggleAlwaysOnTop(event.target.checked); }} disabled={!topSupported} className="accent-blue-500" aria-label={isArabic ? 'دائمًا في المقدمة' : 'Always on top'} />
            <span>{isArabic ? 'دائمًا بالأعلى' : 'Always on top'}</span>
          </label>
        </div>
      </header>

      {!topSupported && <p className="text-[10px] -mt-2" style={{ color: muted }}>{isArabic ? 'ميزة التثبيت بالأعلى متاحة في نسخة سطح المكتب.' : 'Always on top is available in the installed desktop app.'}</p>}

      <section className="rounded-2xl border p-4" style={cardStyle}>
        <div className="flex items-start justify-between gap-3">
          <div>
            <div className="flex items-center gap-2 text-[11px] font-bold uppercase tracking-wider" style={{ color: accent }}><Clock3 className="w-3.5 h-3.5" />{isArabic ? 'الوقت والتاريخ' : 'Time & date'}</div>
            <div className="text-4xl font-black tracking-tight mt-2 tabular-nums">{clockText}</div>
            <div className="text-xs mt-1" style={{ color: muted }}>{dateText}</div>
          </div>
          <CalendarDays className="w-7 h-7 mt-1" style={{ color: accent, opacity: 0.8 }} />
        </div>
      </section>

      <section className="rounded-2xl border p-4 space-y-3" style={cardStyle}>
        <div className="flex items-center justify-between gap-2">
          <div className="text-[11px] font-bold uppercase tracking-wider flex items-center gap-2" style={{ color: accent }}><Music2 className="w-3.5 h-3.5" />{isArabic ? 'المشغل الحالي' : 'Now playing'}</div>
          <span className="flex items-center gap-1.5 text-[10px]" style={{ color: player?.isPlaying ? '#4ade80' : muted }}><span className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: player?.isPlaying ? '#4ade80' : muted }} />{player ? (player.isPlaying ? (isArabic ? 'يعمل' : 'PLAYING') : (isArabic ? 'متوقف' : 'PAUSED')) : (isArabic ? 'في انتظار المشغل' : 'Waiting for player')}</span>
        </div>
        <div className="flex items-center gap-3 min-w-0">
          <div className="w-14 h-14 rounded-xl overflow-hidden flex items-center justify-center shrink-0 border" style={{ backgroundColor: surfaceVariant, borderColor: border }}>
            {player?.song?.coverUri ? <img src={player.song.coverUri} alt="" className="w-full h-full object-cover" /> : <Music2 className="w-6 h-6" style={{ color: accent }} />}
          </div>
          <div className="min-w-0 flex-1"><div className="font-bold text-sm truncate">{player?.song?.title || (isArabic ? 'لا توجد أغنية' : 'No track selected')}</div><div className="text-xs truncate mt-1" style={{ color: muted }}>{player?.song?.artist || (isArabic ? 'شغّل أغنية من المكتبة' : 'Play a track from your library')}</div>{player?.song?.album && <div className="text-[10px] truncate mt-1 opacity-70">{player.song.album}</div>}</div>
        </div>
        <div className="space-y-1">
          <input type="range" min={0} max={player?.durationMs || 100} value={Math.min(player?.currentTimeMs || 0, player?.durationMs || 100)} onChange={(event) => sendCommand({ action: 'seek', ms: Number(event.target.value) })} className="w-full h-1.5 cursor-pointer" style={{ accentColor: accent }} aria-label={isArabic ? 'موضع التشغيل' : 'Playback position'} />
          <div className="flex justify-between text-[10px] tabular-nums" style={{ color: muted }}><span>{formatDuration(player?.currentTimeMs || 0)}</span><span>{formatDuration(player?.durationMs || 0)}</span></div>
        </div>
        <div className="flex items-center justify-center gap-3">
          <button onClick={() => sendCommand({ action: 'toggle-playback-mode' })} className="px-2 py-2 rounded-lg border text-[10px] font-bold" style={{ borderColor: border, backgroundColor: surfaceVariant }} title={isArabic ? 'تغيير وضع التشغيل' : 'Toggle playback mode'}>{player?.playbackMode === 'SHUFFLE' ? 'SHUF' : player?.playbackMode === 'REPEAT_ONE' ? 'REP 1' : player?.playbackMode === 'REPEAT_ALL' ? 'REP ALL' : 'NORMAL'}</button>
          <button onClick={() => sendCommand({ action: 'previous' })} className="w-9 h-9 rounded-full flex items-center justify-center transition-transform active:scale-95" style={{ backgroundColor: surfaceVariant }} title={isArabic ? 'السابق' : 'Previous'}><SkipBack className="w-4 h-4" /></button>
          <button onClick={() => sendCommand({ action: 'toggle' })} className="w-12 h-12 rounded-full flex items-center justify-center shadow-lg transition-transform active:scale-95" style={{ backgroundColor: accent, color: '#fff' }} title={player?.isPlaying ? 'Pause' : 'Play'}>{player?.isPlaying ? <Pause className="w-5 h-5 fill-current" /> : <Play className="w-5 h-5 fill-current ml-0.5" />}</button>
          <button onClick={() => sendCommand({ action: 'next' })} className="w-9 h-9 rounded-full flex items-center justify-center transition-transform active:scale-95" style={{ backgroundColor: surfaceVariant }} title={isArabic ? 'التالي' : 'Next'}><SkipForward className="w-4 h-4" /></button>
        </div>
        <div className="flex items-center gap-2 text-xs" style={{ color: muted }}><Volume2 className="w-4 h-4 shrink-0" /><input type="range" min={0} max={1} step={0.01} value={player?.volume ?? 0.85} onChange={(event) => sendCommand({ action: 'volume', value: Number(event.target.value) })} className="flex-1 cursor-pointer" style={{ accentColor: accent }} aria-label={isArabic ? 'مستوى الصوت' : 'Volume'} /><span className="w-8 text-right tabular-nums">{Math.round((player?.volume ?? 0.85) * 100)}%</span></div>
      </section>

      <section className="rounded-2xl border p-4 space-y-3" style={cardStyle}>
        <div className="flex items-start justify-between gap-3">
          <div>
            <div className="text-[11px] font-bold uppercase tracking-wider flex items-center gap-2" style={{ color: accent }}><WeatherIcon className="w-4 h-4" />{isArabic ? 'الطقس الآن' : 'Current weather'}</div>
            <div className="flex items-center gap-2 mt-2"><div className="text-4xl font-black tabular-nums">{weather ? Math.round(weather.current.temperature_2m) + '°' : '—°'}</div>{weather && <div className="min-w-0"><div className="text-xs font-semibold">{weatherLabel(weather.current.weather_code, isArabic)}</div><div className="text-[10px] mt-1" style={{ color: muted }}>{weatherLocation?.label}</div></div>}</div>
          </div>
          <button onClick={() => { if (weatherLocation) void loadWeather(weatherLocation); else useMyLocation(); }} className="p-2 rounded-lg border" style={{ backgroundColor: surfaceVariant, borderColor: border }} title={isArabic ? 'تحديث الطقس' : 'Refresh weather'}><RefreshCw className={'w-4 h-4 ' + (weatherLoading ? 'animate-spin' : '')} /></button>
        </div>

        {weather && <div className="grid grid-cols-3 gap-2">
          <div className="rounded-xl p-2" style={{ backgroundColor: surfaceVariant }}><Thermometer className="w-3.5 h-3.5 mb-1" style={{ color: accent }} /><div className="text-sm font-bold">{Math.round(weather.current.apparent_temperature)}°</div><div className="text-[9px]" style={{ color: muted }}>{isArabic ? 'المحسوسة' : 'Feels like'}</div></div>
          <div className="rounded-xl p-2" style={{ backgroundColor: surfaceVariant }}><Droplets className="w-3.5 h-3.5 mb-1" style={{ color: accent }} /><div className="text-sm font-bold">{Math.round(weather.current.relative_humidity_2m)}%</div><div className="text-[9px]" style={{ color: muted }}>{isArabic ? 'الرطوبة' : 'Humidity'}</div></div>
          <div className="rounded-xl p-2" style={{ backgroundColor: surfaceVariant }}><Wind className="w-3.5 h-3.5 mb-1" style={{ color: accent }} /><div className="text-sm font-bold">{Math.round(weather.current.wind_speed_10m)}</div><div className="text-[9px]" style={{ color: muted }}>{isArabic ? 'الرياح كم/س' : 'Wind km/h'}</div></div>
        </div>}

        <div className="flex gap-2">
          <input value={cityQuery} onChange={(event) => setCityQuery(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') void searchCity(); }} placeholder={isArabic ? 'اكتب اسم المدينة...' : 'Enter a city...'} className="min-w-0 flex-1 rounded-lg border px-3 py-2 text-xs outline-none" style={{ backgroundColor: surfaceVariant, borderColor: border, color: foreground }} />
          <button onClick={() => { void searchCity(); }} disabled={citySearching || !cityQuery.trim()} className="rounded-lg px-3 text-xs font-bold disabled:opacity-50" style={{ backgroundColor: accent, color: '#fff' }}>{citySearching ? '…' : (isArabic ? 'عرض' : 'Go')}</button>
          <button onClick={useMyLocation} className="rounded-lg border px-2" style={{ borderColor: border }} title={isArabic ? 'استخدام موقعي الحالي' : 'Use current location'}><LocateFixed className="w-4 h-4" /></button>
        </div>

        {weatherError && <p className="text-[10px]" style={{ color: '#fb7185' }}>{weatherError === 'city-not-found' ? (isArabic ? 'لم يتم العثور على المدينة.' : 'City not found. Try another name.') : weatherError === 'location-denied' ? (isArabic ? 'لم يتم السماح بالموقع؛ اكتب اسم المدينة يدويًا.' : 'Location permission was denied. Enter a city instead.') : weatherError === 'unsupported' ? (isArabic ? 'تحديد الموقع غير مدعوم؛ اكتب اسم المدينة.' : 'Geolocation is unavailable. Enter a city instead.') : weatherError === 'city' ? (isArabic ? 'تعذر البحث عن المدينة؛ تحقق من الاتصال.' : 'Could not search that city. Check your connection.') : (isArabic ? 'تعذر تحميل الطقس؛ تحقق من الاتصال أو جرّب مدينة أخرى.' : 'Weather could not load. Check your connection or try another city.')}</p>}
        {weatherLoading && <p className="text-[10px]" style={{ color: muted }}>{isArabic ? 'جاري تحديث بيانات الطقس...' : 'Updating weather…'}</p>}
      </section>

      {weather && <section className="rounded-2xl border p-4 space-y-3" style={cardStyle}>
        <div className="text-[11px] font-bold uppercase tracking-wider flex items-center gap-2" style={{ color: accent }}><Clock3 className="w-3.5 h-3.5" />{isArabic ? 'توقعات اليوم' : "Today's forecast"}</div>
        <div className="grid grid-cols-4 gap-2">
          {(() => {
            const start = weather.hourly.time.findIndex((time) => time >= weather.current.time.slice(0, 13) + ':00');
            const startIndex = Math.max(0, start);
            const indexes = [0, 3, 6, 9].map((offset) => Math.min(weather.hourly.time.length - 1, startIndex + offset));
            return indexes.map((index, slot) => {
              const code = weather.hourly.weather_code[index] ?? weather.current.weather_code;
              const Icon = weatherIconForCode(code);
              return <div key={weather.hourly.time[index] || slot} className="rounded-xl p-2 text-center" style={{ backgroundColor: surfaceVariant }}>
                <div className="text-[9px]" style={{ color: muted }}>{formatClockTime(weather.hourly.time[index] || weather.current.time, isArabic)}</div>
                <Icon className="w-4 h-4 mx-auto my-2" style={{ color: accent }} />
                <div className="text-xs font-bold">{Math.round(weather.hourly.temperature_2m[index] ?? weather.current.temperature_2m)}°</div>
                <div className="text-[9px] mt-1" style={{ color: muted }}>{Math.round(weather.hourly.precipitation_probability[index] ?? 0)}%</div>
              </div>;
            });
          })()}
        </div>
      </section>}

      {weather && <section className="rounded-2xl border p-4 space-y-3" style={cardStyle}>
        <div className="text-[11px] font-bold uppercase tracking-wider flex items-center gap-2" style={{ color: accent }}><CalendarDays className="w-3.5 h-3.5" />{isArabic ? 'توقعات 7 أيام' : '7-day forecast'}</div>
        <div className="space-y-2">
          {weather.daily.time.map((day, index) => {
            const code = weather.daily.weather_code[index];
            const Icon = weatherIconForCode(code);
            const precip = weather.daily.precipitation_probability_max[index] || 0;
            return <div key={day} className="grid grid-cols-[42px_24px_minmax(0,1fr)_52px] items-center gap-2 rounded-lg px-2 py-2 text-xs" style={{ backgroundColor: surfaceVariant }}>
              <span className="font-semibold">{index === 0 ? (isArabic ? 'اليوم' : 'Today') : formatDay(day, isArabic)}</span>
              <Icon className="w-4 h-4" style={{ color: accent }} />
              <span className="truncate text-[10px]" style={{ color: muted }}>{weatherLabel(code, isArabic)}{precip > 0 ? ' · ' + precip + '%' : ''}</span>
              <span className="text-right font-bold tabular-nums">{Math.round(weather.daily.temperature_2m_max[index])}° <span style={{ color: muted }}>{Math.round(weather.daily.temperature_2m_min[index])}°</span></span>
            </div>;
          })}
        </div>
      </section>}
    </main>
  );
};
