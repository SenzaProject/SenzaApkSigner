package com.senza.apksigner.core

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.UnrecoverableKeyException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Pembaca keystore format JKS (Java KeyStore) — Android tidak punya provider JKS bawaan.
 * Dipakai hanya saat impor; hasilnya dikonversi ke PKCS12.
 */
object Jks {
    class Entry(val alias: String, val enc: ByteArray?, val chain: List<X509Certificate>)

    fun isJks(b: ByteArray): Boolean =
        b.size > 4 && b[0] == 0xFE.toByte() && b[1] == 0xED.toByte() && b[2] == 0xFE.toByte() && b[3] == 0xED.toByte()

    fun parse(data: ByteArray): List<Entry> {
        val inp = DataInputStream(ByteArrayInputStream(data))
        inp.readInt()
        val ver = inp.readInt()
        if (ver != 1 && ver != 2) throw IOException("Versi JKS tidak didukung: $ver")
        val count = inp.readInt()
        val cf = CertificateFactory.getInstance("X.509")
        val out = ArrayList<Entry>()

        fun readCert(): X509Certificate {
            if (ver == 2) inp.readUTF()
            val l = inp.readInt()
            val b = ByteArray(l)
            inp.readFully(b)
            return cf.generateCertificate(ByteArrayInputStream(b)) as X509Certificate
        }

        for (n in 0 until count) {
            val tag = inp.readInt()
            val alias = inp.readUTF()
            inp.readLong() // timestamp
            when (tag) {
                1 -> {
                    val l = inp.readInt()
                    val enc = ByteArray(l)
                    inp.readFully(enc)
                    val chainLen = inp.readInt()
                    val chain = ArrayList<X509Certificate>()
                    for (c in 0 until chainLen) chain.add(readCert())
                    out.add(Entry(alias, enc, chain))
                }
                2 -> out.add(Entry(alias, null, listOf(readCert())))
                else -> throw IOException("Entri JKS rusak")
            }
        }
        return out
    }

    private fun tlv(b: ByteArray, off: Int): IntArray {
        val tag = b[off].toInt() and 0xFF
        var p = off + 1
        var l = b[p++].toInt() and 0xFF
        if (l >= 0x80) {
            val n = l and 0x7F
            l = 0
            for (i in 0 until n) l = (l shl 8) or (b[p++].toInt() and 0xFF)
        }
        return intArrayOf(tag, p, l)
    }

    /** Dekripsi kunci privat JKS (algoritma proprietary Sun 1.3.6.1.4.1.42.2.17.1.1). */
    fun recover(enc: ByteArray, password: CharArray): PrivateKey {
        val outer = tlv(enc, 0)
        val algo = tlv(enc, outer[1])
        val oct = tlv(enc, algo[1] + algo[2])
        val data = enc.copyOfRange(oct[1], oct[1] + oct[2])
        if (data.size < 41) throw IOException("Data kunci JKS tidak valid")

        val salt = data.copyOfRange(0, 20)
        val encrypted = data.copyOfRange(20, data.size - 20)
        val check = data.copyOfRange(data.size - 20, data.size)

        val pw = ByteArray(password.size * 2)
        for (i in password.indices) {
            pw[2 * i] = (password[i].code shr 8).toByte()
            pw[2 * i + 1] = password[i].code.toByte()
        }
        val sha = MessageDigest.getInstance("SHA-1")
        val plain = ByteArray(encrypted.size)
        var digest = salt
        var pos = 0
        while (pos < encrypted.size) {
            sha.update(pw)
            sha.update(digest)
            digest = sha.digest()
            val n = minOf(20, encrypted.size - pos)
            for (j in 0 until n) plain[pos + j] = (encrypted[pos + j].toInt() xor digest[j].toInt()).toByte()
            pos += n
        }
        sha.update(pw)
        sha.update(plain)
        if (!sha.digest().contentEquals(check)) throw UnrecoverableKeyException("Kata sandi kunci salah")

        val spec = PKCS8EncodedKeySpec(plain)
        for (alg in listOf("RSA", "EC", "DSA")) {
            try { return KeyFactory.getInstance(alg).generatePrivate(spec) } catch (_: Exception) { }
        }
        throw IOException("Jenis kunci tidak didukung")
    }
}
