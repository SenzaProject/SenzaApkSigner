package com.senza.apksigner.core

import com.senza.apksigner.data.KeyProfile
import com.senza.apksigner.data.NewKeyRequest
import java.io.ByteArrayInputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** Penulis DER minimal supaya bisa membuat sertifikat X.509 self-signed tanpa BouncyCastle. */
private object Der {
    fun len(n: Int): ByteArray = when {
        n < 0x80 -> byteArrayOf(n.toByte())
        n < 0x100 -> byteArrayOf(0x81.toByte(), n.toByte())
        n < 0x10000 -> byteArrayOf(0x82.toByte(), (n shr 8).toByte(), n.toByte())
        else -> byteArrayOf(0x83.toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
    }

    fun tlv(tag: Int, content: ByteArray): ByteArray = byteArrayOf(tag.toByte()) + len(content.size) + content

    fun concat(parts: List<ByteArray>): ByteArray {
        var out = ByteArray(0)
        for (p in parts) out += p
        return out
    }

    fun seq(vararg p: ByteArray): ByteArray = tlv(0x30, concat(p.toList()))
    fun set(vararg p: ByteArray): ByteArray = tlv(0x31, concat(p.toList()))
    fun int(v: BigInteger): ByteArray = tlv(0x02, v.toByteArray())
    fun nul(): ByteArray = byteArrayOf(0x05, 0x00)
    fun utf8(s: String): ByteArray = tlv(0x0C, s.toByteArray(Charsets.UTF_8))
    fun printable(s: String): ByteArray = tlv(0x13, s.toByteArray(Charsets.US_ASCII))
    fun bitString(b: ByteArray): ByteArray = tlv(0x03, byteArrayOf(0) + b)
    fun explicit(n: Int, content: ByteArray): ByteArray = tlv(0xA0 or n, content)

    fun oid(s: String): ByteArray {
        val parts = s.split('.').map { it.toLong() }
        val out = java.io.ByteArrayOutputStream()
        out.write((parts[0] * 40 + parts[1]).toInt())
        for (i in 2 until parts.size) {
            var v = parts[i]
            val tmp = ArrayList<Int>()
            tmp.add((v and 0x7F).toInt())
            v = v shr 7
            while (v > 0) { tmp.add(((v and 0x7F) or 0x80).toInt()); v = v shr 7 }
            for (j in tmp.indices.reversed()) out.write(tmp[j])
        }
        return tlv(0x06, out.toByteArray())
    }

    fun time(d: Date): ByteArray {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = d }
        return if (cal.get(Calendar.YEAR) < 2050) {
            val f = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            tlv(0x17, f.format(d).toByteArray(Charsets.US_ASCII))
        } else {
            val f = SimpleDateFormat("yyyyMMddHHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            tlv(0x18, f.format(d).toByteArray(Charsets.US_ASCII))
        }
    }

    fun name(rdns: List<Pair<String, String>>): ByteArray {
        val items = rdns.map { (id, value) ->
            val v = if (id == "2.5.4.6") printable(value) else utf8(value)
            set(seq(oid(id), v))
        }
        return tlv(0x30, concat(items))
    }
}

object CertGen {
    fun generate(r: NewKeyRequest, dir: File, remember: Boolean): KeyProfile {
        val kpg: KeyPairGenerator
        val sigAlg: String
        if (r.algo.startsWith("RSA")) {
            kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(r.algo.substringAfter('-').toInt(), SecureRandom())
            sigAlg = "SHA256withRSA"
        } else {
            kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
            sigAlg = "SHA256withECDSA"
        }
        val kp = kpg.generateKeyPair()
        val cert = buildCert(kp, sigAlg, r)

        val pass = r.password.toCharArray()
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry(r.alias, kp.private, pass, arrayOf<java.security.cert.Certificate>(cert))
        val id = UUID.randomUUID().toString()
        val f = File(dir, "$id.p12")
        f.outputStream().use { ks.store(it, pass) }
        return Ks.profile(id, r.name, f.name, "PKCS12", r.alias, r.password, r.password, remember, cert)
    }

    private fun buildCert(kp: KeyPair, sigAlg: String, r: NewKeyRequest): X509Certificate {
        val rdns = ArrayList<Pair<String, String>>()
        if (r.c.isNotBlank()) rdns += "2.5.4.6" to r.c.trim().uppercase()
        if (r.st.isNotBlank()) rdns += "2.5.4.8" to r.st.trim()
        if (r.l.isNotBlank()) rdns += "2.5.4.7" to r.l.trim()
        if (r.o.isNotBlank()) rdns += "2.5.4.10" to r.o.trim()
        if (r.ou.isNotBlank()) rdns += "2.5.4.11" to r.ou.trim()
        rdns += "2.5.4.3" to r.cn.trim()
        val dn = Der.name(rdns)

        val sigAlgId = if (sigAlg == "SHA256withRSA")
            Der.seq(Der.oid("1.2.840.113549.1.1.11"), Der.nul())
        else
            Der.seq(Der.oid("1.2.840.10045.4.3.2"))

        val now = Calendar.getInstance()
        now.add(Calendar.DAY_OF_YEAR, -1)
        val notBefore = now.time
        now.add(Calendar.DAY_OF_YEAR, 1)
        now.add(Calendar.YEAR, r.years)
        val notAfter = now.time

        val serial = BigInteger(63, SecureRandom()).add(BigInteger.ONE)
        val tbs = Der.seq(
            Der.explicit(0, Der.int(BigInteger.valueOf(2))),
            Der.int(serial),
            sigAlgId,
            dn,
            Der.seq(Der.time(notBefore), Der.time(notAfter)),
            dn,
            kp.public.encoded
        )
        val sig = Signature.getInstance(sigAlg).run {
            initSign(kp.private)
            update(tbs)
            sign()
        }
        val der = Der.seq(tbs, sigAlgId, Der.bitString(sig))
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(der)) as X509Certificate
    }
}
