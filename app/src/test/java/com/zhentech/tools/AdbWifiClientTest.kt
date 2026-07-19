package com.zhentech.tools

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.util.Base64

class AdbWifiClientTest {
    @Test
    fun messageRoundTripsThroughAdbWireFormat() {
        val original = AdbMessage.open(7, "service call phone 185 i32 1 i32 0")
        val bytes = ByteArrayOutputStream().also(original::writeTo).toByteArray()

        val decoded = AdbMessage.readFrom(ByteArrayInputStream(bytes))

        assertEquals(original.command, decoded.command)
        assertEquals(original.arg0, decoded.arg0)
        assertEquals(original.arg1, decoded.arg1)
        assertArrayEquals(original.data, decoded.data)
    }

    @Test
    fun publicKeyUsesAndroidAdbRsaLayout() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val adbKey = AdbWifiClient.encodeAdbPublicKey(keyPair).toString(Charsets.UTF_8)
        val encoded = Base64.getDecoder().decode(adbKey.substringBefore(' '))
        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(524, encoded.size)
        assertEquals(64, buffer.int)
        buffer.position(encoded.size - Int.SIZE_BYTES)
        assertEquals(65_537, buffer.int)
    }

    @Test
    fun transactionCodesMatchSupportedAndroidReleases() {
        assertEquals(187, simPowerTransactionCode(31))
        assertEquals(182, simPowerTransactionCode(33))
        assertEquals(186, simPowerTransactionCode(34))
        assertEquals(185, simPowerTransactionCode(36))
        assertNull(simPowerTransactionCode(30))
        assertNull(simPowerTransactionCode(38))
    }

    @Test
    fun serviceCallMustReturnAParcelWithoutAnError() {
        assertTrue(isSuccessfulServiceCallOutput("Result: Parcel(00000000  '....')"))
        assertFalse(isSuccessfulServiceCallOutput(""))
        assertFalse(
            isSuccessfulServiceCallOutput(
                "Result: Parcel(00000000) SecurityException: Permission Denial",
            ),
        )
    }
}
