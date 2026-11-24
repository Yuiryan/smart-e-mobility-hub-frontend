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
