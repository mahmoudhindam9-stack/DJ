import { createAuthClient } from '@neon/auth';

const authUrl = import.meta.env.VITE_NEON_AUTH_URL;

export const cloudAuthEnabled = typeof authUrl === 'string' && authUrl.length > 0;
export const cloudAuthClient = cloudAuthEnabled ? createAuthClient(authUrl) : null;

export interface CloudUser {
  id: string;
  name?: string;
  email?: string;
}
