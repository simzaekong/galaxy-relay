// Normal-process receiver test. Never points at a physical phone.
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
const exec=promisify(execFile),adb=process.env.ADB_PATH||'adb';
const run=args=>exec(adb,['-s','emulator-5580',...args],{timeout:90000});
async function instrument(method){const result=await run(['shell','am','instrument','-w','-r','-e','class','kr.infosphere.galaxyrelay.TelephonyRelayTest#'+method,'kr.infosphere.galaxyrelay.test/androidx.test.runner.AndroidJUnitRunner']);if(!result.stdout.includes('OK (1 test)'))throw new Error('Native test failed: '+result.stdout);}
try{
  await instrument('armNativeScenario');
  await run(['emu','sms','send','01012345678','RelaySmsIntegration']);
  await run(['emu','gsm','call','5551234']);
  // Keep the modem call alive for the actual asynchronous platform receiver.
  await new Promise(resolve=>setTimeout(resolve,15000));
  await run(['emu','gsm','cancel','5551234']);
  await new Promise(resolve=>setTimeout(resolve,10000));
  await instrument('verifyNativeScenario');
  console.log('PASS: native SMS and incoming call/number/end in the normal Android app process.');
}finally{await run(['emu','gsm','cancel','5551234']).catch(()=>{});await instrument('restoreNativeSettings').catch(()=>{});}
