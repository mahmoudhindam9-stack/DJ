import test from 'node:test';
import assert from 'node:assert/strict';
import {
  NEON_CLOUD_CONFIG,
  NEON_CANONICAL_SCHEMA_SQL,
  createSignedSessionToken,
  verifySessionToken,
  neonCloudStore,
} from './neon-cloud.js';

test('Neon Cloud config uses exact DJ-Cloud project parameters without Supabase', () => {
  assert.equal(NEON_CLOUD_CONFIG.projectName, 'DJ-Cloud');
  assert.equal(NEON_CLOUD_CONFIG.projectId, 'dark-fire-99620103');
  assert.equal(NEON_CLOUD_CONFIG.organizationId, 'org-jolly-sun-87195223');
  assert.equal(NEON_CLOUD_CONFIG.branchId, 'br-spring-term-baakf0qx');
  assert.equal(NEON_CLOUD_CONFIG.database, 'neondb');
  assert.match(NEON_CLOUD_CONFIG.authBaseUrl, /ep-morning-bird-bad5xn0x\.neonauth\.c-8\.eu-central-1\.aws\.neon\.tech/);
  assert.match(NEON_CLOUD_CONFIG.realtimeUrl, /ep-morning-bird-bad5xn0x\.rt\.c-8\.eu-central-1\.aws\.neon\.tech/);
  assert.match(NEON_CANONICAL_SCHEMA_SQL, /CREATE TABLE IF NOT EXISTS users/);
  assert.match(NEON_CANONICAL_SCHEMA_SQL, /CREATE TABLE IF NOT EXISTS user_playback_state/);
  assert.doesNotMatch(NEON_CANONICAL_SCHEMA_SQL, /supabase/i);
});

test('Email/Password & Google Authentication + Strict User Isolation between User A and User B', async () => {
  neonCloudStore.resetForTests();
  const emailA = `usera_${Date.now()}@example.com`;
  const emailB = `userb_${Date.now()}@example.com`;

  const authA = await neonCloudStore.signUpEmail({
    email: emailA,
    password: 'StrongPass123!',
    displayName: 'User Alpha',
  });
  const authB = await neonCloudStore.signUpEmail({
    email: emailB,
    password: 'StrongPass456!',
    displayName: 'User Beta',
  });

  assert.notEqual(authA.userId, authB.userId);
  assert.ok(authA.sessionToken);
  assert.ok(authB.sessionToken);

  // Sign-in with email/password works for User A
  const signInA = await neonCloudStore.signInEmail({
    email: emailA,
    password: 'StrongPass123!',
  });
  assert.equal(signInA.userId, authA.userId);

  // Wrong password fails
  await assert.rejects(
    () => neonCloudStore.signInEmail({ email: emailA, password: 'WrongPass!' }),
    /Invalid email or password/
  );

  // Write tracks, playlists, favorites, and settings for User A
  await neonCloudStore.updateUserState(authA.userId, {
    tracks: [{ id: 'track_a_1', title: 'Alpha Exclusive Song', artist: 'Artist A' }],
    playlists: [{ id: 'pl_a_1', name: 'Alpha Playlist', songIds: ['track_a_1'] }],
    favoriteIds: ['track_a_1'],
    languageMode: 'ar',
  });

  // Write distinct tracks, playlists, favorites, and settings for User B
  await neonCloudStore.updateUserState(authB.userId, {
    tracks: [{ id: 'track_b_1', title: 'Beta Secret Track', artist: 'Artist B' }],
    playlists: [{ id: 'pl_b_1', name: 'Beta Playlist', songIds: ['track_b_1'] }],
    favoriteIds: ['track_b_1'],
    languageMode: 'en',
  });

  const stateA = await neonCloudStore.getUserState(authA.userId);
  const stateB = await neonCloudStore.getUserState(authB.userId);

  // User A sees ONLY User A's data
  assert.equal(stateA.tracks.length, 1);
  assert.equal(stateA.tracks[0].id, 'track_a_1');
  assert.equal(stateA.playlists[0].name, 'Alpha Playlist');
  assert.deepEqual(stateA.favoriteIds, ['track_a_1']);
  assert.equal(stateA.settings.languageMode, 'ar');

  // User B sees ONLY User B's data
  assert.equal(stateB.tracks.length, 1);
  assert.equal(stateB.tracks[0].id, 'track_b_1');
  assert.equal(stateB.playlists[0].name, 'Beta Playlist');
  assert.deepEqual(stateB.favoriteIds, ['track_b_1']);
  assert.equal(stateB.settings.languageMode, 'en');

  // Cross-user access is strictly rejected with 403 Forbidden
  assert.throws(
    () =>
      neonCloudStore.resolveAuthorizedUserId(
        { headers: { authorization: `Bearer ${authA.sessionToken}` } },
        authB.userId
      ),
    /Forbidden: cross-user data access denied/
  );

  // Google Sign-In works
  const googleAuth = await neonCloudStore.signInGoogle({
    email: `google_${Date.now()}@gmail.com`,
    displayName: 'Google DJ User',
  });
  assert.equal(googleAuth.authProvider, 'google');
});

test('Android <-> Web Single Active Audio Output Arbitration and WebView Handoff', async () => {
  neonCloudStore.resetForTests();
  const email = `sync_${Date.now()}@example.com`;
  const auth = await neonCloudStore.signUpEmail({
    email,
    password: 'SyncPass123!',
    displayName: 'Sync User',
  });

  // 1. Android claims active audio output
  const androidClaim = await neonCloudStore.updatePlaybackState(auth.userId, {
    activeDeviceId: 'android_pixel_9',
    activeDevicePlatform: 'android',
    currentTrackId: 'song_101',
    currentTrackTitle: 'Oriental Beats',
    positionMs: 42000,
    isPlaying: true,
  });
  assert.equal(androidClaim.playbackState.activeDevicePlatform, 'android');
  assert.equal(androidClaim.playbackState.activeDeviceId, 'android_pixel_9');
  assert.equal(androidClaim.playbackState.isPlaying, true);

  // 2. Web claims active audio output -> single output switches to 'web' at the synced position
  const webClaim = await neonCloudStore.updatePlaybackState(auth.userId, {
    activeDeviceId: 'web_chrome_1',
    activeDevicePlatform: 'web',
    currentTrackId: 'song_101',
    currentTrackTitle: 'Oriental Beats',
    positionMs: 45500,
    isPlaying: true,
  });
  assert.equal(webClaim.playbackState.activeDevicePlatform, 'web');
  assert.equal(webClaim.playbackState.activeDeviceId, 'web_chrome_1');
  assert.equal(webClaim.playbackState.positionMs, 45500);
  assert.equal(webClaim.outputTransferred, true);

  // 3. Verify signed session token for WebView handoff
  const token = createSignedSessionToken({
    userId: auth.userId,
    email: auth.email,
    displayName: auth.displayName,
    authProvider: 'email',
  });
  const verified = verifySessionToken(token);
  assert.equal(verified.userId, auth.userId);
  assert.equal(verified.email, auth.email);
});
