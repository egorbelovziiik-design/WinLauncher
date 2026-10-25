import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import express from 'express';
import { WebSocketServer, WebSocket } from 'ws';
import { app } from './app.js';
import { canAccessServer, verifyToken } from './auth.js';
import { store } from './store.js';
import { engine } from './engine.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT_DIR = path.resolve(__dirname, '..');
const PORT = Number(process.env.PORT || 3000);
const IS_PROD = process.env.NODE_ENV === 'production';

const httpServer = http.createServer(app);
const wss = new WebSocketServer({ noServer: true });

interface WsClientContext {
  ws: WebSocket;
  userId: string;
  subscribedServerId: string | null;
}
const wsClients = new Set<WsClientContext>();

httpServer.on('upgrade', (request, socket, head) => {
  try {
    const parsed = new URL(request.url || '/', 'http://localhost');
    if (parsed.pathname !== '/ws') {
      return;
    }

    const token = parsed.searchParams.get('token') || '';
    const payload = verifyToken(token);
    if (!payload || payload.purpose !== 'session') {
      socket.write('HTTP/1.1 401 Unauthorized\r\n\r\n');
      socket.destroy();
      return;
    }

    wss.handleUpgrade(request, socket, head, (ws) => {
      const ctx: WsClientContext = {
        ws,
        userId: payload.sub,
        subscribedServerId: null,
      };
      wsClients.add(ctx);

      ws.on('message', (raw) => {
        try {
          const msg = JSON.parse(String(raw)) as {
            type: string;
            serverId?: string;
            command?: string;
          };
          const user = store.data.users.find((u) => u.id === ctx.userId);
          if (!user || user.suspended) return;

          if (msg.type === 'subscribe' && msg.serverId) {
            const srv = store.data.servers.find((s) => s.id === msg.serverId);
            if (srv && canAccessServer(user, srv, 'console.read')) {
              ctx.subscribedServerId = srv.id;
              ws.send(
                JSON.stringify({
                  type: 'init',
                  serverId: srv.id,
                  status: srv.status,
                  logs: engine.getLogs(srv.id),
                  metricsHistory: engine.getMetricsHistory(srv.id),
                })
              );
            }
          } else if (msg.type === 'command' && msg.serverId && msg.command) {
            const srv = store.data.servers.find((s) => s.id === msg.serverId);
            if (srv && canAccessServer(user, srv, 'console.send')) {
              engine.executeConsoleCommand(srv, msg.command);
            }
          }
        } catch {
          // ignore
        }
      });

      ws.on('close', () => {
        wsClients.delete(ctx);
      });
    });
  } catch {
    socket.destroy();
  }
});

engine.on('console', ({ serverId, line }: { serverId: string; line: string }) => {
  const payload = JSON.stringify({ type: 'console', serverId, line });
  for (const client of wsClients) {
    if (client.subscribedServerId === serverId && client.ws.readyState === WebSocket.OPEN) {
      client.ws.send(payload);
    }
  }
});

engine.on('status', ({ serverId, status }: { serverId: string; status: string }) => {
  const payload = JSON.stringify({ type: 'status', serverId, status });
  for (const client of wsClients) {
    if (client.ws.readyState === WebSocket.OPEN) {
      client.ws.send(payload);
    }
  }
});

engine.on('metrics', (snapshot: unknown) => {
  const payload = JSON.stringify({ type: 'metrics', ...(snapshot as object) });
  for (const client of wsClients) {
    if (client.ws.readyState === WebSocket.OPEN) {
      client.ws.send(payload);
    }
  }
});

async function bootstrap() {
  if (IS_PROD) {
    const distDir = path.join(ROOT_DIR, 'dist');
    app.use(express.static(distDir));
    app.get('*', (_req, res) => {
      res.sendFile(path.join(distDir, 'index.html'));
    });
  } else {
    const { createServer: createViteServer } = await import('vite');
    const vite = await createViteServer({
      root: ROOT_DIR,
      server: {
        middlewareMode: true,
        allowedHosts: true,
        hmr: {
          server: httpServer,
          path: '/__vite_hmr',
        },
      },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  }

  httpServer.listen(PORT, '0.0.0.0', () => {
    console.log(
      `[WinHost Panel] Сайт и Веб-панель запущены на http://0.0.0.0:${PORT}`
    );
  });
}

bootstrap().catch((err) => {
  console.error('[WinHost] Критическая ошибка запуска:', err);
  process.exit(1);
});
