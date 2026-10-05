// End-to-end check for the agent API path contract.
//
// The Android client (JarvisAiClient.kt) builds this request and POSTs it to
// <gateway>/v1/agent. This test starts the real backend pointed at a mock
// OpenAI Responses API, then sends the exact same request shape and asserts
// the path is routed and answered with 200.
import http from 'node:http';
import net from 'node:net';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));

function freePort() {
  return new Promise((resolve, reject) => {
    const srv = net.createServer();
    srv.on('error', reject);
    srv.listen(0, '127.0.0.1', () => {
      const { port } = srv.address();
      srv.close(() => resolve(port));
    });
  });
}

function startMockOpenAI() {
  const expectedAction = {
    action: 'say',
    speech: 'Salom, tushundim.',
    text: '',
    target: '',
    x: 0.5,
    y: 0.5,
    direction: '',
    sensitive: false
  };
  const server = http.createServer((req, res) => {
    let body = '';
    req.on('data', (c) => (body += c));
    req.on('end', () => {
      // Guard: the backend must call the Responses API on our mock base URL.
      if (!req.url.endsWith('/responses')) {
        res.writeHead(404).end('bad path');
        return;
      }
      res.writeHead(200, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ output_text: JSON.stringify(expectedAction) }));
    });
  });
  return new Promise((resolve) => {
    server.listen(0, '127.0.0.1', () => resolve({ server, port: server.address().port, expectedAction }));
  });
}

function waitForHealth(port, timeoutMs = 8000) {
  const deadline = Date.now() + timeoutMs;
  return new Promise((resolve, reject) => {
    const attempt = () => {
      const req = http.get({ host: '127.0.0.1', port, path: '/health' }, (res) => {
        res.resume();
        if (res.statusCode === 200) return resolve();
        retry();
      });
      req.on('error', retry);
      function retry() {
        if (Date.now() > deadline) return reject(new Error('backend did not become healthy'));
        setTimeout(attempt, 200);
      }
    };
    attempt();
  });
}

function post(port, path, payload) {
  const body = JSON.stringify(payload);
  return new Promise((resolve, reject) => {
    const req = http.request(
      {
        host: '127.0.0.1',
        port,
        path,
        method: 'POST',
        headers: { 'content-type': 'application/json; charset=utf-8', 'content-length': Buffer.byteLength(body) }
      },
      (res) => {
        let raw = '';
        res.on('data', (c) => (raw += c));
        res.on('end', () => resolve({ status: res.statusCode, body: raw }));
      }
    );
    req.on('error', reject);
    req.end(body);
  });
}

function assert(cond, message) {
  if (!cond) throw new Error(`ASSERTION FAILED: ${message}`);
}

async function main() {
  const { server: mock, port: mockPort, expectedAction } = await startMockOpenAI();
  const backendPort = await freePort();
  const child = spawn(process.execPath, ['server.mjs'], {
    cwd: here,
    env: {
      ...process.env,
      PORT: String(backendPort),
      OPENAI_API_KEY: 'test-key',
      OPENAI_BASE_URL: `http://127.0.0.1:${mockPort}/v1`
    },
    stdio: ['ignore', 'pipe', 'pipe']
  });

  let backendOut = '';
  child.stdout.on('data', (d) => (backendOut += d));
  child.stderr.on('data', (d) => (backendOut += d));

  const cleanup = () => {
    child.kill('SIGTERM');
    mock.close();
  };

  try {
    await waitForHealth(backendPort);

    // Exact request shape produced by JarvisAiClient.kt.
    const payload = {
      request: 'YouTube ochib ber',
      screen_text: 'Home screen | YouTube',
      package_name: 'com.android.launcher',
      notification_title: '',
      notification_text: '',
      screenshot_base64: ''
    };

    const canonical = await post(backendPort, '/v1/agent', payload);
    assert(canonical.status === 200, `GET /v1/agent expected 200, got ${canonical.status}: ${canonical.body}`);
    const action = JSON.parse(canonical.body);
    assert(action.action === expectedAction.action, `action mismatch: ${canonical.body}`);
    assert(typeof action.speech === 'string' && action.speech.length > 0, 'speech missing');

    const legacy = await post(backendPort, '/api/agent', payload);
    assert(legacy.status === 200, `alias /api/agent expected 200, got ${legacy.status}`);

    const unknown = await post(backendPort, '/nope', payload);
    assert(unknown.status === 404, `unknown path expected 404, got ${unknown.status}`);

    assert(backendOut.includes('[agent] 200'), 'backend did not log the successful agent call');

    console.log('PASS: Android client path /v1/agent reaches the backend and returns a valid action');
    console.log(`  response: ${canonical.body}`);
    console.log(`  backend log: ${backendOut.split('\n').find((l) => l.includes('[agent] 200'))}`);
    cleanup();
    process.exit(0);
  } catch (err) {
    console.error('FAIL:', err.message);
    console.error('--- backend output ---\n' + backendOut);
    cleanup();
    process.exit(1);
  }
}

main();
