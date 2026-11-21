import { afterEach, expect, it, vi } from 'vitest';
import { ApiError, request } from './client';
afterEach(() => vi.unstubAllGlobals());
it('preserves conflict status for the caller', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(new Response(null, { status: 409 })),
  );
  await expect(request('/reservations')).rejects.toMatchObject({
    status: 409,
    name: 'ApiError',
  });
});
it('handles empty successful responses', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(new Response(null, { status: 204 })),
  );
  await expect(request('/logout')).resolves.toBeUndefined();
});
it('includes session cookies without persisting credentials', async () => {
  const fetchMock = vi
    .fn()
    .mockResolvedValue(new Response('{"ok":true}', { status: 200 }));
  vi.stubGlobal('fetch', fetchMock);
  await expect(request('/session')).resolves.toEqual({ ok: true });
  expect(fetchMock).toHaveBeenCalledWith(
    '/api/session',
    expect.objectContaining({ credentials: 'include' }),
  );
  expect(new ApiError(401, 'Unauthorized').status).toBe(401);
});
