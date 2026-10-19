package com.senza.apksigner.core

import com.senza.apksigner.data.KeyProfile
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.util.Collections
import java.util.UUID

data class CertInfo(
    val subject: String, val issuer: String, val serial: String,
    val notBefore: Long, val notAfter: Long,
    val sigAlg: String, val keyAlg: String, val keyBits: Int,
    val sha256: String, val sha1: String, val md5: String
)

object Ks {
    class KeyData(val alias: String, val key: PrivateKey, val chain: List<X509Certificate>)

    fun fp(c: X509Certificate, alg: String): String = Fmt.hex(MessageDigest.getInstance(alg).digest(c.encoded))

    fun certInfo(c: X509Certificate): CertInfo {
        val bits = when (val k = c.publicKey) {
            is RSAPublicKey -> k.modulus.bitLength()
            is ECPublicKey -> k.params.order.bitLength()
            else -> 0
        }
        return CertInfo(
            subject = c.subjectX500Principal.name, issuer = c.issuerX500Principal.name,
            serial = c.serialNumber.toString(16).uppercase(),
            notBefore = c.notBefore.time, notAfter = c.notAfter.time,
            sigAlg = c.sigAlgName, keyAlg = c.publicKey.algorithm, keyBits = bits,
            sha256 = fp(c, "SHA-256"), sha1 = fp(c, "SHA-1"), md5 = fp(c, "MD5")
        )
    }

    /** Coba PKCS12 lalu BKS. Mengembalikan keystore dan tipenya. */
    fun openTyped(file: File, pass: CharArray): Pair<KeyStore, String> {
        var first: Exception? = null
        for (t in listOf("PKCS12", "BKS")) {
            try {
                val ks = KeyStore.getInstance(t)
                file.inputStream().use { ks.load(it, pass) }
                return ks to t
            } catch (e: Exception) {
                if (first == null) first = e
            }
        }
        throw first ?: IOException("Format keystore tidak dikenali")
    }

    fun keyAliases(ks: KeyStore): List<String> =
        Collections.list(ks.aliases()).filter { ks.isKeyEntry(it) }

    fun loadKey(file: File, type: String, storePass: CharArray, alias: String, keyPass: CharArray): KeyData {
        val ks = KeyStore.getInstance(type)
        file.inputStream().use { ks.load(it, storePass) }
        val key = ks.getKey(alias, keyPass) as? PrivateKey
            ?: throw IOException("Kunci privat untuk alias \"$alias\" tidak ditemukan")
        val chain = ks.getCertificateChain(alias)?.map { it as X509Certificate }
            ?: throw IOException("Rantai sertifikat untuk alias \"$alias\" tidak ditemukan")
        return KeyData(alias, key, chain)
    }

    fun profile(
        id: String, name: String, file: String, type: String, alias: String,
        storePw: String, keyPw: String, remember: Boolean, c: X509Certificate
    ): KeyProfile {
        val info = certInfo(c)
        return KeyProfile(
            id = id, name = name, file = file, type = type, alias = alias,
            storePassEnc = if (remember) SecretStore.encrypt(storePw) else null,
            keyPassEnc = if (remember) SecretStore.encrypt(keyPw) else null,
            subject = info.subject, sha256 = info.sha256, sha1 = info.sha1, md5 = info.md5,
            validTo = info.notAfter, algo = "${info.keyAlg} ${info.keyBits}",
            createdAt = System.currentTimeMillis()
        )
    }
}

object KsImport {
    fun probe(bytes: ByteArray, storePw: CharArray, tmpDir: File): List<String> {
        val aliases = if (Jks.isJks(bytes)) {
            Jks.parse(bytes).filter { it.enc != null }.map { it.alias }
        } else {
            val f = File.createTempFile("probe", ".ks", tmpDir)
            try {
                f.writeBytes(bytes)
                Ks.keyAliases(Ks.openTyped(f, storePw).first)
            } finally { f.delete() }
        }
        if (aliases.isEmpty()) throw IOException("Tidak ada kunci privat di keystore ini")
        return aliases
    }

    fun import(
        bytes: ByteArray, storePw: String, keyPw: String, alias: String,
        name: String, dir: File, remember: Boolean
    ): KeyProfile {
        val id = UUID.randomUUID().toString()
        val out = File(dir, "$id.ks")
        if (Jks.isJks(bytes)) {
            val e = Jks.parse(bytes).firstOrNull { it.enc != null && it.alias == alias }
                ?: throw IOException("Alias \"$alias\" tidak ada")
            val pk = Jks.recover(e.enc!!, keyPw.toCharArray())
            val ks = KeyStore.getInstance("PKCS12")
            ks.load(null, null)
            ks.setKeyEntry(e.alias, pk, keyPw.toCharArray(), e.chain.toTypedArray())
            out.outputStream().use { ks.store(it, keyPw.toCharArray()) }
            return Ks.profile(id, name, out.name, "PKCS12", e.alias, keyPw, keyPw, remember, e.chain[0])
        }
        out.writeBytes(bytes)
        try {
            val type = Ks.openTyped(out, storePw.toCharArray()).second
            val kd = Ks.loadKey(out, type, storePw.toCharArray(), alias, keyPw.toCharArray())
            return Ks.profile(id, name, out.name, type, alias, storePw, keyPw, remember, kd.chain[0])
        } catch (e: Exception) {
            out.delete()
            throw e
        }
    }
}
