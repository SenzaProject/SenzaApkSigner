# Senza APK Signer

Aplikasi Android untuk menandatangani (sign) APK — v1/v2/v3/v4, zipalign, manajemen keystore, verifikasi, riwayat.

## Fitur
- Sign banyak APK sekaligus (batch), info APK (ikon, paket, versi, SDK, izin)
- Skema v1, v2, v3, v4 (.idsig), min SDK override, akhiran nama file
- Zipalign bawaan (4 byte, 16 KB untuk .so)
- Buat keystore (RSA 2048/4096, EC P-256) dengan DN lengkap
- Impor PKCS12 / JKS (dikonversi ke PKCS12) / BKS; ekspor; sidik jari SHA-256/SHA-1/MD5
- Kata sandi opsional tersimpan terenkripsi (AES-GCM, Android Keystore)
- Verifikasi APK apa pun (skema, sertifikat, error, peringatan)
- Pasang, bagikan, simpan ke Download/SenzaSigner; riwayat
- Buka APK langsung dari file manager ("Buka dengan")

## Build
Push ke GitHub → Actions → "Build APK" → unduh artifact `SenzaApkSigner`.

## Push lewat Termux
```
pkg install git unzip -y
cd ~/storage/downloads && unzip SenzaApkSigner.zip && cd SenzaApkSigner
git init && git branch -M main
git add . && git commit -m "Senza APK Signer v1.0.0"
git remote add origin https://github.com/SenzaProject/SenzaApkSigner.git
git push -u origin main
```
