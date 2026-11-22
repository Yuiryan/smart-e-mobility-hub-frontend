import { request } from '../shared/api/client.js';
export function searchPayload({
  latitude,
  longitude,
  radiusKm,
  reference = 'origin',
}) {
  const lat = Number(latitude),
    lng = Number(longitude),
    radius = Number(radiusKm);
  if (
    String(latitude).trim() === '' ||
    String(longitude).trim() === '' ||
    !Number.isFinite(lat) ||
    !Number.isFinite(lng) ||
    lat < -90 ||
    lat > 90 ||
    lng < -180 ||
    lng > 180 ||
    !Number.isFinite(radius) ||
    radius <= 0 ||
    radius > 50 ||
    !['origin', 'destination'].includes(reference)
  )
    throw new Error('SEARCH_INPUT');
  return {
    origin: reference === 'origin' ? { latitude: lat, longitude: lng } : null,
    destination:
      reference === 'destination' ? { latitude: lat, longitude: lng } : null,
    radiusKm: radius,
  };
}
export function validateSearchResponse(data) {
  if (!Array.isArray(data?.hubs)) throw new Error('SEARCH_RESPONSE');
  for (const h of data.hubs) {
    if (
      typeof h.hubId !== 'string' ||
      typeof h.hubName !== 'string' ||
      !Number.isFinite(h.distanceKm) ||
      h.distanceKm < 0 ||
      !Number.isInteger(h.availableVehicleCount) ||
      h.availableVehicleCount < 0 ||
      !['FRESH', 'STALE', 'MISSING'].includes(h.telemetryStatus) ||
      !Array.isArray(h.vehicles)
    )
      throw new Error('SEARCH_RESPONSE');
    for (const v of h.vehicles)
      if (
        typeof v.vehicleId !== 'string' ||
        !Number.isFinite(v.batteryPct) ||
        v.batteryPct < 0 ||
        v.batteryPct > 100 ||
        typeof v.status !== 'string'
      )
        throw new Error('SEARCH_RESPONSE');
  }
  return data.hubs;
}
export async function searchHubs(values, signal) {
  return validateSearchResponse(
    await request('/search/hubs', {
      method: 'POST',
      body: JSON.stringify(searchPayload(values)),
      signal,
    }),
  );
}
