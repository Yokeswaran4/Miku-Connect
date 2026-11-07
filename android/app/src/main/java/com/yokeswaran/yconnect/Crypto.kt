// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Yokeswaran N
package com.yokeswaran.yconnect

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM, key = PBKDF2-SHA256(pairing code). Wire format: base64(iv[12] + ciphertext + tag[16]).
 * Every pair of devices has its own random pairing code. It is created when the other device taps Accept:
 * both devices swap throw-away ECDH (P-256) public keys, show the same 6-digit check number, and the requester then
 * sends the new code encrypted with a key from the ECDH secret. Same format and key derivation as desktop (paircrypto.js).
 */
object Crypto {
    private val rnd = SecureRandom()

    fun normalize(p: String): String =
        p.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.ifEmpty { "-" }

    fun deriveKey(pass: String): ByteArray {
        val spec = PBEKeySpec(normalize(pass).toCharArray(), "y-connect-v1".toByteArray(), 100000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    fun encrypt(key: ByteArray, plain: String): String {
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    /** Binary frames (file data): iv[12] + ciphertext + tag[16], same cipher as the text frames, no base64. */
    fun encryptBytes(key: ByteArray, data: ByteArray, off: Int, len: Int): ByteArray {
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val out = ByteArray(12 + c.getOutputSize(len))
        System.arraycopy(iv, 0, out, 0, 12)
        c.doFinal(data, off, len, out, 12)
        return out
    }

    fun decryptBytes(key: ByteArray, b: ByteArray): ByteArray {
        require(b.size > 28) { "short" }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, b.copyOfRange(0, 12)))
        return c.doFinal(b, 12, b.size - 12)
    }

    fun decrypt(key: ByteArray, b64: String): String {
        val b = Base64.decode(b64, Base64.DEFAULT)
        require(b.size > 28) { "short" }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, b.copyOfRange(0, 12)))
        return String(c.doFinal(b, 12, b.size - 12), Charsets.UTF_8)
    }

    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** 16 random characters (80 bits), shown as XXXX-XXXX-XXXX-XXXX. */
    fun randomCode(): String = (1..16).map { ALPHABET[rnd.nextInt(ALPHABET.length)] }.joinToString("").chunked(4).joinToString("-")

    fun validCode(c: String): Boolean = normalize(c).length in 12..64

    class Pair256(val pair: KeyPair, val pubB64: String)

    /** Throw-away key pair; the public half is X.509 / SPKI DER in base64 (the same bytes the desktop app produces). */
    fun newKeyPair(): Pair256 {
        val g = KeyPairGenerator.getInstance("EC")
        g.initialize(ECGenParameterSpec("secp256r1"), rnd)
        val kp = g.generateKeyPair()
        return Pair256(kp, Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP))
    }

    /** Shared secret from our private key and the other side's public half. Throws on anything that is not a P-256 key. */
    fun agree(priv: PrivateKey, peerPubB64: String): ByteArray {
        require(peerPubB64.length in 1..400) { "bad key" }
        val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.decode(peerPubB64, Base64.DEFAULT)))
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(priv)
        ka.doPhase(pub, true)
        return ka.generateSecret()
    }

    /** Key that protects the new pairing code while it travels. */
    fun transportKey(secret: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").run { update("y-connect-pair-v1".toByteArray()); update(secret); digest() }

    /** The 6-digit number both screens show. pubA = the device that asked, pubB = the device that was asked. */
    fun checkNumber(secret: ByteArray, pubA: String, pubB: String): String {
        val h = MessageDigest.getInstance("SHA-256").run {
            update("y-connect-sas-v1".toByteArray()); update(secret)
            update(Base64.decode(pubA, Base64.DEFAULT)); update(Base64.decode(pubB, Base64.DEFAULT)); digest()
        }
        val n = (((h[0].toLong() and 0xff) shl 24) or ((h[1].toLong() and 0xff) shl 16) or ((h[2].toLong() and 0xff) shl 8) or (h[3].toLong() and 0xff)) % 1000000L
        val s = n.toString().padStart(6, '0')
        return s.substring(0, 3) + " " + s.substring(3)
    }
}
