"""Execute one bounded case with a fresh activity and read-only convergence monitoring."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
from common import HERE, PROJECT

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--pool', type=Path, required=True)
    parser.add_argument('--users', type=int, required=True)
    parser.add_argument('--stock', type=int, required=True)
    parser.add_argument('--threads', type=int, required=True)
    parser.add_argument('--mode', choices=['burst', 'sustained'], default='burst')
    parser.add_argument('--duration', type=int, default=60)
    parser.add_argument('--rate', type=float, default=10)
    parser.add_argument('--ramp', type=int, default=5)
    parser.add_argument('--label', required=True)
    args = parser.parse_args()
    env = os.environ.copy()
    env['PYTHONIOENCODING'] = 'utf-8'
    prepare = subprocess.Popen([sys.executable, str(HERE / 'prepare.py'), '--pool', str(args.pool), '--users', str(args.users), '--stock', str(args.stock)],
                               cwd=PROJECT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding='utf-8', env=env)
    manifest_path = None
    for line in prepare.stdout:
        print(line.rstrip(), flush=True)
        if line.startswith('Manifest: '):
            manifest_path = Path(line[len('Manifest: '):].strip())
    if prepare.wait() != 0 or manifest_path is None:
        raise RuntimeError('Fixture preparation failed')
    manifest = json.loads(manifest_path.read_text(encoding='utf-8-sig'))
    manifest['caseLabel'] = args.label
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
    folder = manifest_path.parent
    monitor_log = (folder / 'monitor-output.txt').open('w', encoding='utf-8')
    monitor = subprocess.Popen([sys.executable, '-u', str(HERE / 'monitor.py'), '--manifest', str(manifest_path)],
                               cwd=PROJECT, stdout=monitor_log, stderr=subprocess.STDOUT, env=env)
    try:
        deadline = time.monotonic() + 30
        while not (folder / 'monitor.jsonl').is_file() or (folder / 'monitor.jsonl').stat().st_size == 0:
            if monitor.poll() is not None:
                raise RuntimeError('Monitor stopped during initialization; see monitor-output.txt')
            if time.monotonic() > deadline:
                raise RuntimeError('Monitor did not become ready')
            time.sleep(.2)
        print('CASE START ' + json.dumps({'label': args.label, 'manifest': str(manifest_path), 'mode': args.mode, 'threads': args.threads}, ensure_ascii=False), flush=True)
        pwsh = shutil.which('pwsh') or shutil.which('powershell')
        command = [pwsh, '-NoProfile', '-File', str(HERE / 'run.ps1'), '-Manifest', str(manifest_path), '-Mode', args.mode,
                   '-Threads', str(args.threads), '-RampSeconds', str(args.ramp)]
        if args.mode == 'sustained':
            command += ['-DurationSeconds', str(args.duration), '-TargetRps', str(args.rate)]
        with (folder / 'driver-output.txt').open('w', encoding='utf-8') as stream:
            driver = subprocess.run(command, cwd=PROJECT, stdout=stream, stderr=subprocess.STDOUT, env=env)
        (folder / '.driver-exited.json').write_text(json.dumps({'returnCode': driver.returncode, 'epochMs': int(time.time() * 1000)}), encoding='utf-8')
        print('HTTP phase finished; waiting for final orders and inventory synchronization', flush=True)
        monitor_code = monitor.wait(timeout=420)
        if monitor_code != 0:
            raise RuntimeError('Pipeline failed to converge; inspect pipeline-summary.json')
        analyzed = subprocess.run([sys.executable, str(HERE / 'analyze.py'), '--manifest', str(manifest_path)],
                                  cwd=PROJECT, capture_output=True, text=True, encoding='utf-8', env=env)
        if analyzed.returncode != 0:
            raise RuntimeError('Analysis failed: ' + analyzed.stderr[-1000:])
        result = json.loads((folder / 'analysis.json').read_text(encoding='utf-8'))
        result['caseLabel'] = args.label
        result['manifest'] = str(manifest_path)
        (folder / 'analysis.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
        print('CASE RESULT ' + json.dumps(result, ensure_ascii=False), flush=True)
        if driver.returncode != 0 or not result['passed']:
            raise SystemExit(2)
    finally:
        if monitor.poll() is None:
            monitor.terminate()
            try:
                monitor.wait(timeout=5)
            except subprocess.TimeoutExpired:
                monitor.kill()
        monitor_log.close()

if __name__ == '__main__':
    main()
