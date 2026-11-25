import { afterEach, describe, it, expect, vi } from 'vitest';
import {
  searchPayload,
  searchHubs,
  validateSearchResponse,
} from './hub-search-api.js';
import { mountLiveSearch, searchResults, searchError } from './live-search.js';
const values = { latitude: '10.77', longitude: '106.66', radiusKm: '3' };
const hub = {
  hubId: 'H1',
  hubName: 'Hub 1',
  distanceKm: 0,
  availableVehicleCount: 1,
  vehicles: [{ vehicleId: 'E20', batteryPct: 20, status: 'AVAILABLE' }],
  telemetryStatus: 'FRESH',
};
afterEach(() => vi.unstubAllGlobals());
describe('UC01 backend integration', () => {
  it('uses the implemented POST endpoint and exact DTO without login', async () => {
    const fetch = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify({ hubs: [hub] })));
    vi.stubGlobal('fetch', fetch);
    expect(await searchHubs(values)).toEqual([hub]);
    expect(fetch).toHaveBeenCalledWith(
      '/api/search/hubs',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          origin: { latitude: 10.77, longitude: 106.66 },
          destination: null,
          radiusKm: 3,
        }),
      }),
    );
  });
  it('supports destination search without also supplying origin', () =>
    expect(searchPayload({ ...values, reference: 'destination' })).toEqual({
      origin: null,
      destination: { latitude: 10.77, longitude: 106.66 },
      radiusKm: 3,
    }));
  it.each([
    { latitude: '' },
    { longitude: '181' },
    { latitude: 'NaN' },
    { radiusKm: 0 },
    { radiusKm: 51 },
  ])('rejects invalid search %o', (patch) =>
    expect(() => searchPayload({ ...values, ...patch })).toThrow(
      'SEARCH_INPUT',
    ),
  );
  it('preserves stale empty hub results rather than supplying demo vehicles', () => {
    const stale = {
      ...hub,
      vehicles: [],
      availableVehicleCount: 0,
      telemetryStatus: 'STALE',
    };
    expect(validateSearchResponse({ hubs: [stale] })).toEqual([stale]);
    document.body.innerHTML = searchResults([stale]);
    expect(document.body.textContent).toContain('0');
    expect(document.body.textContent).not.toContain('E-024');
  });
  it('escapes backend names and IDs', () => {
    const html = searchResults([
      {
        ...hub,
        hubName: '<img src=x onerror=bad()>',
        vehicles: [{ ...hub.vehicles[0], vehicleId: '<script>bad()</script>' }],
      },
    ]);
    expect(html).not.toContain('<img');
    expect(html).not.toContain('<script>');
  });
  it('rejects malformed response and keeps network errors actionable', () => {
    expect(() =>
      validateSearchResponse({ hubs: [{ ...hub, telemetryStatus: '???' }] }),
    ).toThrow('SEARCH_RESPONSE');
    expect(searchError({ status: 404 })).toContain('m1_student');
  });
  it('does not call mock or invented APIs on an unavailable route', () => {
    const fetch = vi.fn();
    vi.stubGlobal('fetch', fetch);
    const dispose = mountLiveSearch(document.body, '/account');
    expect(document.body.textContent).toContain('API');
    expect(document.querySelector('form')).toBeNull();
    expect(fetch).not.toHaveBeenCalled();
    dispose();
  });
  it('renders the returned data after submitting the live form', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response(JSON.stringify({ hubs: [hub] }))),
    );
    const dispose = mountLiveSearch(document.body, '/student/hubs');
    document.querySelector('[name="latitude"]').value = '10.77';
    document.querySelector('[name="longitude"]').value = '106.66';
    document
      .querySelector('form')
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await vi.waitFor(() =>
      expect(document.querySelector('#live-results').textContent).toContain(
        'E20',
      ),
    );
    expect(document.querySelector('#live-results').textContent).not.toContain(
      'E-024',
    );
    dispose();
  });
});
