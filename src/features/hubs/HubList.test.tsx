import { fireEvent, render, screen, cleanup } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import HubList from './HubList';
afterEach(cleanup);
it('loads mock hubs and shows an empty search state', async () => {
  render(<HubList />);
  expect(await screen.findByText('Hub A · Demo')).toBeInTheDocument();
  fireEvent.change(screen.getByRole('textbox'), {
    target: { value: 'not-a-hub' },
  });
  expect(screen.getByRole('status')).toHaveTextContent(
    'Không tìm thấy Hub phù hợp.',
  );
});
