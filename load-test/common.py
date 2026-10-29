"""Local benchmark helpers. Credentials stay in memory; never print them."""
import base64
import ctypes
import http.client
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
PROJECT = Path(os.environ.get('M1KASA_PROJECT_ROOT', str(HERE.parent if (HERE.parent / 'M1kasa-backend').is_dir() else Path('D:/M1kasa'))))
RUNS = HERE / '.runs'

def memory_info():
    if os.name != 'nt':
        return {}
    class MemoryStatus(ctypes.Structure):
        _fields_ = [('length', ctypes.c_ulong), ('load', ctypes.c_ulong)] + [(name, ctypes.c_ulonglong) for name in ['totalPhysical', 'availablePhysical', 'commitLimit', 'availableCommit', 'totalVirtual', 'availableVirtual', 'reserved']]
    status = MemoryStatus()
    status.length = ctypes.sizeof(status)
    if not ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(status)):
        return {}
    return {'memoryLoadPercent': status.load, 'availablePhysicalMiB': round(status.availablePhysical / 1024**2), 'availableCommitMiB': round(status.availableCommit / 1024**2)}

class Config:
    def __init__(self, project=PROJECT):
        self.project = Path(project)
        self.environment = {}
        path = self.project / '.idea/workspace.xml'
        if path.is_file():
            root = ET.parse(path).getroot()
            for node in root.findall(".//component[@name='RunManager']/configuration"):
                self.environment[node.get('name', '')] = {entry.get('name'): entry.get('value', '') for entry in node.findall('./envs/env')}

    def get(self, service, *path, default=None):
        source = self.project / 'M1kasa-backend' / service / 'src/main/resources/application.yaml'
        stack = []
        for line in source.read_text(encoding='utf-8-sig').splitlines():
            match = re.match(r'^( *)([A-Za-z0-9_.-]+):(?:\s+(.*))?\s*$', line)
            if not match:
                continue
            indent, key, value = len(match[1]), match[2], match[3]
            while stack and stack[-1][0] >= indent:
                stack.pop()
            current = [part[1] for part in stack] + [key]
            if current == list(path):
                value = (value or '').strip()
                if value.startswith('"') and value.endswith('"'):
                    value = json.loads(value)
                elif value.startswith("'") and value.endswith("'"):
                    value = value[1:-1].replace("''", "'")
                else:
                    value = re.split(r'\s+#', value, maxsplit=1)[0].strip()
                config_name = {'m1kasa-user-service': 'M1kasaUserServiceApplication', 'm1kasa-voucher-service': 'M1kasaVoucherServiceApplication', 'm1kasa-order-service': 'M1kasaOrderServiceApplication', 'm1kasa-gateway': 'M1kasaGatewayApplication'}.get(service, '')
                local_environment = self.environment.get(config_name, {})
                def replace(match):
                    name, separator, fallback = match[1].partition(':')
                    if name in os.environ:
                        return os.environ[name]
                    if name in local_environment:
                        return local_environment[name]
                    if separator:
                        return fallback
                    raise RuntimeError('Missing configuration environment variable: ' + name)
                return re.sub(r'\$\{([^}]+)\}', replace, value)
            if not value:
                stack.append((indent, key))
        if default is not None:
            return default
        raise RuntimeError('Missing configuration property: ' + '.'.join(path))

class Redis:
    def __init__(self, config, service='m1kasa-user-service'):
        self.sock = socket.create_connection((config.get(service, 'spring', 'data', 'redis', 'host'), int(config.get(service, 'spring', 'data', 'redis', 'port', default='6379'))), timeout=8)
        self.stream = self.sock.makefile('rb')
        password = config.get(service, 'spring', 'data', 'redis', 'password', default='')
        username = config.get(service, 'spring', 'data', 'redis', 'username', default='')
        if password:
            self.command('AUTH', *([username, password] if username else [password]))
        database = int(config.get(service, 'spring', 'data', 'redis', 'database', default='0'))
        if database:
            self.command('SELECT', database)

    def _read(self):
        marker = self.stream.read(1)
        if not marker:
            raise RuntimeError('Redis connection closed')
        line = self.stream.readline().rstrip(b'\r\n')
        if marker == b'-':
            raise RuntimeError('Redis command failed: ' + line.decode('utf-8', 'replace'))
        if marker == b'+':
            return line.decode('utf-8', 'replace')
        if marker == b':':
            return int(line)
        if marker == b'$':
            length = int(line)
            if length < 0:
                return None
            data = self.stream.read(length)
            self.stream.read(2)
            return data.decode('utf-8', 'replace')
        if marker == b'*':
            length = int(line)
            return None if length < 0 else [self._read() for _ in range(length)]
        raise RuntimeError('Unsupported Redis reply')

    def command(self, *parts):
        encoded = [str(part).encode('utf-8') for part in parts]
        self.sock.sendall(b'*' + str(len(encoded)).encode() + b'\r\n' + b''.join(b'$' + str(len(part)).encode() + b'\r\n' + part + b'\r\n' for part in encoded))
        return self._read()

    def close(self):
        self.stream.close()
        self.sock.close()

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()

class Api:
    def __init__(self, base_url):
        parsed = urllib.parse.urlsplit(base_url)
        if parsed.scheme != 'http' or parsed.path not in ('', '/'):
            raise RuntimeError('This local test expects an HTTP origin without a path')
        self.connection = http.client.HTTPConnection(parsed.hostname, parsed.port or 80, timeout=15)

    def call(self, path, method='GET', payload=None, token=None):
        headers = {'Content-Type': 'application/json', 'Connection': 'keep-alive'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        body = None if payload is None else json.dumps(payload, ensure_ascii=False).encode('utf-8')
        self.connection.request(method, path, body=body, headers=headers)
        response = self.connection.getresponse()
        data = response.read()
        try:
            parsed = json.loads(data)
        except ValueError:
            raise RuntimeError(f'HTTP {response.status}, non-JSON response for {path.split("?")[0]}')
        return response.status, parsed

    def close(self):
        self.connection.close()

class Db:
    """One small read-only JDBC worker; avoids a new JVM for each observation."""
    def __init__(self, config):
        env = os.environ.copy()
        for short, service in [('USER', 'm1kasa-user-service'), ('VOUCHER', 'm1kasa-voucher-service'), ('ORDER', 'm1kasa-order-service')]:
            for field, suffix in [('url', 'URL'), ('username', 'USER'), ('password', 'PASSWORD')]:
                env['LOAD_DB_' + short + '_' + suffix] = config.get(service, 'spring', 'datasource', field)
        jar_root = Path(os.environ.get('MAVEN_REPO', 'D:/dev-cache/maven-repository')) / 'com/mysql/mysql-connector-j'
        jars = sorted(jar_root.glob('*/*.jar'))
        jars = [path for path in jars if not path.name.endswith(('-sources.jar', '-javadoc.jar'))]
        if not jars:
            raise RuntimeError('MySQL JDBC driver not found; set MAVEN_REPO')
        self.process = subprocess.Popen(['java', '-Xms16m', '-Xmx64m', '--class-path', str(jars[-1]), str(HERE / 'LoadDb.java')], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding='utf-8', env=env)

    def query(self, database, sql):
        if database not in ('user', 'voucher', 'order') or not sql.lstrip().upper().startswith(('SELECT ', 'SHOW ')):
            raise RuntimeError('Only predefined read-only database queries are allowed')
        self.process.stdin.write(database + '\t' + sql.replace('\n', ' ') + '\n')
        self.process.stdin.flush()
        line = self.process.stdout.readline()
        if not line:
            raise RuntimeError('Read-only JDBC worker stopped: ' + self.process.stderr.read()[-800:])
        result = json.loads(line)
        if not result.get('ok'):
            raise RuntimeError('Read-only JDBC query failed: ' + result.get('error', 'unknown'))
        return result['rows']

    def close(self):
        if self.process.poll() is None:
            self.process.stdin.close()
            try:
                self.process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.process.terminate()

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()

def rabbit_queues(config):
    service = 'm1kasa-voucher-service'
    host = config.get(service, 'spring', 'rabbitmq', 'host')
    username = config.get(service, 'spring', 'rabbitmq', 'username')
    password = config.get(service, 'spring', 'rabbitmq', 'password')
    vhost = config.get(service, 'spring', 'rabbitmq', 'virtual-host', default='/')
    port = int(os.environ.get('RABBITMQ_MANAGEMENT_PORT', '15672'))
    url = f'http://{host}:{port}/api/queues/' + urllib.parse.quote(vhost, safe='')
    auth = base64.b64encode((username + ':' + password).encode()).decode()
    request = urllib.request.Request(url, headers={'Authorization': 'Basic ' + auth})
    with urllib.request.urlopen(request, timeout=5) as response:
        rows = json.load(response)
    names = {'m1kasa.seckill.order.queue', 'm1kasa.seckill.order.dlq', 'm1kasa.voucher.stock.deduct.queue', 'm1kasa.voucher.stock.dlq'}
    return [{key: row.get(key) for key in ['name', 'messages', 'messages_ready', 'messages_unacknowledged', 'consumers']} for row in rows if row['name'] in names]

def snapshot(db, redis, voucher_id):
    voucher_id = int(voucher_id)
    order = db.query('order', f'SELECT COUNT(*) AS total, COUNT(DISTINCT user_id) AS distinct_users FROM tb_voucher_order WHERE voucher_id={voucher_id}')[0]
    outbox = db.query('order', f'SELECT COUNT(*) AS total, COALESCE(SUM(status=0),0) AS pending, COALESCE(SUM(status=1),0) AS sent FROM tb_seckill_order_outbox WHERE voucher_id={voucher_id}')[0]
    stock = db.query('voucher', f'SELECT stock FROM tb_seckill_voucher WHERE voucher_id={voucher_id}')
    deduction = db.query('voucher', f'SELECT COUNT(*) AS total FROM tb_seckill_stock_deduction WHERE voucher_id={voucher_id}')[0]
    activity = f'm1kasa:seckill:activity:{{{voucher_id}}}'
    return {'epochMs': int(time.time() * 1000), 'orders': int(order['total']), 'distinctOrderUsers': int(order['distinct_users']),
            'orderOutboxTotal': int(outbox['total']), 'orderOutboxPending': int(outbox['pending']), 'orderOutboxSent': int(outbox['sent']),
            'deductions': int(deduction['total']), 'dbStock': int(stock[0]['stock']) if stock else None,
            'redisStock': int(redis.command('HGET', activity, 'stock')),
            'buyers': int(redis.command('SCARD', f'm1kasa:seckill:buyers:{{{voucher_id}}}')),
            'redisOutboxPending': int(redis.command('HLEN', f'm1kasa:seckill:outbox:{{{voucher_id}}}'))}

def converged(row, initial_stock):
    n = row['buyers']
    return (row['orders'] == row['distinctOrderUsers'] == row['orderOutboxTotal'] == row['orderOutboxSent'] == row['deductions'] == n
            and row['redisOutboxPending'] == row['orderOutboxPending'] == 0
            and row['redisStock'] == row['dbStock'] == initial_stock - n)
