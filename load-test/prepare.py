"""Prepare isolated activities and real API login sessions; never reset existing stock."""
import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import csv
from datetime import datetime, timedelta
import json
from pathlib import Path
import secrets
import time
import urllib.parse
from common import Api, Config, Db, Redis, RUNS

def prepare_pool(config, users, base_url):
    folder = RUNS / ('pool-' + datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
    folder.mkdir(parents=True, exist_ok=False)
    candidates = set()
    with Db(config) as db:
        while len(candidates) < users:
            batch = set()
            while len(batch) < min(300, users - len(candidates)):
                batch.add('199' + f'{secrets.randbelow(100_000_000):08d}')
            quoted = ','.join("'" + phone + "'" for phone in batch)
            existing = {row['phone'] for row in db.query('user', 'SELECT phone FROM tb_user WHERE phone IN (' + quoted + ')')}
            candidates.update(batch - existing)
    phones = list(candidates)[:users]
    started = time.time()
    def login(phone):
        api = Api(base_url)
        try:
            with Redis(config) as redis:
                status, sent = api.call('/user/code?' + urllib.parse.urlencode({'phone': phone}), 'POST')
                if status != 200 or sent.get('success') is not True:
                    raise RuntimeError('Local SMS preparation failed; enable the user service local profile')
                code = redis.command('GET', 'm1kasa:login:code:' + phone)
                if not code:
                    raise RuntimeError('Test login code was not found in the configured Redis database')
                status, result = api.call('/user/login', 'POST', {'phone': phone, 'code': code})
                if status != 200 or result.get('success') is not True or not isinstance(result.get('data'), str):
                    raise RuntimeError('Test login API failed')
                token = result['data']
                user_id = redis.command('GET', 'm1kasa:login:token:' + token)
                if not user_id:
                    raise RuntimeError('Real login session was not persisted')
                return {'userId': str(user_id), 'phone': phone, 'token': token}
        finally:
            api.close()
    records = []
    with ThreadPoolExecutor(max_workers=4) as executor:
        jobs = [executor.submit(login, phone) for phone in phones]
        for job in as_completed(jobs):
            records.append(job.result())
            if len(records) % 50 == 0:
                print(f'Logged in {len(records)}/{users} isolated test users', flush=True)
    pool = {'users': records, 'baseUrl': base_url, 'createdAtEpochMs': int(started * 1000),
            'expiresAtEpochMs': int((started + 1800) * 1000)}
    (folder / 'pool.json').write_text(json.dumps(pool, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f'User pool ready: {folder}', flush=True)
    return folder, pool

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--users', type=int, default=5)
    parser.add_argument('--stock', type=int)
    parser.add_argument('--pool', type=Path)
    parser.add_argument('--pool-only', action='store_true')
    parser.add_argument('--base-url', default='http://127.0.0.1:8080')
    args = parser.parse_args()
    if not 1 <= args.users <= 5000:
        parser.error('users must be 1..5000')
    stock = args.users if args.stock is None else args.stock
    if not 1 <= stock <= 10000:
        parser.error('stock must be 1..10000')
    config = Config()
    api = Api(args.base_url)
    try:
        status, types = api.call('/shop-type/list')
        if status != 200 or types.get('success') is not True:
            raise RuntimeError('Gateway/shop service is not ready')
        if args.pool:
            pool_folder = args.pool.parent if args.pool.name == 'pool.json' else args.pool
            pool = json.loads((pool_folder / 'pool.json').read_text(encoding='utf-8-sig'))
        else:
            pool_folder, pool = prepare_pool(config, args.users, args.base_url)
        if len(pool['users']) < args.users:
            raise RuntimeError('Pool has fewer users than requested')
        if time.time() * 1000 > pool['expiresAtEpochMs'] - 120_000:
            raise RuntimeError('User pool is expiring; prepare a new pool')
        if args.pool_only:
            print('Pool: ' + str(pool_folder.resolve()))
            return
        users = pool['users'][:args.users]
        token = users[0]['token']
        status, me = api.call('/user/me', token=token)
        if status != 200 or me.get('success') is not True:
            raise RuntimeError('Prepared token is not valid for current gateway/user configuration')
        status, orders = api.call('/order/of/me?current=1&size=1', token=token)
        if status != 200 or orders.get('success') is not True:
            raise RuntimeError('Order query service is not ready')
        with Db(config) as db:
            now = datetime.fromisoformat(db.query('voucher', "SELECT DATE_FORMAT(CURRENT_TIMESTAMP,'%Y-%m-%dT%H:%i:%s') AS now")[0]['now'])
        begin = now + timedelta(seconds=20)
        end = begin + timedelta(hours=1)
        payload = {'shopId': 2, 'title': 'LOAD_' + now.strftime('%Y%m%d_%H%M%S_%f'), 'subTitle': 'isolated benchmark fixture',
                   'rules': 'Local benchmark only', 'payValue': 100, 'actualValue': 200, 'stock': stock,
                   'beginTime': begin.isoformat(timespec='seconds'), 'endTime': end.isoformat(timespec='seconds')}
        status, created = api.call('/voucher/seckill', 'POST', payload, token)
        if status != 201 or created.get('success') is not True:
            raise RuntimeError(f'Fixture create failed (HTTP {status}); enable voucher local profile')
        voucher_id = str(int(created['data']))
        folder = RUNS / ('fixture-' + now.strftime('%Y%m%d-%H%M%S-') + secrets.token_hex(3))
        folder.mkdir(parents=True, exist_ok=False)
        (folder / 'created-activity.json').write_text(json.dumps({'voucherId': voucher_id, **payload}, indent=2), encoding='utf-8')
        status, initialized = api.call('/voucher/seckill/' + voucher_id + '/init', 'POST', token=token)
        if status != 200 or initialized.get('success') is not True or initialized.get('data') is not True:
            raise RuntimeError('Fresh activity initialization failed; fixture retained for inspection')
        with (folder / 'tokens.csv').open('w', encoding='utf-8', newline='') as stream:
            csv.writer(stream).writerows([[user['token']] for user in users])
        (folder / 'users.json').write_text(json.dumps([{'userId': u['userId'], 'phone': u['phone']} for u in users], indent=2), encoding='utf-8')
        with Redis(config, 'm1kasa-voucher-service') as redis:
            activity = f'm1kasa:seckill:activity:{{{voucher_id}}}'
            begin_ms = int(redis.command('HGET', activity, 'beginTime'))
            if int(redis.command('HGET', activity, 'stock')) != stock:
                raise RuntimeError('Initialized stock does not match the fresh fixture')
            print(f'Voucher {voucher_id}: stock={stock}, users={args.users}; waiting for activity start', flush=True)
            while True:
                seconds, micros = redis.command('TIME')
                current_ms = int(seconds) * 1000 + int(micros) // 1000
                if current_ms >= begin_ms + 500:
                    break
                time.sleep(min(1.0, (begin_ms + 500 - current_ms) / 1000))
        manifest = {'schemaVersion': 1, 'voucherId': voucher_id, 'users': args.users, 'stock': stock, 'baseUrl': args.base_url,
                    'preparedAt': datetime.now().isoformat(timespec='seconds'), 'beginTime': payload['beginTime'],
                    'endTime': payload['endTime'], 'pool': str(pool_folder.resolve()), 'poolExpiresAtEpochMs': pool['expiresAtEpochMs'],
                    'ready': True}
        path = folder / 'manifest.json'
        path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
        print('Manifest: ' + str(path.resolve()), flush=True)
    finally:
        api.close()

if __name__ == '__main__':
    main()
