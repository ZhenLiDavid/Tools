package com.zhentech.tools

import android.content.Context
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

internal class AdbWifiClient(context: Context) {
    private val keyStore = AdbKeyStore(context.applicationContext)

    fun executeShell(command: String): Result<String> = runCatching {
        Socket().use { socket ->
            socket.soTimeout = SOCKET_TIMEOUT_MILLIS
            socket.connect(InetSocketAddress(LOOPBACK_ADDRESS, DEFAULT_ADB_PORT), CONNECT_TIMEOUT_MILLIS)
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            authenticate(input, output)
            executeLegacyShell(input, output, command)
        }
    }

    private fun authenticate(input: InputStream, output: OutputStream) {
        AdbMessage.connect().writeTo(output)
        val keyPair = keyStore.getOrCreate()
        var sentSignature = false
        var sentPublicKey = false

        while (true) {
            val message = AdbMessage.readFrom(input)
            when (message.command) {
                A_CNXN -> return
                A_AUTH -> when {
                    message.arg0 != AUTH_TOKEN -> error("Unsupported ADB authentication request")
                    !sentSignature -> {
                        AdbMessage.authSignature(signToken(keyPair, message.data)).writeTo(output)
                        sentSignature = true
                    }
                    !sentPublicKey -> {
                        AdbMessage.authPublicKey(encodeAdbPublicKey(keyPair)).writeTo(output)
                        sentPublicKey = true
                    }
                    else -> error("ADB Wi-Fi authorization was rejected")
                }
                else -> error("Unexpected ADB message during authentication")
            }
        }
    }

    private fun executeLegacyShell(
        input: InputStream,
        output: OutputStream,
        command: String,
    ): String {
        val localId = 1
        var remoteId = 0
        AdbMessage.open(localId, command).writeTo(output)
        val response = StringBuilder()

        while (true) {
            val message = AdbMessage.readFrom(input)
            when (message.command) {
                A_OKAY -> if (remoteId == 0) remoteId = message.arg0
                A_WRTE -> {
                    if (response.length + message.data.size > MAX_RESPONSE_CHARACTERS) {
                        error("ADB response was unexpectedly large")
                    }
                    response.append(message.data.toString(Charsets.UTF_8))
                    AdbMessage(A_OKAY, localId, message.arg0, byteArrayOf()).writeTo(output)
                }
                A_CLSE -> {
                    if (remoteId != 0) {
                        AdbMessage(A_CLSE, localId, remoteId, byteArrayOf()).writeTo(output)
                    }
                    return response.toString()
                }
                else -> Unit
            }
        }
    }

    private fun signToken(keyPair: KeyPair, token: ByteArray): ByteArray {
        require(token.size == ADB_TOKEN_SIZE) { "Invalid ADB token" }
        val signature = Signature.getInstance("NONEwithRSA")
        signature.initSign(keyPair.private)
        signature.update(SHA1_DIGEST_INFO_PREFIX)
        signature.update(token)
        return signature.sign()
    }

    internal companion object {
        const val DEFAULT_ADB_PORT = 5555
        private const val LOOPBACK_ADDRESS = "127.0.0.1"
        private const val CONNECT_TIMEOUT_MILLIS = 2_000
        private const val SOCKET_TIMEOUT_MILLIS = 45_000
        private const val MAX_RESPONSE_CHARACTERS = 256 * 1024
        private const val ADB_TOKEN_SIZE = 20

        private val SHA1_DIGEST_INFO_PREFIX = byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02,
            0x1a, 0x05, 0x00, 0x04, 0x14,
        )

        fun encodeAdbPublicKey(keyPair: KeyPair): ByteArray {
            val publicKey = keyPair.public as RSAPublicKey
            val modulus = publicKey.modulus
            val wordBase = BigInteger.ONE.shiftLeft(32)
            val modulusWord = modulus.and(wordBase.subtract(BigInteger.ONE))
            val n0Inverse = modulusWord.modInverse(wordBase).negate().mod(wordBase)
            val rSquared = BigInteger.ONE.shiftLeft(ADB_RSA_BITS * 2).mod(modulus)
            val encoded = ByteBuffer.allocate(ADB_PUBLIC_KEY_SIZE).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(ADB_RSA_WORDS)
                .putInt(n0Inverse.toLong().toInt())
                .putLittleEndianWords(modulus)
                .putLittleEndianWords(rSquared)
                .putInt(publicKey.publicExponent.toInt())
                .array()
            val base64 = Base64.getEncoder().encodeToString(encoded)
            return "$base64 tools@android\u0000".toByteArray(Charsets.UTF_8)
        }

        private const val ADB_RSA_BITS = 2048
        private const val ADB_RSA_WORDS = ADB_RSA_BITS / 32
        private const val ADB_PUBLIC_KEY_SIZE = (2 + ADB_RSA_WORDS * 2 + 1) * 4

        private fun ByteBuffer.putLittleEndianWords(value: BigInteger): ByteBuffer = apply {
            val mask = BigInteger.ONE.shiftLeft(32).subtract(BigInteger.ONE)
            repeat(ADB_RSA_WORDS) { index ->
                putInt(value.shiftRight(index * 32).and(mask).toLong().toInt())
            }
        }
    }
}

private class AdbKeyStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun getOrCreate(): KeyPair {
        val privateKey = preferences.getString(KEY_PRIVATE, null)
        val publicKey = preferences.getString(KEY_PUBLIC, null)
        if (privateKey != null && publicKey != null) {
            val factory = KeyFactory.getInstance("RSA")
            return KeyPair(
                factory.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKey))),
                factory.generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey))),
            )
        }

        val generated = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        preferences.edit()
            .putString(KEY_PRIVATE, Base64.getEncoder().encodeToString(generated.private.encoded))
            .putString(KEY_PUBLIC, Base64.getEncoder().encodeToString(generated.public.encoded))
            .apply()
        return generated
    }

    private companion object {
        const val PREFERENCES_NAME = "adb_wifi"
        const val KEY_PRIVATE = "private_key"
        const val KEY_PUBLIC = "public_key"
    }
}

internal data class AdbMessage(
    val command: Int,
    val arg0: Int,
    val arg1: Int,
    val data: ByteArray,
) {
    fun writeTo(output: OutputStream) {
        val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(command)
            .putInt(arg0)
            .putInt(arg1)
            .putInt(data.size)
            .putInt(data.sumOf { it.toInt() and 0xff })
            .putInt(command xor -1)
            .array()
        output.write(header)
        output.write(data)
        output.flush()
    }

    companion object {
        fun connect(): AdbMessage = AdbMessage(
            A_CNXN,
            ADB_VERSION,
            ADB_MAX_DATA,
            "host::features=shell_v2,cmd\u0000".toByteArray(),
        )

        fun authSignature(signature: ByteArray): AdbMessage =
            AdbMessage(A_AUTH, AUTH_SIGNATURE, 0, signature)

        fun authPublicKey(publicKey: ByteArray): AdbMessage =
            AdbMessage(A_AUTH, AUTH_RSAPUBLICKEY, 0, publicKey)

        fun open(localId: Int, command: String): AdbMessage = AdbMessage(
            A_OPEN,
            localId,
            0,
            "shell:$command\u0000".toByteArray(),
        )

        fun readFrom(input: InputStream): AdbMessage {
            val headerBytes = input.readExactly(HEADER_SIZE)
            val header = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
            val command = header.int
            val arg0 = header.int
            val arg1 = header.int
            val length = header.int
            val checksum = header.int
            val magic = header.int
            require(command xor -1 == magic) { "Invalid ADB message" }
            require(length in 0..ADB_MAX_DATA) { "Invalid ADB payload length" }
            val data = input.readExactly(length)
            require(checksum == 0 || checksum == data.sumOf { it.toInt() and 0xff }) {
                "Invalid ADB payload checksum"
            }
            return AdbMessage(command, arg0, arg1, data)
        }
    }
}

private fun InputStream.readExactly(length: Int): ByteArray {
    val result = ByteArray(length)
    var offset = 0
    while (offset < length) {
        val read = read(result, offset, length - offset)
        if (read < 0) throw EOFException("ADB connection closed")
        offset += read
    }
    return result
}

private const val HEADER_SIZE = 24
private const val ADB_VERSION = 0x01000001
private const val ADB_MAX_DATA = 1024 * 1024
private const val AUTH_TOKEN = 1
private const val AUTH_SIGNATURE = 2
private const val AUTH_RSAPUBLICKEY = 3
private const val A_CNXN = 0x4e584e43
private const val A_AUTH = 0x48545541
private const val A_OPEN = 0x4e45504f
private const val A_OKAY = 0x59414b4f
private const val A_CLSE = 0x45534c43
private const val A_WRTE = 0x45545257
