import { request } from '../../shared/api/client';
export interface HubSummary {
  id: string;
  name: string;
  availableVehicles: number;
  availableParking: number;
}
export const isMock = import.meta.env.VITE_DATA_SOURCE !== 'api';
const mockHubs: HubSummary[] = [
  {
    id: 'demo-01',
    name: 'Hub A · Demo',
    availableVehicles: 12,
    availableParking: 8,
  },
  {
    id: 'demo-02',
    name: 'Hub B · Demo',
    availableVehicles: 6,
    availableParking: 15,
  },
  {
    id: 'demo-03',
    name: 'Hub C · Demo',
    availableVehicles: 9,
    availableParking: 4,
  },
];
// Proposed endpoint and DTO; confirm with backend OpenAPI before integration.
export async function listHubs(signal?: AbortSignal): Promise<HubSummary[]> {
  if (isMock) return structuredClone(mockHubs);
  const result: unknown = await request<unknown>('/hubs', { signal });
  if (
    !Array.isArray(result) ||
    !result.every(
      (h) =>
        h &&
        typeof h.id === 'string' &&
        typeof h.name === 'string' &&
        Number.isInteger(h.availableVehicles) &&
        h.availableVehicles >= 0 &&
        Number.isInteger(h.availableParking) &&
        h.availableParking >= 0,
    )
  ) {
    throw new Error('INVALID_HUB_RESPONSE');
  }
  return result as HubSummary[];
}
