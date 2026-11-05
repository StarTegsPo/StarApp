/**
 * Реестр сервисов. Чтобы добавить новый сервис:
 *  1. создайте src/services/<id>/index.js по контракту ниже;
 *  2. добавьте его в массив services;
 *  3. создайте webapp/services/<id>.js с export function render(el, app).
 *
 * Контракт сервиса:
 *  {
 *    id, title, icon, order,
 *    migrations?: string[],                 // SQL, должен быть идемпотентным
 *    setupBot?(ctx),                        // ctx = { bot, db, config, services }
 *    setupApi?(router, ctx),
 *    onPayment?(ctx, { user, payload, payment }), // payload без префикса "<id>:"
 *    validatePayment?(ctx, { user, payload, totalAmount }) -> string|null (текст ошибки)
 *    start?(ctx)                            // фоновые задачи
 *  }
 */
import profile from './profile/index.js';
import shop from './shop/index.js';
import analytics from './analytics/index.js';
import support from './support/index.js';

export const services = [analytics, shop, profile, support].sort((a, b) => a.order - b.order);

export const isAdmin = (config, userId) => config.adminIds.includes(Number(userId));

/** Создаёт ссылку на оплату в Telegram Stars (currency XTR, provider_token пустой). */
export function createStarsInvoice(bot, serviceId, { title, description, payload, amount }) {
  return bot.call('createInvoiceLink', {
    title: title.slice(0, 32),
    description: (description || title).slice(0, 255),
    payload: `${serviceId}:${payload}`,
    provider_token: '',
    currency: 'XTR',
    prices: [{ label: title.slice(0, 32), amount }],
  });
}
