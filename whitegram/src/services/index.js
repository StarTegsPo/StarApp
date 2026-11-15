/**
 * Реестр сервисов WhiteGram. Чтобы добавить новый сервис:
 *  1. создайте src/services/<id>/index.js по контракту ниже;
 *  2. добавьте его в массив services;
 *  3. создайте webapp/services/<id>.js с export function render(el, app).
 *
 * Контракт сервиса:
 *  {
 *    id, title, icon, order,
 *    migrations?: string[],
 *    setupBot?(ctx),
 *    setupApi?(router, ctx),
 *    onPayment?(ctx, { user, payload, payment }),
 *    validatePayment?(ctx, { user, payload, totalAmount }) -> string|null,
 *    onStart?(ctx, { user, args, isNew }),
 *    afterPayment?(ctx, { user, payment }),
 *    start?(ctx)
 *  }
 */
import profile from './profile/index.js';

export const services = [profile].sort((a, b) => a.order - b.order);

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