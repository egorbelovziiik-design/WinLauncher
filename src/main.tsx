import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './index.css';

// Disable the browser context menu in the packaged app (keeps dev preview usable).
if (typeof window !== 'undefined' && '__TAURI_INTERNALS__' in window) {
  window.addEventListener('contextmenu', (e) => {
    const target = e.target as HTMLElement;
    if (!target.closest('input, textarea')) e.preventDefault();
  });
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
