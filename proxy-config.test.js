// @vitest-environment node
import { createServer as createHttpServer } from 'node:http';
import { readFileSync } from 'node:fs';
import { afterEach, expect, it, vi } from 'vitest';
import { createServer } from 'vite';
import configure from './vite.config.js';

const config = (mode) => configure({ mode, command: 'serve' });
afterEach(() => vi.unstubAllEnvs());

it('keeps normal API development on 5173 -> 8080', () => {
  vi.stubEnv('API_PROXY_TARGET', '');
  const normal = config('api');
  expect(normal.server.port).toBe(5173);
  expect(normal.server.strictPort).toBe(true);
  expect(normal.server.proxy['/api'].target).toBe('http://localhost:8080');
});

it('pairs demo frontend with the backend demo port, independent of the normal target', () => {
  vi.stubEnv('API_PROXY_TARGET', 'http://localhost:9999');
  vi.stubEnv('IOT_DEMO_API_PROXY_TARGET', '');
  const demo = config('iot-demo');
  expect(demo.server.port).toBe(5174);
  expect(demo.server.strictPort).toBe(true);
  expect(demo.server.proxy['/api'].target).toBe('http://localhost:8081');
  expect(demo.cacheDir).not.toBe(config('api').cacheDir);
});

it('respects the ordinary development proxy override', () => {
  vi.stubEnv('API_PROXY_TARGET', 'http://127.0.0.1:9090');
  expect(config('api').server.proxy['/api'].target).toBe(
    'http://127.0.0.1:9090',
  );
});

it('provides a runnable demo script', () => {
  const { scripts } = JSON.parse(
    readFileSync(new URL('./package.json', import.meta.url)),
  );
  expect(scripts['dev:iot-demo']).toBe('vite --mode iot-demo');
});

it('forwards demo API paths, body, auth and idempotency headers and preserves backend errors', async () => {
  const received = [];
  const upstream = createHttpServer(async (req, res) => {
    let body = '';
    for await (const chunk of req) body += chunk;
    received.push({
      path: req.url,
      method: req.method,
      headers: req.headers,
      body,
    });
    res.writeHead(409, { 'Content-Type': 'application/json' });
    res.end(
      JSON.stringify({
        error: {
          code: 'OPTIMISTIC_LOCK_CONFLICT',
          message: 'Conflict',
          details: [],
          requestId: 'test',
          timestamp: '2026-11-26T00:00:00Z',
        },
      }),
    );
  });
  let vite;
  try {
    await new Promise((resolve) => upstream.listen(0, '127.0.0.1', resolve));
    const target = `http://127.0.0.1:${upstream.address().port}`;
    vi.stubEnv('IOT_DEMO_API_PROXY_TARGET', target);
    const demo = config('iot-demo');
    expect(demo.server.proxy['/api'].target).toBe(target);
    vite = await createServer({
      ...demo,
      configFile: false,
      logLevel: 'silent',
      server: { ...demo.server, port: 0, host: '127.0.0.1', watch: null },
    });
    await vite.listen();
    const response = await fetch(
      `http://127.0.0.1:${vite.httpServer.address().port}/api/pickups?attempt=1`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: 'Bearer test-token',
          'Idempotency-Key': 'test-key',
        },
        body: JSON.stringify({ vehicleId: 'bike_001' }),
        signal: AbortSignal.timeout(5000),
      },
    );
    expect(response.status).toBe(409);
    expect((await response.json()).error.code).toBe('OPTIMISTIC_LOCK_CONFLICT');
    expect(received).toHaveLength(1);
    expect(received[0]).toMatchObject({
      path: '/api/pickups?attempt=1',
      method: 'POST',
      body: '{"vehicleId":"bike_001"}',
      headers: {
        authorization: 'Bearer test-token',
        'idempotency-key': 'test-key',
      },
    });
  } finally {
    await vite?.close();
    upstream.closeAllConnections();
    await new Promise((resolve, reject) =>
      upstream.close((error) => (error ? reject(error) : resolve())),
    );
  }
});
