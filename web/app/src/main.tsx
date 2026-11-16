import React from 'react';
import ReactDOM from 'react-dom/client';
import { App } from './App';
import './index.css';
import './premium-ui.css';
import { ControlPanel } from './components/desktop/ControlPanel';

const isControlPanel = new URLSearchParams(window.location.search).get('control') === '1';

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    {isControlPanel ? <ControlPanel /> : <App />}
  </React.StrictMode>
);
