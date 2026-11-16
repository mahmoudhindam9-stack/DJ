export interface LocalMusicGenerationRequest {
  prompt: string;
  lyrics: string;
  bpm: number;
  durationSeconds: number;
  vocalMode: 'instrumental' | 'generated' | 'custom';
}

export interface LocalMusicGenerationResult {
  audioUrl: string;
  lyrics: string;
  durationSeconds: number;
  bpm: number;
  model: string;
  generatedAt: string;
}

interface ServiceResponse<T> {
  ok: boolean;
  data?: T;
  error?: string;
}

async function readResponse<T>(response: Response, fallback: string): Promise<ServiceResponse<T>> {
  try {
    return await response.json() as ServiceResponse<T>;
  } catch {
    throw new Error(fallback);
  }
}

export async function checkLocalMusicEngine(): Promise<boolean> {
  const response = await fetch('/api/local-music/health', {
    method: 'GET',
    cache: 'no-store',
    headers: { Accept: 'application/json' },
  });
  const payload = await readResponse<{ ready: boolean }>(response, 'The local music engine returned an invalid health response.');
  return response.ok && payload.ok && payload.data?.ready === true;
}

export async function startLocalMusicEngine(): Promise<void> {
  const response = await fetch('/api/local-music/start', {
    method: 'POST',
    cache: 'no-store',
    headers: { Accept: 'application/json' },
  });
  const payload = await readResponse<{ launched: boolean; ready?: boolean }>(response, 'The local engine launcher returned an invalid response.');
  if (!response.ok || !payload.ok) {
    throw new Error(payload.error || 'Could not launch ACE-Step locally.');
  }
}

export async function generateLocalMusic(
  request: LocalMusicGenerationRequest,
): Promise<LocalMusicGenerationResult> {
  const response = await fetch('/api/local-music/generate', {
    method: 'POST',
    cache: 'no-store',
    headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });
  const payload = await readResponse<LocalMusicGenerationResult>(response, 'The local AI engine returned an invalid generation response.');
  if (!response.ok || !payload.ok || !payload.data) {
    throw new Error(payload.error || 'ACE-Step music generation failed (HTTP ' + response.status + ').');
  }
  return payload.data;
}
