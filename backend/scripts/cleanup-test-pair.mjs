// Revoke only the dedicated integration pair exported by export-test-link.mjs.
import fs from 'node:fs';
const link=JSON.parse(fs.readFileSync(new URL('../../.secrets/test-link.json',import.meta.url),'utf8'));
if(new URL(link.backend).protocol!=='https:')throw new Error('Test backend must use HTTPS');
const response=await fetch(new URL('/api/unpair',link.backend),{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+link.webToken},body:JSON.stringify({timestamp:Date.now(),nonce:crypto.randomUUID()}),signal:AbortSignal.timeout(15000)});
if(!response.ok&&response.status!==401)throw new Error('Test pair cleanup failed; status '+response.status);
console.log('Dedicated integration pair revoked. No credential values displayed.');
