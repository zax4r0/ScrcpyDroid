package dev.scrcpydroid.core.adb

import android.content.Context
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.bouncycastle.x509.X509V3CertificateGenerator
import java.io.ByteArrayInputStream
import java.io.IOException
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.RSAPublicKeySpec
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import javax.security.auth.x500.X500Principal

@Suppress("DEPRECATION")
@Singleton
class AdbPairingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adbKeyManager: AdbKeyManager
) {
    companion object {
        private const val TAG = "AdbPairingManager"
        private const val PAIRING_TIMEOUT_MS = 15_000L
    }

    suspend fun pair(host: String, port: Int, pairingCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            withTimeout(PAIRING_TIMEOUT_MS) {

                // 2. Load private key
                val privKey = adbKeyManager.getPrivateKey()
                val rsaPriv = privKey as? RSAPrivateCrtKey
                    ?: throw IllegalStateException("Private key is not an RSAPrivateCrtKey")

                val spec = RSAPublicKeySpec(rsaPriv.modulus, rsaPriv.publicExponent)
                val pubKey = KeyFactory.getInstance("RSA").generatePublic(spec)

                // 3. Generate X509 certificate for the TLS handshake
                val gen = X509V3CertificateGenerator()
                val dn = X500Principal("CN=ScrcpyDroid")
                gen.setSerialNumber(BigInteger.valueOf(System.currentTimeMillis()))
                gen.setIssuerDN(dn)
                gen.setSubjectDN(dn)
                gen.setNotBefore(Date(System.currentTimeMillis() - 86400000L))
                gen.setNotAfter(Date(System.currentTimeMillis() + 10L * 365 * 86400000L))
                gen.setPublicKey(pubKey)
                gen.setSignatureAlgorithm("SHA256WithRSAEncryption")
                val rawCert = gen.generate(rsaPriv)

                val certFactory = CertificateFactory.getInstance("X.509")
                val cert: Certificate = certFactory.generateCertificate(ByteArrayInputStream(rawCert.encoded))

                // 4. Execute SPAKE2 pairing using libadb
                val connectionManager = object : AbsAdbConnectionManager() {
                    override fun getPrivateKey(): PrivateKey = rsaPriv
                    override fun getCertificate(): Certificate = cert
                    override fun getDeviceName(): String = Build.MODEL ?: "ScrcpyDroid"
                }

                val success = connectionManager.pair(host, port, pairingCode)
                if (success) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Pairing rejected by device. Check that the 6-digit code matches."))
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "Pairing timed out", e)
            Result.failure(Exception("Pairing timed out. Ensure the 6-digit code popup is still active on the target device."))
        } catch (e: Exception) {
            Log.e(TAG, "Pairing error: ${e.message}", e)
            Result.failure(e)
        }
    }
}
