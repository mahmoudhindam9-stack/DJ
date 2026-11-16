import type { AlbumatyLink } from './OnlineMusicService';

export interface MoodCategory {
  id: string;
  title: string;
  searchTerms: string[];
  preferredArtists: string[];
  excludedTerms?: string[];
}

export const MOOD_CATEGORIES: MoodCategory[] = [
  { id: 'sad', title: 'حزين', searchTerms: ['هيجيلي موجوع','بقول عادي','اتنسيت','فاضي شوية','خليني ذكرى','ياريتك فاهمني','تامر عاشور','شيرين','مسلم','احمد كامل'], preferredArtists: ['تامر عاشور','شيرين','مسلم','احمد كامل','مصطفى كامل','وائل جسار','انغام','ادم'], excludedTerms: ['مهرجان','فرح','رقص','زفة','سنة حلوة','اليوم الحلو','وسع وسع','عيد ميلاد','دلعنا'] },
  { id: 'happy', title: 'سعادة وبهجة', searchTerms: ['اليوم الحلو','اختياراتي','وسع وسع','يا انا يا لا','اماكن السهر','حلو المكان','هرمون السعادة','يا ستار','بالبنط العريض','بشرة خير'], preferredArtists: ['احمد سعد','عمرو دياب','حماقي','تامر حسني','نانسي عجرم','حسين الجسمي','روبي'], excludedTerms: ['حزين','جرح','دموع','فراق','موجوع','وداع','خيانة','بكاء','اتنسيت'] },
  { id: 'drive', title: 'قيادة وسفر', searchTerms: ['كايروكي','بسرح واتوه','روما','ديسكو مصر','شارموفرز','مسار اجباري','توليت','مروان موسى','امير عيد','البخت'], preferredArtists: ['كايروكي','امير عيد','ديسكو مصر','شارموفرز','مسار اجباري','توليت','ويجز'], excludedTerms: ['نوم','سنة حلوة','زفة','موال'] },
  { id: 'midnight', title: 'هدوء منتصف الليل', searchTerms: ['انا لحبيبي','زيديني عشقا','كل القصايد','اواخر الشتا','مشاعر','عبادي الجوهر','كاظم الساهر','مروان خوري','فضل شاكر'], preferredArtists: ['فيروز','كاظم الساهر','مروان خوري','فضل شاكر','اليسا','عبادي الجوهر','وائل كفوري'], excludedTerms: ['مهرجان','رقص','زفة','شعبي','وسع وسع','حمو بيكا','شاكوش','صاصا'] },
  { id: 'sleep', title: 'نوم واسترخاء', searchTerms: ['عمر خيرت','سكن الليل','اعطني الناي','ماجدة الرومي','عبير نعمة','فايا يونان','جوليا بطرس','فيروز','موسيقى هادئة'], preferredArtists: ['عمر خيرت','ماجدة الرومي','عبير نعمة','فايا يونان','جوليا بطرس','فيروز'], excludedTerms: ['مهرجان','رقص','زفة','شعبي','وسع وسع','حمو بيكا','شاكوش','صاصا','دبكة'] },
  { id: 'dance', title: 'أفراح ورقص', searchTerms: ['محمود الليثي','حكيم','رضا البحراوي','حسن شاكوش','بنت الجيران','عمر كمال','بوسي اه يا دنيا','عبد الباسط حمودة','عصام صاصا'], preferredArtists: ['محمود الليثي','حكيم','رضا البحراوي','حسن شاكوش','عمر كمال','سعد الصغير'], excludedTerms: ['حزين','دموع','فراق','موجوع','وداع','اتنسيت'] },
  { id: 'birthday', title: 'عيد الميلاد', searchTerms: ['كل سنة وانت طيب','سنة حلوة يا جميل','انزل يا جميل','عيد ميلاد','وليد توفيق','حمادة هلال','نانسي عجرم','تامر حسني'], preferredArtists: ['وليد توفيق','حمادة هلال','نانسي عجرم','تامر حسني'], excludedTerms: ['حزين','دموع','فراق','موجوع','وداع','جرح','خيانة'] },
  { id: 'romance', title: 'رومانسية', searchTerms: ['اجمل احساس','حالة حب','تملي معاك','معاك قلبي','مليون احبك','يا غايب','تيجي نتراهن','حبه جنة','على بالي','ما بلاش'], preferredArtists: ['اليسا','عمرو دياب','ماجد المهندس','رامي صبري','وائل جسار','حماقي'], excludedTerms: ['مهرجان','خيانة','وداع','حمو بيكا','صاصا','شاكوش'] },
  { id: 'sport', title: 'رياضة وحماس', searchTerms: ['دورك جاي','بطل عالم','مروان بابلو','مروان موسى','عفروتو','ويجز','كايروكي','ابيوسف','الجوكر'], preferredArtists: ['ويجز','مروان بابلو','مروان موسى','عفروتو','ابيوسف','الجوكر'], excludedTerms: ['حزين','نوم','دموع','فراق','موجوع','خليني ذكرى'] },
  { id: 'new', title: 'أحدث الإصدارات', searchTerms: ['عمرو دياب','تامر حسني','احمد سعد','تامر عاشور','شيرين','انغام','حماقي','اليسا','رامي صبري','اصالة','ويجز','توليت'], preferredArtists: ['توليت','احمد سعد','عمرو دياب','تامر عاشور','رامي صبري'] },
  { id: 'trending', title: 'ترند الآن', searchTerms: ['احمد سعد','تامر عاشور','توليت','الشامي','عمرو دياب','ويجز','عصام صاصا','رضا البحراوي','شيرين','سيلاوي','رحمة رياض'], preferredArtists: ['الشامي','سيلاوي','توليت','احمد سعد','عصام صاصا','مسلم'] },
  { id: 'classic', title: 'قديم وطرب أصيل', searchTerms: ['ام كلثوم','عبد الحليم حافظ','وردة الجزائرية','فريد الاطرش','محمد عبد الوهاب','فايزة احمد','نجاة الصغيرة','شادية','جورج وسوف','ميادة الحناوي'], preferredArtists: ['ام كلثوم','عبد الحليم حافظ','وردة الجزائرية','فريد الاطرش','محمد عبد الوهاب','صباح فخري'], excludedTerms: ['مهرجان','راب','ويجز','صاصا','بيكا'] },
  { id: 'classic-gulf', title: 'قديم • خليجي', searchTerms: ['طلال مداح','محمد عبده','عبادي الجوهر','عبد الكريم عبد القادر','ابو بكر سالم','خالد عبد الرحمن','عبد المجيد عبد الله','راشد الماجد'], preferredArtists: ['طلال مداح','محمد عبده','عبادي الجوهر','ابو بكر سالم','خالد عبد الرحمن'], excludedTerms: ['مهرجان','راب','صاصا','بيكا'] },
  { id: 'classic-levant', title: 'قديم • شامي', searchTerms: ['فيروز','وديع الصافي','صباح فخري','جورج وسوف','ملحم بركات','زكي ناصيف','نصري شمس الدين','ميادة الحناوي'], preferredArtists: ['وديع الصافي','صباح فخري','ملحم بركات','زكي ناصيف','فيروز'], excludedTerms: ['مهرجان','صاصا','بيكا'] },
  { id: 'classic-iraqi', title: 'قديم • عراقي', searchTerms: ['كاظم الساهر','ناظم الغزالي','ياس خضر','سعدون جابر','فؤاد سالم','داخل حسن','حسين نعمة','الهام المدفعي'], preferredArtists: ['ناظم الغزالي','ياس خضر','سعدون جابر','كاظم الساهر','الهام المدفعي'], excludedTerms: ['مهرجان','صاصا','بيكا'] },
  { id: 'classic-maghreb', title: 'قديم • مغاربي', searchTerms: ['وردة الجزائرية','الشاب خالد','الشاب حسني','عبد الهادي بلخياط','نعيمة سميح','لطفي بوشناق','ذكرى','صابر الرباعي'], preferredArtists: ['الشاب حسني','الشاب خالد','عبد الهادي بلخياط','لطفي بوشناق','وردة الجزائرية'], excludedTerms: ['مهرجان','صاصا','بيكا'] },
  { id: 'mawawil', title: 'مواويل', searchTerms: ['موال','رضا البحراوي','عبد الباسط حمودة','طارق الشيخ','جورج وسوف','احمد عدوية','حكيم','وديع الصافي','صباح فخري'], preferredArtists: ['احمد عدوية','طارق الشيخ','عبد الباسط حمودة','محمد رشدي','جورج وسوف','صباح فخري'] },
];

const SEMANTIC_POOLS: Array<{ triggers: string[]; terms: string[] }> = [
  { triggers: ['حزين','حزن','فراق','دموع','وجع','جرح','sad'], terms: ['هيجيلي موجوع','بقول عادي','كده يا قلبي','اتنسيت','فاضي شوية','تامر عاشور','شيرين','مسلم','وائل جسار'] },
  { triggers: ['فرح','سعاده','بهجه','مبسوط','happy'], terms: ['اليوم الحلو','اختياراتي','وسع وسع','يا انا يا لا','حلو المكان','احمد سعد','عمرو دياب','حماقي','روبي'] },
  { triggers: ['حماس','جيم','رياضه','تمرين','workout','gym','sport'], terms: ['دورك جاي','بطل عالم','ويجز','مروان بابلو','مروان موسى','عفروتو','كايروكي'] },
  { triggers: ['رقص','افراح','زفه','شعبي','حفله','party','dance'], terms: ['محمود الليثي','حكيم','رضا البحراوي','حسن شاكوش','عمر كمال','بوسي'] },
  { triggers: ['حب','رومانسي','عشق','حبيبي','love','romance'], terms: ['اجمل احساس','حالة حب','تملي معاك','مليون احبك','اليسا','ماجد المهندس','وائل جسار'] },
  { triggers: ['هدوء','ليل','روقان','chill','midnight'], terms: ['انا لحبيبي','زيديني عشقا','كل القصايد','فيروز','كاظم الساهر','مروان خوري'] },
  { triggers: ['نوم','استرخاء','sleep','relax'], terms: ['عمر خيرت','سكن الليل','اعطني الناي','ماجدة الرومي','فيروز'] },
  { triggers: ['سفر','طريق','قياده','سياره','drive','road'], terms: ['كايروكي','ديسكو مصر','شارموفرز','مسار اجباري','توليت','امير عيد'] },
  { triggers: ['خليجي','سعودي','كويتي','اماراتي'], terms: ['محمد عبده','راشد الماجد','ماجد المهندس','عبد المجيد عبد الله','طلال مداح','عبادي الجوهر'] },
  { triggers: ['شامي','لبناني','سوري','اردني','فلسطيني'], terms: ['فيروز','جورج وسوف','وائل كفوري','نانسي عجرم','ناصيف زيتون','ملحم بركات'] },
  { triggers: ['عراقي','بغدادي'], terms: ['كاظم الساهر','ماجد المهندس','سيف نبيل','رحمة رياض','حاتم العراقي','ياس خضر'] },
  { triggers: ['مغربي','مغاربي','جزائري','تونسي','راي'], terms: ['سعد لمجرد','الشاب خالد','الشاب حسني','وردة الجزائرية','سميرة سعيد','صابر الرباعي'] },
  { triggers: ['قديم','طرب','كلاسيك','تراث','classic'], terms: ['ام كلثوم','عبد الحليم حافظ','وردة الجزائرية','فريد الاطرش','محمد عبد الوهاب','نجاة الصغيرة'] },
];

export function normalizeMusicText(value: string): string {
  return value.toLowerCase()
    .replace(/[أإآ]/g, 'ا')
    .replace(/ة/g, 'ه')
    .replace(/ى/g, 'ي')
    .replace(/[ؤ]/g, 'و')
    .replace(/[ئ]/g, 'ي')
    .replace(/ـ/g, '')
    .replace(/[ًٌٍَُِّْ]/g, '')
    .replace(/\s+/g, ' ')
    .trim();
}

export function buildSmartVibeQueries(prompt: string, category?: MoodCategory): string[] {
  const normalized = normalizeMusicText(prompt);
  const pools = SEMANTIC_POOLS
    .filter((pool) => pool.triggers.some((trigger) => normalized.includes(normalizeMusicText(trigger))))
    .map((pool) => [...pool.terms]);
  const terms = [...(category?.searchTerms || [])];
  const max = Math.max(terms.length, ...pools.map((pool) => pool.length), 0);
  const interleaved: string[] = [];
  for (let index = 0; index < max; index += 1) {
    if (terms[index]) interleaved.push(terms[index]);
    for (const pool of pools) if (pool[index]) interleaved.push(pool[index]);
  }

  const generic = new Set([
    'شغل','شغلي','لي','عايز','عاوز','اريد','ابحث','سمعني','ممكن','هات','افتح',
    'اغاني','اغنيه','مزيكا','موسيقي','قائمه','تشغيل','حزين','حزينه','حزن','فراق','دموع',
    'وجع','جرح','سعاده','بهجه','فرح','قياده','سفر','طريق','هدوء','ليل','نوم','استرخاء',
    'افراح','رقص','عيد','ميلاد','رومانسيه','رومانسي','حب','عشق','رياضه','حماس','جيم',
    'جديد','ترند','قديم','طرب','مواويل','موال','مصري','خليجي','شامي','عراقي','مغربي',
  ]);
  const userTerms = prompt.replace(/[•,،]/g, ' ').split(/\s+/)
    .filter((token) => {
      const normalizedToken = normalizeMusicText(token);
      return normalizedToken.length >= 2 && !generic.has(normalizedToken);
    }).join(' ');

  return [...new Set([userTerms, ...interleaved, ...(category ? [category.title] : [])]
    .map((term) => term.trim())
    .filter(Boolean))].slice(0, 10);
}

function trackSignature(link: AlbumatyLink): string {
  const clean = link.title
    .replace(/^(?:اغنية|أغنية|تحميل|استماع)\s+/i, '')
    .replace(/\s*(?:MP3|كاملة|حصريا|بالكلمات)\s*/i, ' ')
    .replace(/\([^)]*\)|\[[^]]*\]/g, ' ');
  return normalizeMusicText(clean)
    .replace(/[|•_]+/g, ' ')
    .replace(/\s*-\s*/g, ' - ')
    .replace(/[^\p{L}\p{N}\s-]/gu, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function artistBucket(link: AlbumatyLink): string {
  const title = link.title
    .replace(/^(?:اغنية|أغنية|تحميل|استماع)\s+/i, '')
    .replace(/\s*MP3\s*$/i, '')
    .trim();
  const artist = title.includes(' - ') ? title.split(' - ').pop() : title.split(/\s+/).slice(0, 2).join(' ');
  return normalizeMusicText(artist || link.url);
}

function isAllowedByPrompt(prompt: string, title: string): boolean {
  const request = normalizeMusicText(prompt);
  const candidate = normalizeMusicText(title);
  const exclusions: Array<[string[], string[]]> = [
    [['حزين','حزينه','فراق','دموع','وجع','جرح'], ['مهرجان','فرح','رقص','زفه','سنه حلوه','اليوم الحلو']],
    [['فرح','سعاده','بهجه','رقص','ميلاد'], ['حزين','دموع','فراق','جرح','موجوع','وداع']],
    [['هدوء','نوم','استرخاء','ليل','هادئ'], ['مهرجان','رقص','زفه','وسع وسع','شاكوش','بيكا','صاصا']],
  ];
  return !exclusions.some(([triggers, forbidden]) =>
    triggers.some((term) => request.includes(term)) && forbidden.some((term) => candidate.includes(term))
  );
}

export function rankAndDiversifyTracks(
  prompt: string,
  candidates: AlbumatyLink[],
  category?: MoodCategory,
  recentSignatures: Set<string> = new Set(),
  diversify = true,
): AlbumatyLink[] {
  const seenUrls = new Set<string>();
  const seenSignatures = new Set<string>();
  const filtered = candidates.filter((track) => {
    const urlKey = track.url.trim().replace(/\/$/, '').toLowerCase();
    const signature = trackSignature(track);
    if (seenUrls.has(urlKey) || (signature && seenSignatures.has(signature))) return false;
    seenUrls.add(urlKey);
    if (signature) seenSignatures.add(signature);
    const title = normalizeMusicText(track.title);
    return !(category?.excludedTerms || []).some((term) => title.includes(normalizeMusicText(term)))
      && isAllowedByPrompt(prompt, track.title);
  });
  const preferred = (category?.preferredArtists || []).map(normalizeMusicText);
  const scored = filtered.map((track) => {
    const signature = trackSignature(track);
    const title = normalizeMusicText(track.title);
    let score = recentSignatures.has(signature) ? 0 : 100;
    if (preferred.some((artist) => title.includes(artist))) score += 25;
    if (title.includes(normalizeMusicText(prompt)) && prompt.trim().length <= 8) score -= 40;
    return { track, score };
  }).sort((a, b) => b.score - a.score).map((entry) => entry.track);

  if (!diversify) return scored;
  const buckets = new Map<string, AlbumatyLink[]>();
  for (const track of scored) {
    const key = artistBucket(track);
    buckets.set(key, [...(buckets.get(key) || []), track]);
  }
  const interleaved: AlbumatyLink[] = [];
  const overflow: AlbumatyLink[] = [];
  const counts = new Map<string, number>();
  const lists = [...buckets.entries()].map(([key, tracks]) => ({ key, tracks, index: 0 }));
  while (lists.some((list) => list.index < list.tracks.length)) {
    for (const list of lists) {
      if (list.index >= list.tracks.length) continue;
      const item = list.tracks[list.index++];
      const count = counts.get(list.key) || 0;
      if (count < 4) {
        interleaved.push(item);
        counts.set(list.key, count + 1);
      } else overflow.push(item);
    }
  }
  return [...interleaved, ...overflow];
}
