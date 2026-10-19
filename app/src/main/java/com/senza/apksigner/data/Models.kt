package com.senza.apksigner.data

data class KeyProfile(
    val id: String,
    val name: String,
    val file: String,
    val type: String,
    val alias: String,
    val storePassEnc: String?,
    val keyPassEnc: String?,
    val subject: String,
    val sha256: String,
    val sha1: String,
    val md5: String,
    val validTo: Long,
    val algo: String,
    val createdAt: Long
)

data class HistoryItem(
    val id: String,
    val apkName: String,
    val pkg: String,
    val version: String,
    val path: String,
    val size: Long,
    val time: Long,
    val profileName: String,
    val schemes: String
)

data class SignOptions(
    val v1: Boolean = true,
    val v2: Boolean = true,
    val v3: Boolean = true,
    val v4: Boolean = false,
    val zipAlign: Boolean = true,
    val pageAlign16k: Boolean = true,
    val minSdk: Int? = null,
    val verifyAfter: Boolean = true,
    val autoSave: Boolean = true,
    val suffix: String = "-signed"
)

data class NewKeyRequest(
    val name: String,
    val alias: String,
    val algo: String,        // RSA-2048 | RSA-4096 | EC-P256
    val years: Int,
    val cn: String,
    val ou: String,
    val o: String,
    val l: String,
    val st: String,
    val c: String,
    val password: String
)
