package io.github.hatefrostamkhani.relaybridge.cert

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object X509CaBuilder {
    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'").withZone(ZoneOffset.UTC)

    fun buildSelfSignedCa(
        keyPair: KeyPair,
        commonName: String,
        notBefore: Instant,
        notAfter: Instant,
    ): ByteArray {
        val algorithm = sequence(
            oid("1.2.840.113549.1.1.11"),
            derNull(),
        )
        val subject = name(commonName)
        val serial = BigInteger(159, SecureRandom()).add(BigInteger.ONE)
        val tbs = sequence(
            explicit(0, integer(BigInteger.valueOf(2))),
            integer(serial),
            algorithm,
            subject,
            sequence(generalizedTime(notBefore), generalizedTime(notAfter)),
            subject,
            keyPair.public.encoded,
            explicit(3, extensions(keyPair.public.encoded)),
        )

        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(keyPair.private)
            update(tbs)
            sign()
        }

        val cert = sequence(
            tbs,
            algorithm,
            bitString(signature),
        )

        CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(cert))
        return cert
    }

    private fun extensions(subjectPublicKeyInfo: ByteArray): ByteArray {
        val basicConstraints = extension(
            oid = "2.5.29.19",
            critical = true,
            value = sequence(derBoolean(true)),
        )
        val keyUsage = extension(
            oid = "2.5.29.15",
            critical = true,
            value = bitString(byteArrayOf(0x06), unusedBits = 1),
        )
        val subjectKeyIdentifier = extension(
            oid = "2.5.29.14",
            critical = false,
            value = octetString(MessageDigest.getInstance("SHA-1").digest(subjectPublicKeyInfo)),
        )
        return sequence(basicConstraints, keyUsage, subjectKeyIdentifier)
    }

    private fun extension(oid: String, critical: Boolean, value: ByteArray): ByteArray {
        val parts = if (critical) {
            arrayOf(oid(oid), derBoolean(true), octetString(value))
        } else {
            arrayOf(oid(oid), octetString(value))
        }
        return sequence(*parts)
    }

    private fun name(commonName: String): ByteArray =
        sequence(set(sequence(oid("2.5.4.3"), utf8String(commonName))))

    private fun sequence(vararg parts: ByteArray): ByteArray = tagged(0x30, concat(*parts))

    private fun set(vararg parts: ByteArray): ByteArray = tagged(0x31, concat(*parts))

    private fun explicit(tag: Int, value: ByteArray): ByteArray = tagged(0xA0 + tag, value)

    private fun integer(value: BigInteger): ByteArray = tagged(0x02, value.toByteArray())

    private fun derBoolean(value: Boolean): ByteArray =
        tagged(0x01, byteArrayOf(if (value) 0xFF.toByte() else 0x00))

    private fun derNull(): ByteArray = tagged(0x05, byteArrayOf())

    private fun octetString(value: ByteArray): ByteArray = tagged(0x04, value)

    private fun bitString(value: ByteArray, unusedBits: Int = 0): ByteArray =
        tagged(0x03, byteArrayOf(unusedBits.toByte()) + value)

    private fun utf8String(value: String): ByteArray =
        tagged(0x0C, value.toByteArray(Charsets.UTF_8))

    private fun generalizedTime(value: Instant): ByteArray =
        tagged(0x18, timeFormatter.format(value).toByteArray(Charsets.US_ASCII))

    private fun oid(value: String): ByteArray {
        val parts = value.split(".").map { it.toLong() }
        require(parts.size >= 2)
        val output = ByteArrayOutputStream()
        output.write((parts[0] * 40 + parts[1]).toInt())
        for (part in parts.drop(2)) {
            val stack = ArrayDeque<Int>()
            var current = part
            stack.addFirst((current and 0x7F).toInt())
            current = current shr 7
            while (current > 0) {
                stack.addFirst(((current and 0x7F) or 0x80).toInt())
                current = current shr 7
            }
            for (item in stack) output.write(item)
        }
        return tagged(0x06, output.toByteArray())
    }

    private fun tagged(tag: Int, value: ByteArray): ByteArray =
        byteArrayOf(tag.toByte()) + length(value.size) + value

    private fun length(size: Int): ByteArray {
        if (size < 128) return byteArrayOf(size.toByte())
        var current = size
        val bytes = ArrayDeque<Byte>()
        while (current > 0) {
            bytes.addFirst((current and 0xFF).toByte())
            current = current ushr 8
        }
        return byteArrayOf((0x80 or bytes.size).toByte()) + bytes.toByteArray()
    }

    private fun concat(vararg parts: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        for (part in parts) output.write(part)
        return output.toByteArray()
    }
}
