"""Observe isolated fixture convergence; writes no business data."""
import argparse
import csv
import json
from pathlib import Path
import time
from common import Config, Db, Redis, converged, memory_info, rabbit_queues, snapshot

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--interval', type=float, default=2)
    parser.add_argument('--timeout', type=int, default=420)
    args = parser.parse_args()
    manifest = json.loads(args.manifest.read_text(encoding='utf-8-sig'))
    folder = args.manifest.parent
    config = Config()
    started = time.monotonic()
    rows = []
    completed = False
    stable = 0
    baseline_mq = None
    try:
        baseline_mq = rabbit_queues(config)
    except Exception:
        pass
    with Db(config) as db, Redis(config, 'm1kasa-voucher-service') as redis, (folder / 'monitor.jsonl').open('w', encoding='utf-8') as stream:
        baseline_deaths = int(db.query('order', 'SELECT COUNT(*) AS n FROM tb_mq_dead_letter')[0]['n'])
        while time.monotonic() - started < args.timeout:
            row = snapshot(db, redis, manifest['voucherId'])
            row.update(memory_info())
            rows.append(row)
            stream.write(json.dumps(row) + '\n')
            stream.flush()
            complete_files = list(folder.glob('*.summary.json'))
            if complete_files or (folder / '.driver-exited.json').is_file():
                completed = True
                stable = stable + 1 if converged(row, manifest['stock']) else 0
                if stable >= 2:
                    break
            if len(rows) % 5 == 1:
                print(json.dumps({'monitor': {'voucherId': manifest['voucherId'], **row}}, ensure_ascii=False), flush=True)
            time.sleep(args.interval)
        final_deaths = int(db.query('order', 'SELECT COUNT(*) AS n FROM tb_mq_dead_letter')[0]['n'])
        final = rows[-1]
        consistent = converged(final, manifest['stock'])
        summary = {'voucherId': manifest['voucherId'], 'initialStock': manifest['stock'], 'testCompleted': completed,
                   'converged': consistent, 'sampleIntervalSeconds': args.interval, 'final': final,
                   'peakRedisOutboxPending': max(r['redisOutboxPending'] for r in rows),
                   'peakOrderOutboxPending': max(r['orderOutboxPending'] for r in rows),
                   'minimumAvailablePhysicalMiB': min(r.get('availablePhysicalMiB', 0) for r in rows),
                   'newDeadLetterRows': final_deaths - baseline_deaths, 'baselineMq': baseline_mq}
        try:
            summary['finalMq'] = rabbit_queues(config)
        except Exception:
            summary['finalMq'] = None
        if completed and complete_files:
            http_summary = json.loads(complete_files[-1].read_text(encoding='utf-8-sig'))
            summary['httpSummaryFile'] = complete_files[-1].name
            summary['httpSummary'] = http_summary
        (folder / 'pipeline-summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps({'monitorFinished': summary}, ensure_ascii=False), flush=True)
        if not completed or not consistent:
            raise SystemExit(2)

if __name__ == '__main__':
    main()
