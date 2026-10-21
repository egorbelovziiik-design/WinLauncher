import { StrictMode, Component, type ErrorInfo, type ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import '@fontsource-variable/manrope';
import './styles.css';
import App from './App';
const queryClient = new QueryClient({ defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } } });
class ErrorBoundary extends Component<{ children: ReactNode }, { error: string | null }> {
  state = { error: null as string | null };
  static getDerivedStateFromError(error: Error) { return { error: error.message }; }
  componentDidCatch(error: Error, info: ErrorInfo) { console.error('WinLauncher UI:', error.message, info.componentStack); }
  render() { return this.state.error ? <div className="boot-screen"><h1>WinLauncher</h1><h2>Ошибка интерфейса</h2><p>{this.state.error}</p><button className="button button-primary" onClick={() => window.location.reload()}>Перезапустить интерфейс</button><small>Игровые файлы не будут изменены.</small></div> : this.props.children; }
}
createRoot(document.getElementById('root')!).render(<StrictMode><ErrorBoundary><QueryClientProvider client={queryClient}><App /></QueryClientProvider></ErrorBoundary></StrictMode>);
