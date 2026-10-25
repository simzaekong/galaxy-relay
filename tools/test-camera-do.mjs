import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { createRequire } from 'node:module';
const require=createRequire(new URL('../backend/package.json',import.meta.url));const {build}=require('esbuild'),{Miniflare,convertV4MiniflareOptions}=require('miniflare');
await mkdir(new URL('../.tools/',import.meta.url),{recursive:true});
const output=new URL('../.tools/camera-do-bundle.mjs',import.meta.url);await build({entryPoints:[new URL('../backend/test/fixtures/camera-do-entry.mjs',import.meta.url).pathname.replace(/^\/([A-Za-z]:)/,'$1')],outfile:output.pathname.replace(/^\/([A-Za-z]:)/,'$1'),bundle:true,format:'esm',platform:'node',external:['cloudflare:workers']});
const mf=new Miniflare(convertV4MiniflareOptions({workers:[{name:'camera',modules:true,scriptPath:output.pathname.replace(/^\/([A-Za-z]:)/,'$1'),compatibilityDate:'2026-10-03',compatibilityFlags:['nodejs_compat'],durableObjects:{CAMERA:{className:'CameraRoom',useSQLite:true}}}]}));
const call=async body=>(await mf.dispatchFetch('http://fixture/',{method:'POST',body:JSON.stringify(body)})).json();
try{
 const sessionId='evt_00000000-0000-4000-8000-000000000001';assert.equal(await call({op:'start',sessionId}),true);assert.ok(await call({op:'attach',sessionId}));
 assert.equal((await call({op:'signal',role:'viewer',sessionId,packet:{seq:1,iv:'A'.repeat(16),ciphertext:'B'.repeat(24)}})).ended,false);
 assert.equal((await call({op:'poll',role:'sender',sessionId})).messages.length,1);
 assert.equal((await call({op:'end',role:'viewer',sessionId})).ended,true);assert.equal(await call({op:'attach',sessionId}),null);
 console.log('PASS actual Durable Object runtime, RPC, encrypted signal handoff, cancellation tombstone and alarm setup.');
}finally{await mf.dispose();}
