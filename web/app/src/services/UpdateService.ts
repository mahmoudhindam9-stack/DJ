export const APP_VERSION = '3.14.4';

export interface UpdateInfo {
  currentVersion: string;
  latestVersion: string;
  updateAvailable: boolean;
  canAutoInstall: boolean;
  releaseUrl: string;
  assetUrl?: string;
  downloadedPackageAvailable?: boolean;
  downloadPath?: string;
  publishedAt?: string;
  releaseNotes?: string;
}

export type UpdateStatus = 'idle' | 'checking' | 'upToDate' | 'available' | 'installing' | 'error';

interface UpdateApiResponse<T> {
  ok: boolean;
  data?: T;
  error?: string;
}

class UpdateService {
  async checkForUpdates(): Promise<UpdateInfo> {
    const response = await fetch('/api/update/check', {
      method: 'GET',
      cache: 'no-store',
      headers: { Accept: 'application/json' },
    });

    let payload: UpdateApiResponse<UpdateInfo>;
    try {
      payload = await response.json();
    } catch {
      throw new Error('The update service returned an invalid response.');
    }

    if (!response.ok || !payload.ok || !payload.data) {
      throw new Error(payload.error || `Update check failed (HTTP ${response.status}).`);
    }
    return payload.data;
  }

  async installUpdate(): Promise<void> {
    const response = await fetch('/api/update/install', {
      method: 'POST',
      cache: 'no-store',
      headers: { Accept: 'application/json' },
    });

    let payload: UpdateApiResponse<{ accepted: boolean; message?: string }>;
    try {
      payload = await response.json();
    } catch {
      // The Windows server intentionally closes after accepting the self-update.
      if (response.ok) return;
      throw new Error('The update installer did not return a valid response.');
    }

    if (!response.ok || !payload.ok || !payload.data?.accepted) {
      throw new Error(payload.error || 'Could not start the update installer.');
    }
  }
}

export const updateService = new UpdateService();
