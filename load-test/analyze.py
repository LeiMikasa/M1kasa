"""Combine measured HTTP samples with sampled, eventually consistent pipeline results."""
import argparse
import csv
import json
import math
from pathlib import Path
from common import converged

def percentile(values, fraction):
    return sorted(values)[max(0, math.ceil(len(values) * fraction) - 1)] if values else None

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--manifest', type=Path, required=True)
    args = parser.parse_args()
    folder = args.manifest.parent
    manifest = json.loads(args.manifest.read_text(encoding='utf-8-sig'))
    paths = sorted(folder.glob('*.jtl'))
    if len(paths) != 1:
        raise RuntimeError('Expected exactly one measured run per fresh fixture')
    with paths[0].open(encoding='utf-8-sig', newline='') as stream:
        all_rows = list(csv.DictReader(stream))
    warmup = [row for row in all_rows if 'prewarm' in row['label'].lower()]
    rows = [row for row in all_rows if row not in warmup]
    if not rows:
        raise RuntimeError('No measured POST samples')
    counts = {label: sum(row['label'] == label for row in rows) for label in ['accepted', 'sold_out', 'duplicate']}
    failed = [row for row in rows if row['success'].lower() != 'true' or row['label'] not in counts]
    start_ms = min(int(row['timeStamp']) for row in rows)
    end_ms = max(int(row['timeStamp']) + int(row['elapsed']) for row in rows)
    duration = max(0.001, (end_ms - start_ms) / 1000)
    latency = [int(row['elapsed']) for row in rows]
    monitor = [json.loads(line) for line in (folder / 'monitor.jsonl').read_text(encoding='utf-8').splitlines() if line.strip()]
    final = monitor[-1]
    completed_samples = [row for row in monitor if row['buyers'] == final['buyers'] and converged(row, manifest['stock']) and row['epochMs'] >= start_ms]
    completion_ms = completed_samples[0]['epochMs'] if completed_samples else None
    e2e_seconds = max(0.001, (completion_ms - start_ms) / 1000) if completion_ms and final['buyers'] else None
    pipeline = json.loads((folder / 'pipeline-summary.json').read_text(encoding='utf-8'))
    result = {'voucherId': manifest['voucherId'], 'usersPrepared': manifest['users'], 'stock': manifest['stock'],
              'requests': len(rows), **counts, 'failures': len(failed), 'failureRatePercent': round(len(failed) * 100 / len(rows), 3),
              'httpWindowSeconds': round(duration, 3), 'attemptedRps': round(len(rows) / duration, 2),
              'businessResponseRps': round(sum(counts.values()) / duration, 2), 'acceptedRps': round(counts['accepted'] / duration, 2),
              'averageMs': round(sum(latency) / len(latency), 2), 'p50Ms': percentile(latency, .5), 'p95Ms': percentile(latency, .95), 'p99Ms': percentile(latency, .99),
              'prewarmRequests': len(warmup), 'prewarmFailures': sum(row['success'].lower() != 'true' for row in warmup),
              'final': final, 'pipelineConverged': pipeline['converged'], 'acceptedMatchesBuyers': counts['accepted'] == final['buyers'],
              'peakRedisOutboxPending': pipeline['peakRedisOutboxPending'], 'peakOrderOutboxPending': pipeline['peakOrderOutboxPending'],
              'observedE2eWindowSeconds': round(e2e_seconds, 3) if e2e_seconds else None,
              'observedE2eOrdersPerSecond': round(final['orders'] / e2e_seconds, 2) if e2e_seconds else None,
              'observedDrainAfterHttpSeconds': round(max(0, (completion_ms - end_ms) / 1000), 3) if completion_ms else None,
              'monitorSampleIntervalSeconds': pipeline['sampleIntervalSeconds'],
              'httpMeasurementValid': pipeline.get('httpSummary', {}).get('measurementValid', False),
              'expectedBusinessCountsPassed': pipeline.get('httpSummary', {}).get('expectedBusinessCountsPassed', False),
              'minimumAvailablePhysicalMiB': pipeline.get('minimumAvailablePhysicalMiB'), 'newGlobalDeadLetterRows': pipeline['newDeadLetterRows']}
    result['passed'] = not failed and result['prewarmFailures'] == 0 and result['pipelineConverged'] and result['acceptedMatchesBuyers'] and result['httpMeasurementValid'] and result['expectedBusinessCountsPassed']
    (folder / 'analysis.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False, indent=2))

if __name__ == '__main__':
    main()
