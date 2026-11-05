import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validateInitData, signInitData } from '../src/auth.js';

const token = '42:secret';
const now = Math.floor(Date.now() / 1000);
const user = { id: 7, first_name: 'Аня' };

test('валидная подпись initData принимается', () => {
  const data = signInitData({ auth_date: String(now), user: JSON.stringify(user) }, token);
  assert.deepEqual(validateInitData(data, token), user);
});

test('подделка, чужой токен и устаревшие данные отклоняются', () => {
  const data = signInitData({ auth_date: String(now), user: JSON.stringify(user) }, token);
  assert.equal(validateInitData(data.replace('%22id%22%3A7', '%22id%22%3A8'), token), null);
  assert.equal(validateInitData(data, '42:other'), null);
  const old = signInitData({ auth_date: String(now - 3 * 86400), user: JSON.stringify(user) }, token);
  assert.equal(validateInitData(old, token), null);
  assert.equal(validateInitData('', token), null);
});
