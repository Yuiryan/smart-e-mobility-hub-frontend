import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import App from './App';
import { setPreferences } from './shared/preferences';
afterEach(() => {
  cleanup();
  setPreferences({ locale: 'vi', theme: 'light' });
  window.history.replaceState({}, '', '/');
});
it('opens operator workspace and preserves display preferences across navigation', async () => {
  window.history.replaceState({}, '', '/');
  render(<App />);
  fireEvent.click(screen.getByRole('link', { name: /Đơn vị vận hành/ }));
  expect(
    await screen.findByRole('heading', { name: 'Tổng quan mạng lưới' }),
  ).toBeInTheDocument();
  fireEvent.change(screen.getByRole('combobox', { name: 'Ngôn ngữ' }), {
    target: { value: 'en' },
  });
  expect(
    screen.getByRole('heading', { name: 'Network overview' }),
  ).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Toggle theme' }));
  expect(document.documentElement.dataset.theme).toBe('dark');
  expect(JSON.parse(localStorage.getItem('emobility.preferences')!)).toEqual({
    locale: 'en',
    theme: 'dark',
  });
  fireEvent.click(screen.getByRole('link', { name: /UC14/ }));
  expect(
    await screen.findByRole('heading', { name: 'What-if simulation' }),
  ).toBeInTheDocument();
});
it('renders unknown route as 404', () => {
  window.history.replaceState({}, '', '/missing-page');
  render(<App />);
  expect(screen.getByRole('heading', { name: '404' })).toBeInTheDocument();
});
