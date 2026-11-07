"""Run the isolated Java 25 build with a UTF-8 log (no system Java changes)."""
from pathlib import Path
import os, subprocess, sys

root = Path(__file__).resolve().parents[1]
env = os.environ.copy()
env['JAVA_HOME'] = str(Path.home()/'.jdks/ms-25.0.3')
log = root / sys.argv[1]
with log.open('w', encoding='utf-8') as out:
    result = subprocess.run([str(root/'.toolchains/gradle-9.6.0/bin/gradle.bat'),
                             *sys.argv[2:], '--console=plain'], cwd=root, env=env,
                            stdout=out, stderr=subprocess.STDOUT)
print(log.name, 'exit', result.returncode)
print('\n'.join(log.read_text(encoding='utf-8', errors='replace').splitlines()[-12:]))
sys.exit(result.returncode)
