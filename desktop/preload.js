const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('djDesktop', {
  openAudioFiles: () => ipcRenderer.invoke('dialog-open-audio'),
  saveRecording: (name) => ipcRenderer.invoke('dialog-save-recording', name),
  loadState: () => ipcRenderer.invoke('load-state'),
  saveState: (state) => ipcRenderer.invoke('save-state', state),
  getRecordingsFolder: () => ipcRenderer.invoke('get-recordings-folder'),
  openRecordingsFolder: () => ipcRenderer.invoke('open-recordings-folder'),
  onMediaKey: (callback) => ipcRenderer.on('media-key', (_event, key) => callback(key)),
  onShortcut: (callback) => ipcRenderer.on('desktop-shortcut', (_event, key) => callback(key)),
  onBeforeClose: (callback) => ipcRenderer.on('app-before-close', callback)
});
