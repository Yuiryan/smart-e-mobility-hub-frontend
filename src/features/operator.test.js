import { beforeEach, describe, expect, it } from 'vitest';
import { DemoEngine, DEMO_PASSWORD } from '../mocks/engine.js';
import { OperatorEngine } from '../mocks/operator-engine.js';
import { operatorPage, bindOperator } from './operator.js';
import { preference } from '../shared/ui.js';
let ops;
beforeEach(() => {
  const engine = new DemoEngine();
  ops = new OperatorEngine(engine);
  engine.login('operator@demo.local', DEMO_PASSWORD, 'OPERATOR');
  preference('locale', 'en');
});
describe('Operator pages', () => {
  it.each([
    'overview',
    'interventions',
    'alerts',
    'dispatch',
    'incidents',
    'charging',
    'simulation',
  ])('renders the %s page without placeholders', (route) => {
    document.body.innerHTML = operatorPage(ops, '/operator/' + route);
    expect(document.querySelector('h1')).not.toBeNull();
    expect(document.body.textContent).not.toContain('not implemented');
  });
  it('escapes incident text and renders pending and timeout commands', () => {
    ops.recordIncident('E-024', '<img src=x onerror=alert(1)>', 'CRITICAL');
    ops.command('E-052', 'LOCK', 'Inspect', 'TIMEOUT');
    document.body.innerHTML = operatorPage(ops, '/operator/incidents');
    expect(document.querySelector('img')).toBeNull();
    expect(document.body.textContent).toContain('Awaiting ACK');
  });
  it('filters resource rows and reports empty results', () => {
    document.body.innerHTML = operatorPage(ops, '/operator/interventions');
    const dispose = bindOperator(
      document.body,
      ops,
      '/operator/interventions',
      {},
      () => {},
    );
    const search = document.querySelector('[data-resource-search]');
    search.value = 'nonexistent';
    search.dispatchEvent(new Event('input', { bubbles: true }));
    expect(document.querySelector('[data-resource-empty]').hidden).toBe(false);
    expect(
      [...document.querySelectorAll('[data-resource-row]')].every(
        (r) => r.hidden,
      ),
    ).toBe(true);
    dispose();
  });
  it('submits a sandbox scenario through the actual form event', async () => {
    document.body.innerHTML = operatorPage(ops, '/operator/simulation');
    const dispose = bindOperator(
      document.body,
      ops,
      '/operator/simulation',
      {},
      () => {},
    );
    document
      .querySelector('form')
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await Promise.resolve();
    expect(ops.simulation).not.toBeNull();
    expect(ops.orders).toHaveLength(0);
    dispose();
  });
});

it('updates selected dispatch count and destination capacity', () => {
  document.body.innerHTML = operatorPage(ops, '/operator/dispatch');
  const dispose = bindOperator(
    document.body,
    ops,
    '/operator/dispatch',
    {},
    () => {},
  );
  const checkbox = document.querySelector('[name=vehicles]:not(:disabled)');
  checkbox.checked = true;
  checkbox.dispatchEvent(new Event('change', { bubbles: true }));
  expect(document.querySelector('[data-dispatch-count]').value).toBe('1');
  expect(document.querySelector('[data-capacity]').textContent).toContain(
    'receiving 1 vehicles',
  );
  dispose();
});
it('shows resource totals without undefined values', () => {
  document.body.innerHTML = operatorPage(ops, '/operator/resources');
  expect(document.querySelectorAll('table')).toHaveLength(3);
  expect(document.body.textContent).not.toMatch(/undefined|NaN/);
});
it('opens a proposed dispatch form without creating an order', () => {
  ops.simulate('metro', 0, 100, 2);
  expect(ops.simulation.plan).toBeTruthy();
  const state = {};
  document.body.innerHTML = operatorPage(ops, '/operator/simulation', state);
  const dispose = bindOperator(
    document.body,
    ops,
    '/operator/simulation',
    state,
    () => {},
  );
  document.querySelector('[data-op=prepare-simulation]').click();
  expect(state.source).toBe(ops.simulation.plan.source);
  expect(state.proposedVehicles).toEqual(ops.simulation.plan.vehicleIds);
  expect(ops.orders).toHaveLength(0);
  expect(location.hash).toBe('#/operator/dispatch');
  dispose();
});
it('switches the resource details selector before sending a command', () => {
  const state = {};
  document.body.innerHTML = operatorPage(ops, '/operator/interventions', state);
  const dispose = bindOperator(
    document.body,
    ops,
    '/operator/interventions',
    state,
    () => {},
  );
  const select = document.querySelector('[name=selectedResource]');
  select.value = 'E-024';
  select.dispatchEvent(new Event('change', { bubbles: true }));
  expect(state.resource).toBe('E-024');
  document.body.innerHTML = operatorPage(ops, '/operator/interventions', state);
  expect(document.querySelector('[name=resource]').value).toBe('E-024');
  dispose();
});
