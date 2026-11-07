// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Yokeswaran N
'use strict';
/*
 * Mount the storage of a paired Android phone as a drive / network location on this computer.
 *
 *   file manager  --WebDAV-->  127.0.0.1 (this app)  --fs-* messages, encrypted like everything else-->  phone
 *
 * The operating system only ever talks to a small WebDAV server that listens on 127.0.0.1 (never on the network).
 * That server turns every request into `fs-*` messages on the same encrypted link the rest of Y Connect uses, so the
 * file data never travels over the Wi-Fi in clear text and only a paired phone can answer.
 *
 * Phone side (see StorageShare.kt) - all the safety switches are on the phone:
 *  - sharing is OFF until the user turns it on there (and gives "All files access");
 *  - changing things (write, create folder, rename, delete) is a second switch, also OFF by default.
 *
 *   computer -> phone   fs-info | fs-stat {path} | fs-list {path} | fs-read {path, off, len}
 *                       fs-write {path, off, data(base64), trunc} | fs-mkdir {path} | fs-del {path} | fs-move {from, to}
 *   phone -> computer   fs-res {rid, ok, ...}   or   fs-res {rid, ok:false, err, why}
 *   err: disabled | readonly | denied | notfound | exists | badpath | io
 *
 * Every request carries a random per-run token in its URL and a Host check, so other programs or web pages on this
 * computer cannot reach the phone through the local server.
 */
const http = require('http');
const crypto = require('crypto');
const { execFile } = require('child_process');

const CHUNK = 256 * 1024;       // bytes per fs-read / fs-write message
const TIMEOUT = 20000;          // ms to wait for the phone to answer one message
const STAT_TTL = 2000;

let D = null;                   // { peers, sendTo, addLog, pushState }
const pending = new Map();      // rid -> { ws, resolve, reject, timer }
let seq = 0;

function init(deps) { D = deps; }

const mkErr = (code, why) => Object.assign(new Error(why || code), { code, why: why || code });

/* ---------- talking to the phone ---------- */
function call(peerId, op, args = {}, timeout = TIMEOUT) {
  return new Promise((resolve, reject) => {
    const p = D.peers.get(peerId);
    if (!p) return reject(mkErr('offline', 'The phone is not connected.'));
    if (pending.size > 64) return reject(mkErr('busy', 'Too many requests at once.'));
    const rid = (++seq).toString(36) + crypto.randomBytes(4).toString('hex');
    const timer = setTimeout(() => { pending.delete(rid); reject(mkErr('timeout', 'The phone did not answer.')); }, timeout);
    pending.set(rid, { ws: p.ws, resolve, reject, timer });
    D.sendTo(p.ws, Object.assign({ type: 'fs-' + op, rid }, args));
  });
}
function onMessage(ws, msg) {
  if (msg.type !== 'fs-res' || typeof msg.rid !== 'string') return;
  const p = pending.get(msg.rid);
  if (!p || p.ws !== ws) return;
  pending.delete(msg.rid);
  clearTimeout(p.timer);
  if (msg.ok) p.resolve(msg); else p.reject(mkErr(String(msg.err || 'io'), String(msg.why || msg.err || 'Failed')));
}
function onDisconnected(ws) {
  for (const [rid, p] of pending) if (p.ws === ws) { pending.delete(rid); clearTimeout(p.timer); p.reject(mkErr('offline', 'The phone disconnected.')); }
}

/* ---------- WebDAV server ---------- */
const mounts = new Map();       // slug -> peerId
const active = new Map();       // peerId -> { slug, url, via, drive? }
let server = null, port = 0, token = '';
const statCache = new Map();

const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const cleanSlug = (n) => String(n || 'phone').replace(/[^\w .()-]+/g, '_').trim().slice(0, 40) || 'phone';
function slugFor(peerId, name) {
  for (const [s, id] of mounts) if (id === peerId) return s;
  let s = cleanSlug(name), n = 2;
  while (mounts.has(s)) s = cleanSlug(name) + ' ' + n++;
  mounts.set(s, peerId);
  return s;
}
const enc = (segs) => segs.map(encodeURIComponent).join('/');
const base = (slug) => `/${token}/${encodeURIComponent(slug)}`;

const HTTP_FOR = { notfound: 404, denied: 403, readonly: 403, disabled: 403, exists: 405, badpath: 400, offline: 502, timeout: 504, busy: 503, io: 500 };
const statusOf = (e) => HTTP_FOR[e && e.code] || 500;

function parseTarget(urlPath) {
  let segs;
  try { segs = urlPath.split('?')[0].split('/').filter((x, i) => i === 0 ? false : true).map(decodeURIComponent); } catch { return null; }
  while (segs.length && segs[segs.length - 1] === '') segs.pop();
  segs = segs.filter((x) => x !== '');
  if (segs.length < 2) return null;
  const a = Buffer.from(segs[0]), b = Buffer.from(token);
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) return null;
  const peerId = mounts.get(segs[1]);
  if (!peerId) return null;
  const rest = segs.slice(2);
  if (rest.some((s) => s === '..' || s === '.' || s.includes('\0') || s.includes('\\') || s.includes('/'))) return null;
  return { slug: segs[1], peerId, rest, path: rest.join('/') };
}

async function stat(peerId, path) {
  const k = peerId + '|' + path, c = statCache.get(k);
  if (c && Date.now() - c.t < STAT_TTL) return c.st;
  const r = await call(peerId, 'stat', { path });
  statCache.set(k, { t: Date.now(), st: r.st });
  if (statCache.size > 500) statCache.clear();
  return r.st;
}
const forget = () => statCache.clear();

const rfc1123 = (ms) => new Date(ms || 0).toUTCString();
function entryXml(href, st, name, extra = '') {
  const dir = !!st.d;
  return `<D:response><D:href>${esc(href)}</D:href><D:propstat><D:prop>` +
    `<D:displayname>${esc(name)}</D:displayname>` +
    (dir ? '<D:resourcetype><D:collection/></D:resourcetype>' : '<D:resourcetype/>') +
    (dir ? '' : `<D:getcontentlength>${st.s || 0}</D:getcontentlength><D:getcontenttype>application/octet-stream</D:getcontenttype>`) +
    `<D:getlastmodified>${rfc1123(st.m)}</D:getlastmodified><D:creationdate>${new Date(st.m || 0).toISOString()}</D:creationdate>` +
    extra + '</D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>';
}

function send(res, code, body, headers = {}) {
  const b = body == null ? Buffer.alloc(0) : Buffer.from(body);
  res.writeHead(code, Object.assign({ 'Content-Length': b.length, 'Content-Type': 'text/plain; charset=utf-8', DAV: '1, 2', 'MS-Author-Via': 'DAV' }, headers));
  res.end(b);
}
const fail = (res, e) => send(res, statusOf(e), (e && e.why) || 'Error');

async function handle(req, res) {
  const host = String(req.headers.host || '');
  if (host !== `127.0.0.1:${port}` && host !== `localhost:${port}`) return send(res, 403, 'Forbidden');
  const m = req.method.toUpperCase();
  if (m === 'OPTIONS') return send(res, 200, '', { Allow: 'OPTIONS, PROPFIND, PROPPATCH, GET, HEAD, PUT, MKCOL, DELETE, MOVE, LOCK, UNLOCK' });
  const t = parseTarget(req.url);
  if (!t) { req.resume(); return send(res, 404, 'Not found'); }
  const href = (rel) => base(t.slug) + (rel ? '/' + enc(rel.split('/')) : '') + '/';
  try {
    switch (m) {
      case 'PROPFIND': {
        req.resume();
        const depth = String(req.headers.depth || '1');
        const st = await stat(t.peerId, t.path);
        const self = t.rest.length ? href(t.path) : base(t.slug) + '/';
        const selfName = t.rest.length ? t.rest[t.rest.length - 1] : t.slug;
        let extra = '';
        if (!t.rest.length) {
          try { const i = await call(t.peerId, 'info'); extra = `<D:quota-available-bytes>${i.free || 0}</D:quota-available-bytes><D:quota-used-bytes>${Math.max(0, (i.total || 0) - (i.free || 0))}</D:quota-used-bytes>`; } catch {}
        }
        let xml = '<?xml version="1.0" encoding="utf-8"?><D:multistatus xmlns:D="DAV:">';
        const selfHref = st.d ? self : self.replace(/\/$/, '');
        xml += entryXml(selfHref, st, selfName, extra);
        if (st.d && depth !== '0') {
          const l = await call(t.peerId, 'list', { path: t.path });
          for (const e of l.entries || []) {
            const rel = t.path ? t.path + '/' + e.n : e.n;
            xml += entryXml(e.d ? href(rel) : href(rel).replace(/\/$/, ''), e, e.n);
          }
        }
        return send(res, 207, xml + '</D:multistatus>', { 'Content-Type': 'application/xml; charset=utf-8' });
      }
      case 'PROPPATCH': {
        req.resume();
        await stat(t.peerId, t.path);
        return send(res, 207, `<?xml version="1.0" encoding="utf-8"?><D:multistatus xmlns:D="DAV:"><D:response><D:href>${esc(req.url)}</D:href><D:propstat><D:prop/><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response></D:multistatus>`, { 'Content-Type': 'application/xml; charset=utf-8' });
      }
      case 'GET': case 'HEAD': {
        const st = await stat(t.peerId, t.path);
        if (st.d) return send(res, 200, m === 'GET' ? `<html><body>${esc(t.rest[t.rest.length - 1] || t.slug)}</body></html>` : '', { 'Content-Type': 'text/html; charset=utf-8' });
        const size = st.s || 0;
        let a = 0, b = size - 1, code = 200;
        const rg = /^bytes=(\d*)-(\d*)$/.exec(String(req.headers.range || ''));
        if (rg && (rg[1] || rg[2])) {
          if (rg[1] === '') { a = Math.max(0, size - Number(rg[2])); } else { a = Number(rg[1]); if (rg[2]) b = Math.min(b, Number(rg[2])); }
          if (a > b || a >= size) return send(res, 416, 'Range not satisfiable', { 'Content-Range': `bytes */${size}` });
          code = 206;
        }
        const len = size ? b - a + 1 : 0;
        const h = { 'Content-Type': 'application/octet-stream', 'Content-Length': len, 'Accept-Ranges': 'bytes', 'Last-Modified': rfc1123(st.m), DAV: '1, 2' };
        if (code === 206) h['Content-Range'] = `bytes ${a}-${b}/${size}`;
        res.writeHead(code, h);
        if (m === 'HEAD' || !len) return res.end();
        let closed = false;
        res.on('close', () => { closed = true; });
        for (let off = a; off <= b && !closed; off += CHUNK) {
          const r = await call(t.peerId, 'read', { path: t.path, off, len: Math.min(CHUNK, b - off + 1) });
          const buf = Buffer.from(String(r.data || ''), 'base64');
          if (!buf.length) break;
          if (closed) break;
          if (!res.write(buf)) await new Promise((ok) => { const f = () => { res.off('drain', f); res.off('close', f); ok(); }; res.on('drain', f); res.on('close', f); });
        }
        return res.end();
      }
      case 'PUT': {
        let existed = true;
        try { await stat(t.peerId, t.path); } catch (e) { if (e.code === 'notfound') existed = false; else throw e; }
        let off = 0, first = true, acc = [], accLen = 0;
        const flush = async (final) => {
          while (accLen >= CHUNK || (final && (accLen > 0 || first))) {
            const all = Buffer.concat(acc);
            const piece = all.subarray(0, Math.min(CHUNK, all.length));
            const rest = all.subarray(piece.length);
            acc = rest.length ? [Buffer.from(rest)] : []; accLen = rest.length;
            await call(t.peerId, 'write', { path: t.path, off, data: piece.toString('base64'), trunc: first });
            off += piece.length; first = false;
            if (final && accLen === 0) break;
          }
        };
        for await (const c of req) { acc.push(c); accLen += c.length; await flush(false); }
        await flush(true);
        forget();
        return send(res, existed ? 204 : 201, '');
      }
      case 'MKCOL': {
        req.resume();
        await call(t.peerId, 'mkdir', { path: t.path });
        forget();
        return send(res, 201, '');
      }
      case 'DELETE': {
        req.resume();
        await call(t.peerId, 'del', { path: t.path });
        forget();
        return send(res, 204, '');
      }
      case 'MOVE': {
        req.resume();
        let dest;
        try { dest = parseTarget(new URL(String(req.headers.destination || ''), 'http://x').pathname); } catch { dest = null; }
        if (!dest || dest.peerId !== t.peerId || !dest.rest.length) return send(res, 400, 'Bad destination');
        let existed = true;
        try { await stat(t.peerId, dest.path); } catch (e) { if (e.code === 'notfound') existed = false; else throw e; }
        if (existed) {
          if (String(req.headers.overwrite || 'T').toUpperCase() === 'F') return send(res, 412, 'Destination exists');
          await call(t.peerId, 'del', { path: dest.path });
        }
        await call(t.peerId, 'move', { from: t.path, to: dest.path });
        forget();
        return send(res, existed ? 204 : 201, '');
      }
      case 'LOCK': { // some clients insist on a lock before they write; it is only a courtesy here
        req.resume();
        const tok = 'opaquelocktoken:' + crypto.randomUUID();
        return send(res, 200, `<?xml version="1.0" encoding="utf-8"?><D:prop xmlns:D="DAV:"><D:lockdiscovery><D:activelock><D:locktype><D:write/></D:locktype><D:lockscope><D:exclusive/></D:lockscope><D:depth>infinity</D:depth><D:timeout>Second-600</D:timeout><D:locktoken><D:href>${tok}</D:href></D:locktoken></D:activelock></D:lockdiscovery></D:prop>`, { 'Content-Type': 'application/xml; charset=utf-8', 'Lock-Token': `<${tok}>` });
      }
      case 'UNLOCK': req.resume(); return send(res, 204, '');
      case 'COPY': req.resume(); return send(res, 501, 'Copy on the phone is not supported; copy from the phone to the computer instead.');
      default: req.resume(); return send(res, 405, 'Method not allowed');
    }
  } catch (e) {
    if (!res.headersSent) return fail(res, e);
    try { res.destroy(); } catch {}
  }
}

function ensureServer() {
  if (server) return Promise.resolve();
  token = crypto.randomBytes(16).toString('hex');
  return new Promise((resolve, reject) => {
    const s = http.createServer((req, res) => { handle(req, res); });
    s.on('error', (e) => { if (!server) reject(e); });
    s.listen(0, '127.0.0.1', () => { server = s; port = s.address().port; resolve(); });
  });
}

/* ---------- mounting in the operating system ---------- */
const run = (cmd, args, opts = {}) => new Promise((resolve) => execFile(cmd, args, Object.assign({ timeout: 25000, windowsHide: true }, opts), (err, stdout, stderr) => resolve({ err, stdout: String(stdout || ''), stderr: String(stderr || '') })));

/** Asks the phone if sharing is on, then mounts it. Returns { ok, url, drive?, manual?, error?, hint? }. */
async function mount(peerId, name, platform = process.platform, exec = run) {
  if (active.has(peerId)) return { ok: true, ...active.get(peerId), already: true };
  try { await call(peerId, 'info'); }
  catch (e) {
    const hint = e.code === 'disabled' ? 'On the phone open Y Connect > Settings > Storage sharing and switch it on.' : e.code === 'offline' ? 'Connect the phone first.' : '';
    return { ok: false, error: e.why, code: e.code, hint };
  }
  await ensureServer();
  const slug = slugFor(peerId, name);
  const http_ = `http://127.0.0.1:${port}${base(slug)}/`;
  const dav = `dav://127.0.0.1:${port}${base(slug)}/`;
  let rec;
  if (platform === 'linux') {
    const r = await exec('gio', ['mount', dav]);
    if (r.err && r.err.code === 'ENOENT') rec = { slug, url: http_, via: 'manual', manual: true, hint: 'Install gvfs (gio) to mount it automatically, or connect to this address in your file manager.' };
    else if (r.err) { mounts.delete(slug); return { ok: false, error: (r.stderr || r.err.message).trim().slice(0, 300), hint: 'Mounting needs the gvfs-dav package. You can also connect to the address in your file manager.', url: http_ }; }
    else rec = { slug, url: http_, via: 'gio', dav };
  } else if (platform === 'win32') {
    const r = await exec('net', ['use', '*', http_.replace(/\/$/, '')]);
    const d = /([A-Z]:)/i.exec(r.stdout);
    if (r.err || !d) { mounts.delete(slug); return { ok: false, error: (r.stderr || r.stdout || 'Could not map a drive').trim().slice(0, 300), hint: 'Windows needs the "WebClient" service running (services.msc). Or choose Map network drive in Explorer and use the address.', url: http_ }; }
    rec = { slug, url: http_, via: 'net', drive: d[1].toUpperCase() };
  } else rec = { slug, url: http_, via: 'manual', manual: true, hint: 'Connect to this address in your file manager.' };
  active.set(peerId, rec);
  D.addLog('link', 'Phone storage mounted', name);
  if (D.pushState) D.pushState();
  return { ok: true, ...rec };
}

async function unmount(peerId, exec = run) {
  const r = active.get(peerId);
  if (!r) return false;
  if (r.via === 'gio') await exec('gio', ['mount', '-u', r.dav]);
  else if (r.via === 'net') await exec('net', ['use', r.drive, '/delete', '/y']);
  active.delete(peerId);
  mounts.delete(r.slug);
  forget();
  D.addLog('link', 'Phone storage unmounted');
  if (D.pushState) D.pushState();
  return true;
}
async function unmountAll() { for (const id of [...active.keys()]) await unmount(id); if (server) { server.close(); server = null; } }
const list = () => [...active.entries()].map(([id, r]) => ({ id, url: r.url, via: r.via, drive: r.drive || '' }));

module.exports = { init, call, onMessage, onDisconnected, mount, unmount, unmountAll, list, ensureServer, _test: { slugFor, getPort: () => port, getToken: () => token, mounts, base } };
