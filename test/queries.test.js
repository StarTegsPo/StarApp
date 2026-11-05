import { test } from 'node:test';
import assert from 'node:assert/strict';
import { fillDays } from '../src/services/analytics/queries.js';

test('fillDays заполняет пропуски нулями', () => {
  const now = Date.UTC(2026, 0, 10) / 1000;
  const out = fillDays([{ day: '2026-01-09', value: 5 }], 3, now);
  assert.deepEqual(out, [
    { day: '2026-01-08', value: 0 },
    { day: '2026-01-09', value: 5 },
    { day: '2026-01-10', value: 0 },
  ]);
});
