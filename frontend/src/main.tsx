import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { AuthProvider } from './auth';
import { registerServiceWorker } from './lib/push';
import '@fontsource/atkinson-hyperlegible/400';
import '@fontsource/atkinson-hyperlegible/700';
import '@fontsource/big-shoulders-display/600';
import '@fontsource/big-shoulders-display/800';
import './styles.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
);

void registerServiceWorker();
