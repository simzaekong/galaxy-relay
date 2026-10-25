import fs from 'node:fs';
const directory = new URL('../../.secrets/', import.meta.url);
const account=JSON.parse(fs.readFileSync(new URL('fcm-service-account.json',directory),'utf8'));
fs.writeFileSync(new URL('fcm-worker.json',directory),JSON.stringify({FCM_CLIENT_EMAIL:account.client_email,FCM_PRIVATE_KEY:account.private_key}));
console.log('Prepared ignored FCM secret file; no private values printed.');
