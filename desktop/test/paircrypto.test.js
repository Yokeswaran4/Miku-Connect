'use strict';
const assert = require('assert');
const pc = require('../paircrypto');

// both sides reach the same secret and the same check number; the code survives the trip
const a = pc.newKeyPair(), b = pc.newKeyPair();
const sa = pc.agree(a.priv, b.pub), sb = pc.agree(b.priv, a.pub);
assert(sa.equals(sb));
assert.strictEqual(pc.checkNumber(sa, a.pub, b.pub), pc.checkNumber(sb, a.pub, b.pub));
assert(/^\d{3} \d{3}$/.test(pc.checkNumber(sa, a.pub, b.pub)));
const code = pc.randomCode();
assert(/^([A-Z2-9]{4}-){3}[A-Z2-9]{4}$/.test(code) && pc.validCode(code));
assert.strictEqual(pc.open(pc.transportKey(sb), pc.seal(pc.transportKey(sa), code)), code);

// a man in the middle ends up with different secrets, so the check numbers differ
const m = pc.newKeyPair();
assert.notStrictEqual(pc.checkNumber(pc.agree(a.priv, m.pub), a.pub, m.pub), pc.checkNumber(pc.agree(b.priv, m.pub), m.pub, b.pub));

// data encryption is unchanged: key = PBKDF2(code), wrong key fails
const k = pc.deriveKey(code), k2 = pc.deriveKey(pc.randomCode());
assert.strictEqual(pc.open(k, pc.seal(k, 'hello')), 'hello');
assert.throws(() => pc.open(k2, pc.seal(k, 'hello')));
assert(pc.openBytes(k, pc.sealBytes(k, Buffer.from('abc'))).equals(Buffer.from('abc')));
assert.throws(() => pc.agree(a.priv, 'AAAA'));
console.log('paircrypto ok');
