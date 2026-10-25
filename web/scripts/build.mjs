import fs from 'node:fs';
import crypto from 'node:crypto';
const root=new URL('../',import.meta.url),out=new URL('dist/',root);
fs.mkdirSync(out,{recursive:true});
fs.cpSync(new URL('public/',root),out,{recursive:true,filter:source=>!source.endsWith('wallpaper.png')});
fs.cpSync(new URL('src/',root),new URL('src/',out),{recursive:true,filter:source=>!source.endsWith('glass.js')});
// Fixed retired files inside web/dist only.
for(const file of ['images/wallpaper.png','src/glass.js'])fs.rmSync(new URL(file,out),{force:true});
for(const file of ['manifest.json','service-worker.js'])fs.copyFileSync(new URL(file,root),new URL(file,out));
const digest=crypto.createHash('sha256');
function hashTree(directory){for(const item of fs.readdirSync(directory,{withFileTypes:true}).sort((a,b)=>a.name.localeCompare(b.name))){const path=new URL(item.name+(item.isDirectory()?'/':''),directory);if(item.isDirectory())hashTree(path);else{digest.update(item.name);digest.update(fs.readFileSync(path));}}}
hashTree(new URL('public/',root));hashTree(new URL('src/',root));digest.update(fs.readFileSync(new URL('manifest.json',root)));digest.update(fs.readFileSync(new URL('service-worker.js',root)));
const worker=new URL('service-worker.js',out);fs.writeFileSync(worker,fs.readFileSync(worker,'utf8').replace(/const CACHE='[^']+';/,"const CACHE='galaxy-relay-"+digest.digest('hex').slice(0,16)+"';"));
console.log('PWA static assets built. No credentials or user data included.');
