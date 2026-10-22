package dev.scrcpydroid.core.adb

import android.content.Context
import dadb.AdbKeyPair
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdbKeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val privateKeyFile: File get() = File(context.filesDir, "adbkey")
    private val publicKeyFile: File get() = File(context.filesDir, "adbkey.pub")

    fun getOrCreateKeyPair(): AdbKeyPair {
        if (!privateKeyFile.exists()) {
            AdbKeyPair.generate(privateKeyFile, publicKeyFile)
        }
        return AdbKeyPair.read(privateKeyFile, publicKeyFile)
    }

    fun getPrivateKey(): java.security.PrivateKey {
        getOrCreateKeyPair()
        val pem = String(privateKeyFile.readBytes(), Charsets.UTF_8)
        val cleanBase64 = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replace("\r", "")
            .replace("\n", "")
            .trim()
        val derBytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
        val keySpec = java.security.spec.PKCS8EncodedKeySpec(derBytes)
        return java.security.KeyFactory.getInstance("RSA").generatePrivate(keySpec)
    }
}
