import { describe, expect, it } from 'vitest';
import { parsePreferences } from './preferences';
describe('saved browser preferences', () => {
  it('recovers from corrupt data', () => {
    expect(parsePreferences('{broken')).toEqual({
      locale: 'vi',
      theme: 'light',
    });
  });
  it('rejects unsupported values', () => {
    expect(parsePreferences('{"locale":"fr","theme":"invalid"}')).toEqual({
      locale: 'vi',
      theme: 'light',
    });
  });
  it('restores supported preferences', () => {
    expect(parsePreferences('{"locale":"en","theme":"dark"}')).toEqual({
      locale: 'en',
      theme: 'dark',
    });
  });
});
