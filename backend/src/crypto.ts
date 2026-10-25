import { createHmac, timingSafeEqual } from 'node:crypto';
export const random = (n = 32) => Buffer.from(crypto.getRandomValues(new Uint8Array(n))).toString('base64url');
export const hash = async (s: string) => Buffer.from(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(s))).toString('hex');
export function equal(a: string, b: string) { const x = Buffer.from(a), y = Buffer.from(b); return x.length === y.length && timingSafeEqual(x, y); }
export const commandKey = (secret: string, id: string) => createHmac('sha256', secret).update('command:' + id).digest('base64url');
export const sign = (key: string, value: string) => createHmac('sha256', Buffer.from(key, 'base64url')).update(value).digest('base64url');
export function code() { // Rejection sampling: unbiased decimal digits, ~40 bits, plus local Android approval.
  let out = ''; while (out.length < 12) for (const b of crypto.getRandomValues(new Uint8Array(24))) if (b < 250 && out.length < 12) out += b % 10;
  return out;
}
