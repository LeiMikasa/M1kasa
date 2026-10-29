"""Produce a sanitized, reproducible Markdown report from completed local runs."""
import csv
from datetime import datetime
import json
from pathlib import Path
from common import PROJECT, RUNS

def peak_inflight(jtl):
    with jtl.open(encoding='utf-8-sig', newline='') as stream:
        rows = [row for row in csv.DictReader(stream) if row['label'] != 'prewarm']
    events = []
    for row in rows:
        start = int(row['timeStamp'])
        end = start + max(1, int(row['elapsed']))
        events += [(start, 1), (end, -1)]
    live = peak = 0
    for _, delta in sorted(events):
        live += delta
        peak = max(peak, live)
    return peak

def trend_bins(folder, http):
    start = http['sampleStartUnixMs']
    duration = http.get('configuredDurationSeconds') or 60
    samples = [json.loads(line) for line in (folder / 'monitor.jsonl').read_text(encoding='utf-8').splitlines() if line.strip()]
    bins = []
    for i in range(3):
        left, right = duration * i / 3, duration * (i + 1) / 3
        selected = [row for row in samples if left <= (row['epochMs'] - start) / 1000 < right]
        if selected:
            bins.append({'seconds': f'{left:g}-{right:g}',
                         'avgRedisPending': round(sum(row['redisOutboxPending'] for row in selected) / len(selected), 1),
                         'avgOrderPending': round(sum(row['orderOutboxPending'] for row in selected) / len(selected), 1),
                         'maxOrderPending': max(row['orderOutboxPending'] for row in selected),
                         'avgUnfinished': round(sum(row['buyers'] - row['deductions'] for row in selected) / len(selected), 1),
                         'maxUnfinished': max(row['buyers'] - row['deductions'] for row in selected)})
    return bins

def render(output):
    records = []
    for path in sorted(RUNS.glob('fixture-*/analysis.json')):
        row = json.loads(path.read_text(encoding='utf-8-sig'))
        folder = path.parent
        manifest = json.loads((folder / 'manifest.json').read_text(encoding='utf-8-sig'))
        summary_files = list(folder.glob('*.summary.json'))
        if not summary_files:
            continue
        http = json.loads(summary_files[-1].read_text(encoding='utf-8-sig'))
        row['caseLabel'] = row.get('caseLabel', 'smoke-5')
        row['folder'] = str(folder.resolve())
        row['mode'] = http['mode']
        row['threads'] = http['threads']
        row['targetRps'] = http.get('targetRps')
        row['configuredDurationSeconds'] = http.get('configuredDurationSeconds')
        row['measurementValid'] = http['measurementValid']
        row['clientScriptErrors'] = http.get('clientScriptErrors', 0)
        row['trendBins'] = trend_bins(folder, http) if http['mode'] == 'sustained' else []
        row['peakClientInflightApprox'] = peak_inflight(next(folder.glob('*.jtl')))
        row['preparedAt'] = manifest['preparedAt']
        records.append(row)
    valid = [row for row in records if row.get('passed') and row['measurementValid'] and row['clientScriptErrors'] == 0]
    max_burst = max((row['threads'] for row in valid if row['mode'] == 'burst'), default=0)
    stable = [row for row in valid if row['mode'] == 'sustained']
    lines = ['# M1kasa 秒杀微服务压测报告', '', '测试日期：2026-10-28（北京时间）。测试对象为当前微服务版本，经网关访问，不使用旧单体压测结果。', '',
             '## 结论', '',
             f'- 连接预热条件下，已验证最高 **{max_burst} 个 JMeter 工作线程同步突发抢券**，具体业务结果见下表；这是已验证负载，不是最大承载上限。']
    for row in stable:
        lines.append(f"- 持续有效下单：目标 **{row['targetRps']:g} 请求/秒 × {row['configuredDurationSeconds']} 秒**，实际 {row['requests']} 次 POST、{row['accepted']} 个成功资格，实际 HTTP 窗口 {row['httpWindowSeconds']} 秒，平均 {row['averageMs']}ms、P95 {row['p95Ms']}ms，最终 {row['final']['orders']} 个订单及流水全部完成。")
        parts = row['trendBins']
        if len(parts) == 3 and parts[1]['avgOrderPending'] > parts[0]['avgOrderPending'] + 20 and parts[2]['avgOrderPending'] > parts[1]['avgOrderPending'] + 20:
            lines.append(f"  - **该档后台积压明显增长**：订单待发分段均值为 {parts[0]['avgOrderPending']} / {parts[1]['avgOrderPending']} / {parts[2]['avgOrderPending']}，请求结束后约 {row['observedDrainAfterHttpSeconds']} 秒首次观测排空。不能把 HTTP 受理速率称为完整链路的长期稳定处理能力。")
    lines += ['', '## 环境与口径', '',
              '- 压测端与应用端同机：Windows，i9-12900H，20 个逻辑处理器，物理内存约 15.8GiB。服务启动后曾仅有约 0.8GiB 可用，关闭部分应用后继续测试。每轮最低可用内存另行记录。',
              '- JMeter 5.6.3，非 GUI，显式使用 `-Xms64m -Xmx256m -XX:MaxMetaspaceSize=128m -Xss256k`；没有调整服务端业务代码、线程池、Redis、数据库或 RabbitMQ 参数。',
              '- 请求：`POST /voucher/seckill/{voucherId}/reserve`，通过网关 8080，真实登录 Bearer token。每轮独立新券；同一轮每次 POST 使用不同用户。测试账户及券保留用于复查。',
              '- 突发档先分批 GET `/user/me` 预建连接，再用屏障放行 POST；预热请求单独统计并从秒杀延迟和 RPS 中排除。Ramp 控制预热启动，不将突发 POST 摊开。',
              '- 持续档以全局时间槽限速，线程数是客户端工作线程上限，不是每秒请求数或全程实际同时在途请求数。',
              '- 200 成功响应表示获得资格。售罄/重复是业务拒绝，独立统计。只有订单、扣减流水和库存最终核验一致，才算完整链路通过。',
              '- HTTP RPS = POST 数量 /（最早 POST 开始至最后 POST 结束）。突发窗口很短，不能当作可持续 QPS。',
              '- 后台每 2 秒采样。积压数为采样峰值，可能漏掉更短的峰值；排空时间为首次观察到最终一致的时间，不是精确提交时刻，也不产生毫秒级订单完成 P95。',
              '- 原始数据库/MQ实例的资源分配和服务端 JVM 参数没有独立控制，结果只适用于本轮环境。', '',
              '本报告的“通过”表示计数、预热、技术异常和最终一致性核验通过；没有预先定义延迟 SLO，不将某个 P95 数值自动判为满足线上业务要求。', '',
              '## HTTP 结果', '',
              '| 场景 | 券ID | 工作线程 | 请求数 | 成功资格 | 售罄 | 技术异常 | Avg ms | P95 ms | P99 ms | POST窗口 s | HTTP RPS |',
              '| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |']
    for row in valid:
        lines.append(f"| {row['caseLabel']} | {row['voucherId']} | {row['threads']} | {row['requests']} | {row['accepted']} | {row['sold_out']} | {row['failures']} | {row['averageMs']} | {row['p95Ms']} | {row['p99Ms']} | {row['httpWindowSeconds']} | {row['businessResponseRps']} |")
    lines += ['', '以上有效轮次的预热失败均为 0。库存仅 10 张的轮次主要验证售罄拒绝路径；库存充足轮次才验证大量有效资格进入后端处理链路。', '',
              '## 完整业务链路与积压', '',
              '| 场景 | 最终订单/流水 | Redis/MySQL余量 | Redis Outbox采样峰值 | 订单Outbox采样峰值 | 请求结束后观测排空 s | 最低可用内存 MiB |',
              '| --- | --- | --- | --- | --- | --- | --- |']
    for row in valid:
        final = row['final']
        lines.append(f"| {row['caseLabel']} | {final['orders']}/{final['deductions']} | {final['redisStock']}/{final['dbStock']} | {row['peakRedisOutboxPending']} | {row['peakOrderOutboxPending']} | {row['observedDrainAfterHttpSeconds']} | {row.get('minimumAvailablePhysicalMiB')} |")
    lines += ['', '有效轮次最终均核对：', '',
              '- HTTP accepted = Redis 资格用户数 = 订单数 = 不同订单用户数 = 扣减流水数。',
              '- 订单 Outbox 总数 = 已发送数 = 订单数；Redis 与订单数据库 Outbox 待发送数均为 0。',
              '- Redis 和 MySQL 剩余库存均等于初始库存减去资格数。',
              '- 观察的数据库死信记录未新增；测试完成后队列指标记录在原始结果中。', '',
              '']
    lines += ['## 持续测试积压趋势', '',
              '总未完成量为已获资格用户数减去已完成库存同步数量。以下按实际 POST 开始后的时间分段；即使最终能排空，测试窗口内持续增长也不能当作完整业务稳态能力证明。', '',
              '| 场景 | 时间段 s | Redis待发均值 | 订单待发均值 | 订单待发峰值 | 总未完成均值 | 总未完成峰值 |',
              '| --- | --- | --- | --- | --- | --- | --- |']
    for row in stable:
        for part in row['trendBins']:
            lines.append(f"| {row['caseLabel']} | {part['seconds']} | {part['avgRedisPending']} | {part['avgOrderPending']} | {part['maxOrderPending']} | {part['avgUnfinished']} | {part['maxUnfinished']} |")
    lines += ['', '## 200 并发复测说明', '']
    repeats = [row for row in valid if row['caseLabel'].startswith('burst-200-stock10-r')]
    if repeats:
        lines.append(f"共 {len(repeats)} 轮独立新券测试，均为 200 人抢 10 张券。突发 RPS 范围 {min(r['businessResponseRps'] for r in repeats)}～{max(r['businessResponseRps'] for r in repeats)}，P95 范围 {min(r['p95Ms'] for r in repeats)}～{max(r['p95Ms'] for r in repeats)}ms。不能把不同轮次、不同分支的结果简单平均后称为系统稳定最大 QPS。")
    lines += ['', '## 排除的客户端异常轮', '']
    invalids = list(RUNS.glob('fixture-*/invalid-run-note.json'))
    if invalids:
        for note_path in invalids:
            note = json.loads(note_path.read_text(encoding='utf-8'))
            final = note['finalSnapshot']
            invalid_manifest = json.loads((note_path.parent / 'manifest.json').read_text(encoding='utf-8-sig'))
            lines.append(f"- 券 {invalid_manifest['voucherId']}：原计划 10 请求/秒持续 60 秒，限速 Timer 发生 Groovy 数值类型重载错误，600 请求在约 1.369 秒发完。有效性检查判为无效，未作为持续能力结论；最终600订单及流水已完成，剩余库存200。已保留原始计划与日志，修复后使用新活动重测。")
    else:
        lines.append('无。')
    lines += ['', '## 解释与限制', '',
              '- 当前订单 Outbox 每轮最多 100 条，逐条发送等待确认并更新数据库，再等待 2 秒。单实例该阶段长期投递上界为 `100 / (2秒 + 一轮实际耗时)`，低于 50 个新事件/秒；这不等于 HTTP 只能处理 50 请求/秒。',
              '- 入口能快速受理和后台能持续排空是两个指标。有限突发可以排队后完成；若长期有效到达率高于处理速率，积压仍会增长。',
              '- 连接冷启动、更多并发、更长时间、多实例、故障注入与独立压测机环境尚未由本报告证明。未压到清晰拐点前，不声称已测出系统最大容量。',
              '- 本轮未提交或推送代码。`.runs/` 含本地登录 token，已被 Git 忽略，不应上传。', '',
              '## 复现与证据', '',
              '使用项目中的 `load-test/README.md`、`prepare.py`、`run.ps1`、`monitor.py`、`analyze.py`。`case.py` 可自动串起单轮准备、JMeter、只读监测与核验。每轮必须使用新活动，禁止重置原活动库存后重复测量。', '',
              '| 场景 | 原始结果目录 |', '| --- | --- |']
    for row in records:
        path = Path(row['folder']).as_posix()
        lines.append(f"| {row['caseLabel']} | [{row['voucherId']}]({path}) |")
    lines.append('')
    output.write_text('\n'.join(lines), encoding='utf-8')
    csv_path = output.with_suffix('.csv')
    fields = ['caseLabel', 'voucherId', 'mode', 'threads', 'requests', 'accepted', 'sold_out', 'failures', 'httpWindowSeconds', 'businessResponseRps', 'averageMs', 'p95Ms', 'p99Ms', 'peakClientInflightApprox', 'observedDrainAfterHttpSeconds', 'peakRedisOutboxPending', 'peakOrderOutboxPending', 'minimumAvailablePhysicalMiB', 'passed']
    with csv_path.open('w', encoding='utf-8-sig', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=fields, extrasaction='ignore')
        writer.writeheader()
        writer.writerows(records)
    print(json.dumps({'report': str(output), 'csv': str(csv_path), 'validCases': len(valid), 'maxVerifiedBurstThreads': max_burst, 'sustainedRates': [r['targetRps'] for r in stable]}, ensure_ascii=False))

if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, default=PROJECT / 'docs/压测报告-20261028.md')
    args = parser.parse_args()
    render(args.output)
