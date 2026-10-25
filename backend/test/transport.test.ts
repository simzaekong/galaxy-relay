import { it, expect, vi, afterEach } from 'vitest';
import { createECDH, randomBytes, generateKeyPairSync } from 'node:crypto';
import { createRequire } from 'node:module';
import webpush from 'web-push';
import { production } from '../src/transport';
const ece=createRequire(import.meta.url)('http_ece');
afterEach(()=>vi.unstubAllGlobals());
it('generates a real VAPID aes128gcm payload that the subscriber decrypts',async()=>{
  const keys=webpush.generateVAPIDKeys(), client=createECDH('prime256v1');client.generateKeys();const auth=randomBytes(16).toString('base64url');
  const subscription={endpoint:'https://web.push.apple.com/test',keys:{p256dh:client.getPublicKey().toString('base64url'),auth}};
  const payload={id:'evt_'+crypto.randomUUID(),title:'김철수',body:'야 어디야?'};
  vi.stubGlobal('fetch',vi.fn(async(_url,init)=>{
    expect(init.headers.Authorization).toMatch(/^vapid /i);
    const plaintext=ece.decrypt(Buffer.from(init.body),{version:'aes128gcm',privateKey:client,authSecret:auth});
    expect(JSON.parse(plaintext.toString())).toEqual(payload);return new Response(null,{status:201});
  }));
  await production.push({VAPID_SUBJECT:'mailto:a@example.com',VAPID_PUBLIC_KEY:keys.publicKey,VAPID_PRIVATE_KEY:keys.privateKey},subscription,payload);
});
it('executes OAuth RS256 and FCM HTTP v1 request, handles rejected delivery',async()=>{
  const keys=generateKeyPairSync('rsa',{modulusLength:2048});let n=0;
  vi.stubGlobal('fetch',vi.fn(async(url,init)=>{
    if(n++===0){expect(String(url)).toContain('oauth2.googleapis.com');expect(init.body.get('assertion').split('.')).toHaveLength(3);return Response.json({access_token:'test-access-token'});}
    expect(String(url)).toContain('/v1/projects/project/messages:send');expect(JSON.parse(init.body).message.data.commandId).toBe('cmd_test');return new Response(null,{status:403});
  }));
  await expect(production.fcm({FCM_PRIVATE_KEY:keys.privateKey.export({type:'pkcs8',format:'pem'}).toString(),FCM_CLIENT_EMAIL:'a@example.com',FCM_PROJECT_ID:'project'},'registration-token',{commandId:'cmd_test'})).rejects.toThrow('FCM_FAILED');
});
