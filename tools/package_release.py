"""Export source + personal debug APK without credentials, user data or build caches."""
from pathlib import Path
import hashlib
import json
import os
import shutil
import zipfile

root=Path(__file__).resolve().parent.parent
output=root/'artifacts'
output.mkdir(exist_ok=True)
excluded={'node_modules','.npm-cache','.wrangler','dist','build','.gradle','.secrets','.tools','artifacts','test-results','playwright-report','.git','.idea','__pycache__','.build','.swiftpm','.build-ipa','.build-ios','.build-ios-sim'}
names={'local.properties','google-services.json','.dev.vars','release-signing.properties'}
files=[]
for directory, directories, filenames in os.walk(root):
    directories[:] = [name for name in directories if name not in excluded]
    for name in filenames:
        path=Path(directory)/name
        if path.name in names or path.name.startswith('.env') or path.suffix.lower() in {'.jks','.keystore','.pem','.p12','.pfx','.apk','.ipa','.log'}:continue
        files.append(path)

# Compare local setup secrets with candidate files without displaying any values.
secrets=[]
signing=root/'android/release-signing.properties'
if signing.exists():
    secrets.extend(line.split('=',1)[1].encode() for line in signing.read_text().splitlines() if line.startswith(('storePassword=','keyPassword=')))
for filename,keys in [('cloudflare.json',['INSTALL_SECRET','COMMAND_SECRET','VAPID_PRIVATE_KEY']),('fcm-service-account.json',['private_key']),('fcm-worker.json',['FCM_PRIVATE_KEY']),('test-link.json',['webToken'])]:
    path=root/'.secrets'/filename
    if path.exists():
        values=json.loads(path.read_text(encoding='utf-8'))
        secrets.extend(values[k].encode() for k in keys if isinstance(values.get(k),str) and len(values[k])>=24)
for path in files:
    data=path.read_bytes()
    if b'-----BEGIN PRIVATE KEY-----\nMII' in data or any(secret in data or secret.replace(b'\n',b'\\n') in data for secret in secrets):
        raise RuntimeError('Secret detected in release candidate; export stopped.')

archive=output/'galaxy-relay-source.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as zipped:
    for path in sorted(files):
        zipped.write(path,'galaxy-relay/'+path.relative_to(root).as_posix())
with zipfile.ZipFile(archive) as zipped:
    assert zipped.testzip() is None
    assert not any(any(part in excluded or part in names for part in Path(name).parts) for name in zipped.namelist())

apks=[]
for variant,filename in [('release','GalaxyRelay.apk'),('debug','GalaxyRelay-debug.apk')]:
    apk=output/filename
    shutil.copyfile(root/f'android/app/build/outputs/apk/{variant}/app-{variant}.apk',apk)
    with zipfile.ZipFile(apk) as zipped:
        for name in zipped.namelist():
            data=zipped.read(name)
            if any(secret in data for secret in secrets):raise RuntimeError('Setup secret found in APK; release stopped.')
    apks.append(apk)
checksums='\n'.join(hashlib.sha256(path.read_bytes()).hexdigest()+'  '+path.name for path in [*apks,archive,*sorted(output.glob('*.ipa'))])+'\n'
(output/'SHA256SUMS.txt').write_text(checksums,encoding='utf-8')
print(f'Exported {len(files)} source files and APK. Archive integrity and secret exclusion checks passed.')
