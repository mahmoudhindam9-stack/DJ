import { createAuthClient } from '@neon/auth';
import { BetterAuthReactAdapter } from '@neon/auth/react/adapters';

const authUrl = import.meta.env.VITE_NEON_AUTH_URL;

export const cloudAuthEnabled = typeof authUrl === 'string' && authUrl.length > 0;
export const cloudAuthClient = cloudAuthEnabled
  ? createAuthClient(authUrl, {
      adapter: BetterAuthReactAdapter({
        fetchOptions: { credentials: 'include' },
      }),
    })
  : null;

 // The default Neon client exposes token(), which returns the short-lived
 // JWT used by the authenticated DJ Cloud API. Keep the React adapter for UI auth.
export const cloudTokenClient = cloudAuthEnabled
  ? createAuthClient(authUrl)
  : null;

export interface CloudUser {
  id: string;
  name?: string;
  email?: string;
}
