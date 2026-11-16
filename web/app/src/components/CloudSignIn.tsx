import React, { useState } from 'react';
import { cloudAuthClient } from '../services/cloudAuth';

interface CloudSignInProps {
  onAuthenticated: () => void;
}

export const CloudSignIn: React.FC<CloudSignInProps> = ({ onAuthenticated }) => {
  const [mode, setMode] = useState<'signin' | 'signup'>('signin');
  const [email, setEmail] = useState('');
  const [name, setName] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!cloudAuthClient || busy) return;
    setBusy(true);
    setError(null);
    try {
      const result = mode === 'signup'
        ? await cloudAuthClient.signUp.email({ email: email.trim(), password, name: name.trim() || email.trim().split('@')[0] })
        : await cloudAuthClient.signIn.email({ email: email.trim(), password });
      if (result.error) throw new Error(result.error.message || 'Authentication failed.');
      onAuthenticated();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not sign in to DJ Cloud.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-5" style={{ background: 'radial-gradient(circle at top, #19385c 0%, #080e18 55%, #05080e 100%)' }}>
      <div className="w-full max-w-md rounded-3xl border p-7 shadow-2xl" style={{ background: 'rgba(15,25,41,.94)', borderColor: 'rgba(109,164,230,.25)', color: '#f5f8ff' }}>
        <div className="flex items-center gap-3 mb-6">
          <div className="w-12 h-12 rounded-2xl flex items-center justify-center text-2xl" style={{ background: 'linear-gradient(135deg,#2986dc,#8b5cf6)' }}>♫</div>
          <div>
            <div className="text-xl font-black tracking-wide">DJ Cloud</div>
            <div className="text-xs text-blue-200/70">One account. Every device. One active player.</div>
          </div>
        </div>
        <h1 className="text-lg font-bold mb-1">{mode === 'signup' ? 'Create your DJ account' : 'Sign in to DJ Cloud'}</h1>
        <p className="text-sm text-slate-300 mb-5">Use the same account on your Android phone and computer to sync your playback and devices.</p>
        <form onSubmit={submit} className="space-y-3">
          {mode === 'signup' && (
            <label className="block text-xs font-semibold text-slate-300">
              Display name
              <input value={name} onChange={(e) => setName(e.target.value)} className="mt-1 w-full rounded-xl border px-3 py-3 text-sm outline-none" style={{ background: '#0b1422', borderColor: '#334967', color: 'white' }} placeholder="Your name" autoComplete="name" />
            </label>
          )}
          <label className="block text-xs font-semibold text-slate-300">
            Email
            <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} className="mt-1 w-full rounded-xl border px-3 py-3 text-sm outline-none" style={{ background: '#0b1422', borderColor: '#334967', color: 'white' }} placeholder="you@example.com" autoComplete="email" />
          </label>
          <label className="block text-xs font-semibold text-slate-300">
            Password
            <input type="password" required minLength={8} value={password} onChange={(e) => setPassword(e.target.value)} className="mt-1 w-full rounded-xl border px-3 py-3 text-sm outline-none" style={{ background: '#0b1422', borderColor: '#334967', color: 'white' }} placeholder="At least 8 characters" autoComplete={mode === 'signup' ? 'new-password' : 'current-password'} />
          </label>
          {error && <div className="rounded-xl border border-red-400/30 bg-red-500/10 p-3 text-sm text-red-200">{error}</div>}
          <button type="submit" disabled={busy} className="w-full rounded-xl py-3 font-bold disabled:opacity-50" style={{ background: 'linear-gradient(90deg,#1879d0,#7253d6)', color: 'white' }}>
            {busy ? 'Please wait…' : mode === 'signup' ? 'Create account' : 'Sign in'}
          </button>
        </form>
        <button onClick={() => { setMode(mode === 'signin' ? 'signup' : 'signin'); setError(null); }} className="mt-4 w-full text-center text-sm text-blue-200 hover:text-white">
          {mode === 'signup' ? 'Already have an account? Sign in' : 'New to DJ Cloud? Create an account'}
        </button>
        <div className="mt-5 border-t pt-4 text-[11px] text-slate-400" style={{ borderColor: '#25364c' }}>
          Your local audio files stay on each device. Cloud playback handoff works for online tracks and shared tracks available to both devices.
        </div>
      </div>
    </div>
  );
};
