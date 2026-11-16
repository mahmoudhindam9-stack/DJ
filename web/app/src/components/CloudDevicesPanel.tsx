import React, { useState } from 'react';
import { Cloud, Laptop, Smartphone, Radio, RefreshCw, LogOut, Play, Check, CircleDot } from 'lucide-react';
import type { CloudDevice, CloudPlaybackState } from '../services/cloudSync';

interface CloudDevicesPanelProps {
  deviceId: string;
  devices: CloudDevice[];
  state: CloudPlaybackState | null;
  status: string;
  error: string | null;
  onTakeControl: () => Promise<void>;
  onRefresh: () => Promise<unknown>;
  onSignOut: () => Promise<void>;
}

export const CloudDevicesPanel: React.FC<CloudDevicesPanelProps> = ({
  deviceId, devices, state, status, error, onTakeControl, onRefresh, onSignOut
}) => {
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const activeId = state?.active_device_id || null;
  const active = devices.find((device) => device.device_id === activeId);
  const current = devices.find((device) => device.device_id === deviceId);

  const run = async (action: () => Promise<unknown>) => {
    setBusy(true);
    try { await action(); } finally { setBusy(false); }
  };

  return (
    <div className="fixed right-3 top-[68px] z-[80] sm:right-5">
      <button
        onClick={() => setOpen(!open)}
        className="flex items-center gap-2 rounded-full border px-3 py-2 text-xs font-bold shadow-xl backdrop-blur-xl"
        style={{ background: 'rgba(10,20,34,.94)', borderColor: 'rgba(83,153,230,.45)', color: '#eaf4ff' }}
      >
        <Cloud className="h-4 w-4 text-sky-300" />
        <span>Devices</span>
        <span className="h-2 w-2 rounded-full" style={{ background: status === 'live' ? '#22c55e' : status === 'error' ? '#ef4444' : '#f59e0b' }} />
      </button>

      {open && (
        <div className="mt-2 w-[min(340px,calc(100vw-24px))] rounded-2xl border p-3 shadow-2xl backdrop-blur-2xl" style={{ background: 'rgba(9,17,29,.97)', borderColor: 'rgba(83,153,230,.35)', color: '#edf5ff' }}>
          <div className="mb-3 flex items-center justify-between">
            <div>
              <div className="text-sm font-bold">Your devices</div>
              <div className="text-[11px] text-slate-400">{status === 'live' ? 'Live sync connected' : status === 'connecting' ? 'Connecting…' : status === 'offline' ? 'Offline' : 'Sync unavailable'}</div>
            </div>
            <button disabled={busy} onClick={() => void run(onRefresh)} className="rounded-lg p-2 hover:bg-white/10 disabled:opacity-40" title="Refresh devices"><RefreshCw className={'h-4 w-4 ' + (busy ? 'animate-spin' : '')} /></button>
          </div>

          <div className="space-y-2">
            {devices.map((device) => {
              const isCurrent = device.device_id === deviceId;
              const isOutput = device.device_id === activeId;
              const Icon = device.platform === 'android' ? Smartphone : device.platform === 'desktop' ? Laptop : Radio;
              return (
                <div key={device.device_id} className="flex items-center gap-2 rounded-xl border p-2.5" style={{ borderColor: isOutput ? 'rgba(34,197,94,.55)' : 'rgba(100,116,139,.25)', background: isOutput ? 'rgba(34,197,94,.08)' : 'rgba(255,255,255,.025)' }}>
                  <Icon className="h-4 w-4 shrink-0 text-slate-300" />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-1 text-xs font-semibold">
                      <span className="truncate">{device.name}</span>
                      {isCurrent && <span className="text-[10px] text-sky-300">(This device)</span>}
                    </div>
                    <div className="mt-0.5 flex items-center gap-1 text-[10px]" style={{ color: isOutput ? '#86efac' : device.online ? '#cbd5e1' : '#64748b' }}>
                      {isOutput ? <><CircleDot className="h-3 w-3" /> Playing output</> : device.online ? 'Online' : 'Last seen'}
                    </div>
                  </div>
                  {isOutput && <Check className="h-4 w-4 text-green-400" />}
                </div>
              );
            })}
            {devices.length === 0 && <div className="rounded-xl border border-dashed p-4 text-center text-xs text-slate-400">No other devices yet. Sign in with this account on your phone or computer.</div>}
          </div>

          {active && active.device_id !== deviceId && (
            <button disabled={busy} onClick={() => void run(onTakeControl)} className="mt-3 flex w-full items-center justify-center gap-2 rounded-xl py-2.5 text-xs font-bold disabled:opacity-40" style={{ background: 'linear-gradient(90deg,#1678c8,#6255d8)', color: 'white' }}>
              <Play className="h-4 w-4" /> Move playback to this device
            </button>
          )}
          {!active && current && (
            <button disabled={busy} onClick={() => void run(onTakeControl)} className="mt-3 w-full rounded-xl py-2.5 text-xs font-bold disabled:opacity-40" style={{ background: '#1678c8', color: 'white' }}>
              Enable playback on this device
            </button>
          )}
          {error && <div className="mt-2 rounded-lg bg-red-500/10 p-2 text-[11px] text-red-200">{error}</div>}

          <button onClick={() => void run(onSignOut)} className="mt-3 flex w-full items-center justify-center gap-2 rounded-xl border py-2 text-xs text-slate-300 hover:bg-white/5" style={{ borderColor: 'rgba(100,116,139,.25)' }}>
            <LogOut className="h-3.5 w-3.5" /> Sign out
          </button>
        </div>
      )}
    </div>
  );
};
