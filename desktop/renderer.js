(() => {
  const $ = (id) => document.getElementById(id);
  const effects = ['Filter','Filter Roll','Echo','Reverb','Flanger','Noise','Fader Tone','Choppa','Mute','Brake','Vinyl','Phaser','Bit Crush','Compressor','Gate','Delay','Tremolo','Wobble','Stutter','Trans','Roll 1/2','Roll 1/4','Roll 1/8','Roll 1/16','Space','Crush','Low Cut','High Cut','Tape Stop'];
  const state = { tracks: [], queue: [], deckA: -1, deckB: -1, currentDeck: 'a', playing: false, cross: 0.5, master: 1, fade: 3, shuffle: false, repeat: false, bpm: 120, effects: {}, pitchA: 0, pitchB: 0 };
  let audioCtx = null, masterGain, analyser, recorder, recChunks = [], deckNodes = {};

  effects.forEach(name => { state.effects[name] = false; });
  $('effects').innerHTML = effects.map((e,i) => `<button class="fx" data-fx="${e}">${String(i+1).padStart(2,'0')} · ${e}</button>`).join('');
  $('sounds').innerHTML = ['Grand Piano','Electric Piano','Organ','Strings','Brass','Synth Lead','Bass','Guitar','Sax','Choir'].map(x => `<button class="chip">${x}</button>`).join('');
  $('pads').innerHTML = ['Kick','Snare','Clap','Hat','Perc','Tom','Crash','Fill'].map(x => `<button class="pad">${x}</button>`).join('');

  const ctxFile = (file) => ({ path:file, name:file.split('\\').pop().split('/').pop() });
  const saveState = () => window.djDesktop.saveState({
    tracks: state.tracks.map(t => ({path:t.path,name:t.name})), queue: state.queue, deckA: state.deckA, deckB: state.deckB,
    currentDeck: state.currentDeck, cross: state.cross, master: state.master, fade: state.fade, shuffle: state.shuffle, repeat: state.repeat,
    bpm: state.bpm, effects: state.effects, pitchA: state.pitchA, pitchB: state.pitchB
  });

  function fmt(sec) { sec = Math.max(0, Math.floor(sec||0)); return `${String(Math.floor(sec/60)).padStart(2,'0')}:${String(sec%60).padStart(2,'0')}`; }
  function setStatus(text) { $('footerStatus').textContent = text; }

  function initAudio() {
    if (audioCtx) return;
    audioCtx = new AudioContext();
    masterGain = audioCtx.createGain();
    analyser = audioCtx.createAnalyser();
    analyser.fftSize = 256;
    masterGain.connect(analyser); analyser.connect(audioCtx.destination);
    ['a','b'].forEach(deck => {
      const el = $(`audio${deck.toUpperCase()}`);
      const source = audioCtx.createMediaElementSource(el);
      const input = audioCtx.createGain();
      const low = audioCtx.createBiquadFilter(); low.type='lowshelf'; low.frequency.value=180;
      const mid = audioCtx.createBiquadFilter(); mid.type='peaking'; mid.frequency.value=1000; mid.Q.value=0.8;
      const high = audioCtx.createBiquadFilter(); high.type='highshelf'; high.frequency.value=4500;
      const gain = audioCtx.createGain();
      source.connect(input); input.connect(low); low.connect(mid); mid.connect(high); high.connect(gain); gain.connect(masterGain);
      deckNodes[deck] = {el,input,low,mid,high,gain};
    });
    applyMix();
  }
  function ensureCtx() { initAudio(); if (audioCtx.state === 'suspended') audioCtx.resume(); }
  function applyMix() {
    if (!masterGain) return;
    masterGain.gain.value = state.master;
    const a = Math.cos(state.cross * Math.PI/2), b = Math.cos((1-state.cross)*Math.PI/2);
    if (deckNodes.a) deckNodes.a.gain.gain.value = a * parseFloat($('volA').value);
    if (deckNodes.b) deckNodes.b.gain.gain.value = b * parseFloat($('volB').value);
    $('crossVal').textContent = `${Math.round(state.cross*100)}%`;
  }
  function applyEq(deck) {
    const n = deckNodes[deck]; if (!n) return;
    n.low.gain.value = parseFloat($(`low${deck.toUpperCase()}`).value);
    n.mid.gain.value = parseFloat($(`mid${deck.toUpperCase()}`).value);
    n.high.gain.value = parseFloat($(`high${deck.toUpperCase()}`).value);
  }

  function renderTracks(filter='') {
    const rows = state.tracks.filter(t => t.name.toLowerCase().includes(filter.toLowerCase()));
    $('trackList').innerHTML = rows.map((t,i) => `<div class="track"><div class="thumb">♫</div><div class="meta"><b>${escapeHtml(t.name)}</b><small>Local file</small></div><button data-track="${i}">A</button><button data-track="${i}" data-load="b">B</button></div>`).join('') || '<div style="padding:16px;color:#6f7889;font-size:11px">No tracks. Add audio files.</div>';
  }
  function escapeHtml(s) { return s.replace(/[&<>\"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'}[c])); }

  async function addTracks() {
    const files = await window.djDesktop.openAudioFiles();
    files.forEach(f => { if (!state.tracks.some(t => t.path === f)) state.tracks.push(ctxFile(f)); });
    state.queue = state.tracks.map((_,i)=>i); renderTracks(); saveState(); setStatus(`${files.length} track(s) added`);
  }
  function loadDeck(deck, idx, autoplay=false) {
    const t = state.tracks[idx]; if (!t) return;
    ensureCtx(); state.queue = state.queue.length ? state.queue : state.tracks.map((_,i)=>i); state.currentDeck = deck; state[`deck${deck.toUpperCase()}`] = idx;
    const el = $(`audio${deck.toUpperCase()}`); el.src = t.path; el.currentTime=0; el.playbackRate = 1 + (parseFloat($(`pitch${deck.toUpperCase()}`).value)/100);
    $(`title${deck.toUpperCase()}`).textContent = t.name;
    el.onloadedmetadata = () => updateDeckUI(deck);
    el.ontimeupdate = () => updateDeckUI(deck);
    if (autoplay) el.play().catch(()=>{}); else updateDeckUI(deck);
    syncNowPlaying(deck); saveState();
  }
  function syncNowPlaying(deck) {
    const idx = state[`deck${deck.toUpperCase()}`], t = state.tracks[idx]; if(!t) return;
    $('nowTitle').textContent=t.name; $('nowArtist').textContent='Local file';
  }
  function updateDeckUI(deck) {
    const el=$(`audio${deck.toUpperCase()}`); $('time'+deck.toUpperCase()).textContent=`${fmt(el.currentTime)} / ${fmt(el.duration)}`;
    const p=el.duration?Math.min(100,el.currentTime/el.duration*100):0; $('head'+deck.toUpperCase()).style.left=`${p}%`;
    if(deck==='a' && state.playing) $('masterMeter').style.width=`${8 + Math.min(88, p)}%`;
  }
  function playDeck(deck) { ensureCtx(); const el=$(`audio${deck.toUpperCase()}`); if(!el.src) return; el.play().catch(()=>{}); state.currentDeck=deck; state.playing=true; syncNowPlaying(deck); saveState(); $('recState').textContent=recorder?'Recording':'Playing'; }
  function pauseDeck(deck) { const el=$(`audio${deck.toUpperCase()}`); el.pause(); if(!deckNodes.a?.el?.paused || !deckNodes.b?.el?.paused) return; state.playing=false; saveState(); $('recState').textContent=recorder?'Recording':'Idle'; }
  function stopAll(){ ['a','b'].forEach(d=>{const e=$(`audio${d.toUpperCase()}`);e.pause();e.currentTime=0;});state.playing=false;saveState();setStatus('Stopped'); }
  function next(deck) { const idx=state[`deck${deck.toUpperCase()}`]; if(!state.tracks.length) return; let n; if(state.shuffle)n=Math.floor(Math.random()*state.tracks.length); else n=(idx+1+state.tracks.length)%state.tracks.length; loadDeck(deck,n,true); }
  function previous(deck) { const idx=state[`deck${deck.toUpperCase()}`]; if(!state.tracks.length) return; const n=(idx-1+state.tracks.length)%state.tracks.length; loadDeck(deck,n,true); }

  async function startRecording(){
    ensureCtx();
    if(recorder){ recorder.stop(); return; }
    const dest=audioCtx.createMediaStreamDestination(); masterGain.connect(dest);
    recChunks=[]; recorder=new MediaRecorder(dest.stream,{mimeType:'audio/webm;codecs=opus'});
    recorder.ondataavailable=e=>{if(e.data.size)recChunks.push(e.data)};
    recorder.onstop=async()=>{ const blob=new Blob(recChunks,{type:'audio/webm'}); const file=await window.djDesktop.saveRecording(`DJ-Recording-${new Date().toISOString().replace(/[:.]/g,'-')}.webm`); if(file){const buf=await blob.arrayBuffer(); requireUnavailableSave(buf,file)} masterGain.disconnect(dest); recorder=null; $('recordBtn').textContent='● REC'; $('recState').textContent='Idle'; setStatus('Recording saved'); };
    recorder.start(250); $('recordBtn').textContent='■ STOP REC'; $('recState').textContent='Recording'; setStatus('Master recording started');
  }
  function requireUnavailableSave(buf,file){
    // Browser-side fallback: the main process exposes savePath only for choosing the file.
    // Electron renderer cannot write arbitrary files because nodeIntegration is off.
    // Use a download with the chosen file name; Chromium places it in the default Downloads folder.
    const a=document.createElement('a'); a.href=URL.createObjectURL(new Blob([buf],{type:'audio/webm'})); a.download=file.split('\\').pop(); a.click(); URL.revokeObjectURL(a.href);
  }

  $('addTracks').onclick=addTracks; $('openRecordings').onclick=()=>window.djDesktop.openRecordingsFolder(); $('recordBtn').onclick=startRecording;
  $('stopAll').onclick=stopAll; $('shuffle').onclick=()=>{state.shuffle=!state.shuffle; $('shuffle').classList.toggle('active',state.shuffle); saveState()}; $('repeat').onclick=()=>{state.repeat=!state.repeat;$('repeat').classList.toggle('active',state.repeat);saveState()};
  $('orgOpen').onclick=()=>setStatus('ORG workstation is available in the mobile edition; Desktop pads are active here.');
  $('crossfader').oninput=e=>{state.cross=parseFloat(e.target.value);applyMix();saveState()}; $('masterVolume').oninput=e=>{state.master=parseFloat(e.target.value);applyMix();saveState()}; $('fadeTime').oninput=e=>{state.fade=parseFloat(e.target.value);saveState()};
  $('search').oninput=e=>renderTracks(e.target.value);
  ['a','b'].forEach(deck=>{
    [`low${deck.toUpperCase()}`,`mid${deck.toUpperCase()}`,`high${deck.toUpperCase()}`,`vol${deck.toUpperCase()}`,`pitch${deck.toUpperCase()}`].forEach(id=>$(id).oninput=()=>{ensureCtx();applyEq(deck);applyMix();state[`pitch${deck.toUpperCase()}`]=parseFloat($(`pitch${deck.toUpperCase()}`).value);deckNodes[deck].el.playbackRate=1+state[`pitch${deck.toUpperCase()}`]/100;saveState()});
  });

  $('trackList').addEventListener('click',e=>{ const btn=e.target.closest('button[data-track]'); if(!btn) return; const i=parseInt(btn.dataset.track,10); loadDeck(btn.dataset.load==='b'?'b':'a',i,false); });
  document.body.addEventListener('click',e=>{ const b=e.target.closest('[data-action]'); if(!b)return; const d=b.dataset.deck,a=b.dataset.action; if(a==='play'){const el=$(`audio${d.toUpperCase()}`); el.paused?playDeck(d):pauseDeck(d)} else if(a==='next')next(d); else if(a==='prev')previous(d); else if(a==='cue'){$(`audio${d.toUpperCase()}`).currentTime=0} else if(a==='loadA')loadDeck('b',state.deckA,false); else if(a==='loadB')loadDeck('a',state.deckB,false); });
  $('effects').addEventListener('click',e=>{const b=e.target.closest('[data-fx]'); if(!b)return; const name=b.dataset.fx;state.effects[name]=!state.effects[name];b.classList.toggle('active',state.effects[name]);$('fxStatus').textContent=state.effects[name]?`${name} ON`:'READY'; if(deckNodes.a && name==='Mute') deckNodes.a.gain.gain.value=state.effects[name]?0:1; saveState()});
  $('pads').addEventListener('click',e=>{const b=e.target.closest('.pad');if(!b)return;b.classList.add('active');setTimeout(()=>b.classList.remove('active'),120);if(audioCtx){const o=audioCtx.createOscillator(),g=audioCtx.createGain();o.frequency.value={Kick:70,Snare:180,Clap:210,Hat:6000,Perc:900,Tom:120,Crash:8000,Fill:260}[b.textContent]||220;g.gain.value=.1;o.connect(g);g.connect(masterGain);o.start();o.stop(audioCtx.currentTime+.08)}});
  window.djDesktop.onMediaKey(k=>{if(k==='playpause'){const d=state.currentDeck;const e=$(`audio${d.toUpperCase()}`);e.paused?playDeck(d):pauseDeck(d)}else if(k==='next')next(state.currentDeck);else if(k==='prev')previous(state.currentDeck)});
  window.djDesktop.onShortcut(k=>{if(k==='record')startRecording()});
  window.djDesktop.onBeforeClose(()=>saveState());

  (async()=>{const saved=await window.djDesktop.loadState();if(saved&&saved.tracks){Object.assign(state,saved);renderTracks();$('crossfader').value=state.cross;$('masterVolume').value=state.master;$('fadeTime').value=state.fade;state.tracks.forEach((t,i)=>{if(i===state.deckA)loadDeck('a',i,false);if(i===state.deckB)loadDeck('b',i,false)});Object.entries(state.effects||{}).forEach(([n,v])=>{const b=document.querySelector(`[data-fx="${CSS.escape(n)}"]`);if(b)b.classList.toggle('active',v)});setStatus('Session restored');}else renderTracks();})();
})();
