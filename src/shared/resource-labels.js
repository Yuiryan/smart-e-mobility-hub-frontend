import { t } from './ui.js';

export function hubLabel(id, hubs = []) {
  return (
    hubs.find((hub) => hub.hubId === id)?.hubName ||
    (id?.startsWith('hub_') ? 'Hub ' + id.slice(4).toUpperCase() : id)
  );
}

export function resourceLabel(id) {
  const match = /^(space|port)_.+_(\d+)$/.exec(id);
  if (!match) return id;
  return (
    (match[1] === 'space' ? t('Ô số ', 'Space ') : t('Cổng sạc số ', 'Port ')) +
    Number(match[2])
  );
}
