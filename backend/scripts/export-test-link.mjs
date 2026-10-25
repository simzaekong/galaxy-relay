import fs from 'node:fs';
import {execFileSync} from 'node:child_process';
const adb=process.env.ADB_PATH||'adb';
const value=execFileSync(adb,['-s','emulator-5580','exec-out','run-as','kr.infosphere.galaxyrelay','cat','files/relay-test-link.json']);
JSON.parse(value.toString());fs.writeFileSync(new URL('../../.secrets/test-link.json',import.meta.url),value);
console.log('Saved test-only browser credential in ignored .secrets/test-link.json.');
