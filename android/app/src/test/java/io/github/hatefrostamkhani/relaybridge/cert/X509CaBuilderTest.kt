package io.github.hatefrostamkhani.relaybridge.cert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.KeyPairGenerator
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit

class X509CaBuilderTest {
    @Test
    fun buildsParseableSelfSignedCaCertificate() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()

        val der = X509CaBuilder.buildSelfSignedCa(
            keyPair = keyPair,
            commonName = "RelayBridge Test CA",
            notBefore = Instant.now().minus(1, ChronoUnit.DAYS),
            notAfter = Instant.now().plus(30, ChronoUnit.DAYS),
        )

        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(der)) as X509Certificate

        certificate.verify(keyPair.public)
        assertEquals("CN=RelayBridge Test CA", certificate.subjectX500Principal.name)
        assertEquals(certificate.subjectX500Principal, certificate.issuerX500Principal)
        assertTrue(certificate.basicConstraints >= 0)
        assertTrue(certificate.keyUsage[5])
    }
}
