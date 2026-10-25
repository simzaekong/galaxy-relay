import webpush from 'web-push';
import { importPKCS8, SignJWT } from 'jose';
export type Subscription = { endpoint: string; keys: { p256dh: string; auth: string } };
export class DeliveryError extends Error { constructor(public code: string, public status = 502) { super(code); } }
export interface Transport {
  push(env: Env, subscription: Subscription, payload: object): Promise<void>;
  fcm(env: Env, token: string, data: Record<string, string>): Promise<void>;
}
type PushConfig = { VAPID_SUBJECT: string; VAPID_PUBLIC_KEY: string; VAPID_PRIVATE_KEY: string };
type FcmConfig = { FCM_CLIENT_EMAIL: string; FCM_PRIVATE_KEY: string; FCM_PROJECT_ID: string };
export const production = {
  async push(env: PushConfig, subscription: Subscription, payload: object) {
    const details = webpush.generateRequestDetails(subscription, JSON.stringify(payload), {
      TTL: 3600, urgency: 'high', contentEncoding: 'aes128gcm',
      vapidDetails: { subject: env.VAPID_SUBJECT, publicKey: env.VAPID_PUBLIC_KEY, privateKey: env.VAPID_PRIVATE_KEY }
    });
    let response:Response;
    try{response = await fetch(details.endpoint, { method: 'POST', headers: details.headers as Record<string, string>, body: new Uint8Array(details.body!), redirect: 'manual', signal: AbortSignal.timeout(10000) });}catch(e){const message=e instanceof Error?e.message.toLowerCase():'';const reason=message.includes('header')?'PUSH_HEADER_REJECTED':message.includes('content-length')?'PUSH_LENGTH_REJECTED':message.includes('redirect')?'PUSH_REDIRECT_REJECTED':message.includes('timeout')||message.includes('abort')?'PUSH_TIMEOUT':'PUSH_NETWORK_FAILED';throw new DeliveryError(reason);}
    if (!response.ok) {await response.body?.cancel();throw new DeliveryError([404, 410].includes(response.status) ? 'PUSH_EXPIRED' : response.status>=300&&response.status<400?'PUSH_REDIRECT_REJECTED':'PUSH_FAILED');}
    await response.body?.cancel();
  },
  async fcm(env: FcmConfig, token: string, data: Record<string, string>) {
    const key = await importPKCS8(env.FCM_PRIVATE_KEY.replace(/\\n/g, '\n'), 'RS256');
    const assertion = await new SignJWT({ scope: 'https://www.googleapis.com/auth/firebase.messaging' })
      .setProtectedHeader({ alg: 'RS256' }).setIssuer(env.FCM_CLIENT_EMAIL)
      .setAudience('https://oauth2.googleapis.com/token').setIssuedAt().setExpirationTime('5m').sign(key);
    const oauth = await fetch('https://oauth2.googleapis.com/token', { method: 'POST', body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion }), signal: AbortSignal.timeout(10000) });
    if (!oauth.ok) throw new DeliveryError('FCM_AUTH_FAILED');
    const auth = await oauth.json() as { access_token: string };
    const response = await fetch(`https://fcm.googleapis.com/v1/projects/${encodeURIComponent(env.FCM_PROJECT_ID)}/messages:send`, {
      method: 'POST', headers: { Authorization: `Bearer ${auth.access_token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: { token, data, android: { priority: 'high', ttl: '120s' } } }), signal: AbortSignal.timeout(10000)
    });
    if (!response.ok) throw new DeliveryError('FCM_FAILED');
    await response.body?.cancel();
  }
} satisfies Transport;
