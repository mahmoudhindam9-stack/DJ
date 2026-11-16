import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const appDir = path.resolve(__dirname, '..');

// Default host and port as required by AI Studio environment
let port = '3000';
let host = '0.0.0.0';

// Parse arguments (handling both positional and flagged args that npm/supervisor might forward)
const rawArgs = process.argv.slice(2);
for (let i = 0; i < rawArgs.length; i++) {
  const arg = rawArgs[i];
  if ((arg === '--port' || arg === '-p') && rawArgs[i + 1]) {
    port = rawArgs[++i];
  } else if ((arg === '--host' || arg === '-h') && rawArgs[i + 1]) {
    host = rawArgs[++i];
  } else if (/^\d+$/.test(arg)) {
    // If passed as bare number like "3000"
    port = arg;
  } else if (arg === '0.0.0.0' || arg === 'localhost' || arg === '127.0.0.1') {
    // If passed as bare IP/hostname like "0.0.0.0"
    host = arg;
  }
}

// Locate local or workspace-hoisted vite binary
const localViteBin = path.resolve(appDir, 'node_modules', '.bin', 'vite');
const rootViteBin = path.resolve(appDir, '..', 'node_modules', '.bin', 'vite');
const viteBin = fs.existsSync(localViteBin) ? localViteBin : rootViteBin;

const viteProcess = spawn(
  viteBin,
  ['--host', host, '--port', port],
  {
    cwd: appDir,
    stdio: 'inherit',
    env: { ...process.env, PORT: port, HOST: host },
  }
);

viteProcess.on('exit', (code, signal) => {
  if (signal) {
    process.kill(process.pid, signal);
  } else {
    process.exit(code ?? 0);
  }
});

process.on('SIGINT', () => viteProcess.kill('SIGINT'));
process.on('SIGTERM', () => viteProcess.kill('SIGTERM'));
