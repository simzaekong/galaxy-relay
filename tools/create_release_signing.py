"""Create a private local Android signing key; never print passwords."""
from pathlib import Path
import secrets
import subprocess

root = Path(__file__).resolve().parent.parent
private = root / '.secrets'
private.mkdir(exist_ok=True)
key = private / 'galaxy-relay-release.p12'
config = root / 'android/release-signing.properties'
if key.exists() or config.exists():
    raise SystemExit('Existing signing key/config preserved. Reuse or back up it before creating another.')
password = secrets.token_urlsafe(32)
password_file = private / 'release-password.tmp'
try:
    password_file.write_text(password, encoding='ascii')
    subprocess.run(['keytool', '-genkeypair', '-keystore', str(key), '-storetype', 'PKCS12',
                    '-storepass:file', str(password_file), '-keypass:file', str(password_file),
                    '-alias', 'galaxy-relay', '-keyalg', 'RSA', '-keysize', '3072',
                    '-sigalg', 'SHA256withRSA', '-validity', '10000',
                    '-dname', 'CN=Galaxy Relay Personal'], check=True)
    config.write_text('storeFile=../.secrets/galaxy-relay-release.p12\nkeyAlias=galaxy-relay\n'
                      + 'storePassword=' + password + '\nkeyPassword=' + password + '\n', encoding='ascii')
finally:
    password_file.unlink(missing_ok=True)
print('Private signing key and ignored Gradle configuration created. Keep an offline backup for updates.')
