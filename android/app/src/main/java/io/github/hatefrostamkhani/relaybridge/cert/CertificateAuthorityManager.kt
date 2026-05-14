package io.github.hatefrostamkhani.relaybridge.cert

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

class CertificateAuthorityManager(context: Context) {
    private val appContext = context.applicationContext
    private val caDir = File(appContext.filesDir, "ca")
    val certificateFile: File = File(caDir, "ca.crt")
    private val privateKeyFile: File = File(caDir, "ca.key")

    fun generateIfMissing(): File {
        if (!certificateFile.isFile || !privateKeyFile.isFile) {
            generate()
        }
        return certificateFile
    }

    fun certificateUri(): Uri = Uri.parse("content://${appContext.packageName}.certs/ca.crt")

    private fun generate() {
        caDir.mkdirs()
        val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048, SecureRandom())
        }.generateKeyPair()

        val notBefore = Instant.now().minus(1, ChronoUnit.DAYS)
        val notAfter = Instant.now().plus(3650, ChronoUnit.DAYS)
        val certDer = X509CaBuilder.buildSelfSignedCa(
            keyPair = keyPair,
            commonName = "RelayBridge Android Local CA",
            notBefore = notBefore,
            notAfter = notAfter,
        )

        certificateFile.writeText(pem("CERTIFICATE", certDer), Charsets.US_ASCII)
        privateKeyFile.writeText(pem("PRIVATE KEY", keyPair.private.encoded), Charsets.US_ASCII)
    }

    private fun pem(type: String, der: ByteArray): String {
        val body = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(der)
        return "-----BEGIN $type-----\n$body\n-----END $type-----\n"
    }
}
