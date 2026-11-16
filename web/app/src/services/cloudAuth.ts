import { createAuthClient } from 'better-auth/react';
import { jwtClient } from 'better-auth/client/plugins';

const authUrl = import.meta.env.VITE_NEON_AUTH_URL;

export const cloudAuthEnabled = typeof authUrl === 'string' && authUrl.length > 0;

/**
 * Use the native Better Auth client API against Neon's Managed Auth endpoint.
 * The JWT plugin supplies token() for our authenticated DJ Cloud API requests.
 * We use email/password auth and do not need migration adapters.
 */
export const cloudAuthClient = cloudAuthEnabled
  ? createAuthClient({
      baseURL: authUrl,
      plugins: [jwtClient()],
      fetchOptions: {
        credentials: 'include',
      },
    })
  : null;

export const cloudTokenClient = cloudAuthClient;

export interface CloudUser {
  id: string;
  name?: string;
  email?: string;
}
