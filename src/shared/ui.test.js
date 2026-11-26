import { expect, it } from 'vitest';
import { date } from './ui.js';
it('formats ISO instants from backend the same way as millisecond timestamps', () => {
  const instant = '2026-11-25T16:48:00Z';
  expect(date(instant)).toBe(date(Date.parse(instant)));
  expect(date(instant)).not.toBe('—');
});
it.each([null, undefined, '', 'invalid', Infinity])(
  'keeps missing or invalid dates unknown: %s',
  (value) => {
    expect(date(value)).toBe('—');
  },
);
