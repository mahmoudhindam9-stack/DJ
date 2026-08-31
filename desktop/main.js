const { app, BrowserWindow, ipcMain, dialog, globalShortcut, shell } = require('electron');
const path = require('path');
const fs = require('fs');

let mainWindow;
const stateFile = path.join(app.getPath('userData'), 'dj-desktop-state.json');

function loadState() {
  try { return JSON.parse(fs.readFileSync(stateFile, 'utf8')); } catch { return {}; }
}
function saveState(state) {
  fs.mkdirSync(path.dirname(stateFile), { recursive: true });
  fs.writeFileSync(stateFile, JSON.stringify(state, null, 2), 'utf8');
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1480,
    height: 920,
    minWidth: 1100,
    minHeight: 720,
    backgroundColor: '#090b10',
    title: 'DJ Workstation',
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  });
  mainWindow.loadFile(path.join(__dirname, 'index.html'));
  mainWindow.once('ready-to-show', () => mainWindow.show());
  mainWindow.on('close', () => {
    if (!mainWindow.isDestroyed()) mainWindow.webContents.send('app-before-close');
  });
}

app.whenReady().then(() => {
  createWindow();
  globalShortcut.register('MediaPlayPause', () => mainWindow?.webContents.send('media-key', 'playpause'));
  globalShortcut.register('MediaNextTrack', () => mainWindow?.webContents.send('media-key', 'next'));
  globalShortcut.register('MediaPreviousTrack', () => mainWindow?.webContents.send('media-key', 'prev'));
  globalShortcut.register('CommandOrControl+Shift+R', () => mainWindow?.webContents.send('desktop-shortcut', 'record'));

  ipcMain.handle('dialog-open-audio', async () => {
    const result = await dialog.showOpenDialog(mainWindow, {
      properties: ['openFile', 'multiSelections'],
      filters: [{ name: 'Audio', extensions: ['mp3','wav','flac','m4a','aac','ogg','opus'] }]
    });
    return result.canceled ? [] : result.filePaths;
  });
  ipcMain.handle('dialog-save-recording', async (_event, defaultName) => {
    const result = await dialog.showSaveDialog(mainWindow, {
      defaultPath: defaultName || 'DJ-Recording.wav',
      filters: [{ name: 'WAV Audio', extensions: ['wav'] }]
    });
    return result.canceled ? null : result.filePath;
  });
  ipcMain.handle('load-state', () => loadState());
  ipcMain.handle('save-state', (_event, state) => { saveState(state); return true; });
  ipcMain.handle('open-recordings-folder', () => {
    const dir = path.join(app.getPath('music'), 'DJ Recordings');
    fs.mkdirSync(dir, { recursive: true });
    return shell.openPath(dir);
  });
  ipcMain.handle('get-recordings-folder', () => {
    const dir = path.join(app.getPath('music'), 'DJ Recordings');
    fs.mkdirSync(dir, { recursive: true });
    return dir;
  });

  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
});

app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
app.on('will-quit', () => globalShortcut.unregisterAll());
