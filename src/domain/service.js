import { DemoEngine } from '../mocks/engine.js';
import { DomainError } from './types.js';
export const engine = new DemoEngine();
export const demoMode = import.meta.env.VITE_DATA_SOURCE !== 'api';
// Replace this adapter when the backend team publishes endpoints and DTOs.
export async function call(method, ...args) {
  if (!demoMode) throw new DomainError('API_NOT_CONFIGURED');
  return engine[method](...args);
}
export function snapshot() {
  if (!demoMode) throw new DomainError('API_NOT_CONFIGURED');
  return engine.snapshot();
}
