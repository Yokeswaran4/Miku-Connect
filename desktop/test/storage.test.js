// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Yokeswaran N
'use strict';
// Runs the WebDAV bridge against a pretend phone (a JS version of what StorageShare.kt does) on a temp folder.
const assert = require('assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const http = require('http');
const storage = require('../storage');

const root = fs.mkdtempSync(path.join(os.tmpdir(), 'yc-phone-'));
fs.mkdirSync(path.join(root, 'DCIM'));
fs.writeFileSync(path.join(root, 'DCIM', 'a.txt'), 'hello phone');
const big = Buffer.alloc(700 * 1024); for (let i = 0; i < big.length; i++) big[i] = i % 251;
fs.writeFileSync(path.join(root, 'big.bin'), big);

const phone = { enabled: true, write: false };
const E = (err, why) => ({ ok: false, err, why: why || err });
function resolve(p) {
  const parts = String(p || '').split('/').filter(Boolean);
  if (parts.some((x) => x === '..' || x === '.' || x.includes('\0'))) return null;
  return path.join(root, ...parts);
}
function phoneHandle(m) {
  if (!phone.enabled) return E('disabled', 'Storage sharing is off on the phone.');
  const w = () => (phone.write ? null : E('readonly', 'Changes are not allowed on the phone.'));
  try {
    switch (m.type) {
      case 'fs-info': return { ok: true, free: 1000, total: 5000 };
      case 'fs-stat': { const f = resolve(m.path); if (!f) return E('badpath'); const s = fs.statSync(f); return { ok: true, st: { d: s.isDirectory(), s: s.size, m: s.mtimeMs } }; }
      case 'fs-list': { const f = resolve(m.path); if (!f) return E('badpath'); return { ok: true, entries: fs.readdirSync(f).map((n) => { const s = fs.statSync(path.join(f, n)); return { n, d: s.isDirectory(), s: s.size, m: s.mtimeMs }; }) }; }
      case 'fs-read': { const f = resolve(m.path); if (!f) return E('badpath'); const fd = fs.openSync(f, 'r'); const b = Buffer.alloc(m.len); const n = fs.readSync(fd, b, 0, m.len, m.off); fs.closeSync(fd); return { ok: true, data: b.subarray(0, n).toString('base64') }; }
      case 'fs-write': { const x = w(); if (x) return x; const f = resolve(m.path); if (!f) return E('badpath'); const fd = fs.openSync(f, m.trunc ? 'w' : 'r+'); const b = Buffer.from(m.data, 'base64'); fs.writeSync(fd, b, 0, b.length, m.off); fs.closeSync(fd); return { ok: true }; }
      case 'fs-mkdir': { const x = w(); if (x) return x; const f = resolve(m.path); if (!f) return E('badpath'); if (fs.existsSync(f)) return E('exists'); fs.mkdirSync(f); return { ok: true }; }
      case 'fs-del': { const x = w(); if (x) return x; const f = resolve(m.path); if (!f || f === root) return E('badpath'); fs.rmSync(f, { recursive: true }); return { ok: true }; }
      case 'fs-move': { const x = w(); if (x) return x; fs.renameSync(resolve(m.from), resolve(m.to)); return { ok: true }; }
    }
  } catch (e) { return E(e.code === 'ENOENT' ? 'notfound' : 'io', e.message); }
  return E('io', 'unknown');
}

const ws = { id: 'ws1' };
const peers = new Map([['phone-1', { ws, info: { id: 'phone-1', name: 'My Phone' } }]]);
storage.init({
  peers, addLog() {}, pushState() {},
  sendTo: (w, obj) => setImmediate(() => storage.onMessage(w, Object.assign({ type: 'fs-res', rid: obj.rid }, phoneHandle(obj)))),
});

function req(method, url, { headers = {}, body } = {}) {
  return new Promise((ok, no) => {
    const u = new URL(url);
    const r = http.request({ method, hostname: u.hostname, port: u.port, path: u.pathname + u.search, headers }, (res) => {
      const c = []; res.on('data', (d) => c.push(d)); res.on('end', () => ok({ status: res.statusCode, headers: res.headers, body: Buffer.concat(c) }));
    });
    r.on('error', no);
    if (body) r.write(body);
    r.end();
  });
}

(async () => {
  let n = 0; const t = async (name, fn) => { await fn(); n++; console.log('ok  - ' + name); };
  const calls = [];
  const exec = async (cmd, args) => { calls.push([cmd, ...args]); return { err: null, stdout: 'Drive Z: is now connected', stderr: '' }; };

  await t('mount asks the phone first and reports "sharing is off"', async () => {
    phone.enabled = false;
    const r = await storage.mount('phone-1', 'My Phone', 'linux', exec);
    assert.strictEqual(r.ok, false); assert.strictEqual(r.code, 'disabled'); assert(r.hint.includes('Storage sharing'));
    assert.strictEqual(calls.length, 0);
    phone.enabled = true;
  });
  let m;
  await t('mount on Linux uses gio and a localhost dav:// address with a secret token', async () => {
    m = await storage.mount('phone-1', 'My Phone', 'linux', exec);
    assert(m.ok); assert.strictEqual(calls[0][0], 'gio'); assert(calls[0][2].startsWith('dav://127.0.0.1:'));
    assert(m.url.startsWith('http://127.0.0.1:')); assert(/\/[0-9a-f]{32}\/My%20Phone\/$/.test(m.url));
  });
  const U = m.url;
  await t('wrong token / wrong host / unknown phone are refused', async () => {
    const bad = U.replace(/[0-9a-f]{32}/, '0'.repeat(32));
    assert.strictEqual((await req('PROPFIND', bad, { headers: { Depth: '0' } })).status, 404);
    assert.strictEqual((await req('PROPFIND', U, { headers: { Host: 'evil.example', Depth: '0' } })).status, 403);
    assert.strictEqual((await req('GET', U.replace('My%20Phone', 'Other'))).status, 404);
  });
  await t('PROPFIND lists the root and folders', async () => {
    const r = await req('PROPFIND', U, { headers: { Depth: '1' } });
    assert.strictEqual(r.status, 207);
    const x = r.body.toString();
    assert(x.includes('<D:collection/>') && x.includes('DCIM') && x.includes('big.bin') && x.includes('quota-available-bytes>1000'));
    const d = (await req('PROPFIND', U + 'DCIM/', { headers: { Depth: '1' } })).body.toString();
    assert(d.includes('a.txt') && d.includes('<D:getcontentlength>11<'));
    assert.strictEqual((await req('PROPFIND', U + 'nope', { headers: { Depth: '0' } })).status, 404);
  });
  await t('GET returns the file, Range works, big files stream in chunks', async () => {
    assert.strictEqual((await req('GET', U + 'DCIM/a.txt')).body.toString(), 'hello phone');
    const r = await req('GET', U + 'DCIM/a.txt', { headers: { Range: 'bytes=6-10' } });
    assert.strictEqual(r.status, 206); assert.strictEqual(r.body.toString(), 'phone'); assert.strictEqual(r.headers['content-range'], 'bytes 6-10/11');
    const b = await req('GET', U + 'big.bin');
    assert(b.body.equals(big));
    const tail = await req('GET', U + 'big.bin', { headers: { Range: 'bytes=-1000' } });
    assert(tail.body.equals(big.subarray(big.length - 1000)));
    assert.strictEqual((await req('GET', U + 'big.bin', { headers: { Range: 'bytes=99999999-' } })).status, 416);
    assert.strictEqual((await req('HEAD', U + 'big.bin')).headers['content-length'], String(big.length));
  });
  await t('changes are refused while the phone says read-only', async () => {
    assert.strictEqual((await req('PUT', U + 'x.txt', { body: Buffer.from('x') })).status, 403);
    assert.strictEqual((await req('MKCOL', U + 'New')).status, 403);
    assert.strictEqual((await req('DELETE', U + 'big.bin')).status, 403);
    assert(fs.existsSync(path.join(root, 'big.bin')));
  });
  phone.write = true;
  await t('PUT creates (201) and overwrites (204), including multi-chunk and empty files', async () => {
    assert.strictEqual((await req('PUT', U + 'DCIM/new.txt', { body: Buffer.from('abc') })).status, 201);
    assert.strictEqual(fs.readFileSync(path.join(root, 'DCIM/new.txt'), 'utf8'), 'abc');
    const up = Buffer.alloc(600 * 1024, 7);
    assert.strictEqual((await req('PUT', U + 'DCIM/new.txt', { body: up })).status, 204);
    assert(fs.readFileSync(path.join(root, 'DCIM/new.txt')).equals(up));
    assert.strictEqual((await req('PUT', U + 'empty.bin', { body: null })).status, 201);
    assert.strictEqual(fs.statSync(path.join(root, 'empty.bin')).size, 0);
  });
  await t('MKCOL, MOVE and DELETE', async () => {
    assert.strictEqual((await req('MKCOL', U + 'Docs')).status, 201);
    assert.strictEqual((await req('MKCOL', U + 'Docs')).status, 405);
    const mv = await req('MOVE', U + 'DCIM/new.txt', { headers: { Destination: U + 'Docs/moved.txt' } });
    assert.strictEqual(mv.status, 201); assert(fs.existsSync(path.join(root, 'Docs/moved.txt')));
    assert.strictEqual((await req('MOVE', U + 'Docs/moved.txt', { headers: { Destination: U + 'big.bin', Overwrite: 'F' } })).status, 412);
    assert.strictEqual((await req('DELETE', U + 'Docs')).status, 204); assert(!fs.existsSync(path.join(root, 'Docs')));
    assert.strictEqual((await req('MOVE', U + 'big.bin', { headers: { Destination: 'http://evil/x' } })).status, 400);
  });
  await t('paths cannot climb out of the shared folder', async () => {
    const r = await req('GET', U.replace(/\/$/, '') + '/%2e%2e/%2e%2e/etc/passwd');
    assert.strictEqual(r.status, 404);
    assert.strictEqual((await req('GET', U.replace(/\/$/, '') + '/DCIM/..%2f..%2fx')).status, 404);
  });
  await t('LOCK / UNLOCK / OPTIONS / COPY behave', async () => {
    assert.strictEqual((await req('LOCK', U + 'big.bin')).status, 200);
    assert.strictEqual((await req('UNLOCK', U + 'big.bin')).status, 204);
    assert.strictEqual((await req('OPTIONS', U)).status, 200);
    assert.strictEqual((await req('COPY', U + 'big.bin', { headers: { Destination: U + 'c.bin' } })).status, 501);
  });
  await t('if the phone disconnects, requests fail fast instead of hanging', async () => {
    peers.delete('phone-1');
    assert.strictEqual((await req('GET', U + 'big.bin')).status, 502);
    peers.set('phone-1', { ws, info: { id: 'phone-1', name: 'My Phone' } });
  });
  await t('Windows mount maps a drive with net use; unmount removes it', async () => {
    await storage.unmount('phone-1', exec);
    calls.length = 0;
    const w = await storage.mount('phone-1', 'My Phone', 'win32', exec);
    assert(w.ok && w.drive === 'Z:'); assert.deepStrictEqual(calls[0].slice(0, 2), ['net', 'use']);
    assert(!calls[0][3].endsWith('/'));
    await storage.unmount('phone-1', exec);
    assert.deepStrictEqual(calls[1], ['net', 'use', 'Z:', '/delete', '/y']);
  });
  await t('without gio the address is returned for manual connection', async () => {
    const r = await storage.mount('phone-1', 'My Phone', 'linux', async () => ({ err: Object.assign(new Error('x'), { code: 'ENOENT' }), stdout: '', stderr: '' }));
    assert(r.ok && r.manual && r.url.startsWith('http://127.0.0.1:'));
  });
  console.log(`\n${n} passed`);
  await storage.unmountAll();
  fs.rmSync(root, { recursive: true });
  process.exit(0);
})().catch((e) => { console.error(e); process.exit(1); });
