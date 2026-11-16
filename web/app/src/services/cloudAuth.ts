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

export interface CloudUser {
  id: string;
  name?: string;
  email?: string;
}
