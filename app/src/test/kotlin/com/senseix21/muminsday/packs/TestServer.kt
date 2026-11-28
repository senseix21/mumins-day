package com.senseix21.muminsday.packs

import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.SocketException
import java.security.KeyStore
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

/** One request as the server saw it: the request line and the headers, names lowercased. */
data class SeenRequest(val line: String, val headers: Map<String, String>)

/** How the server answers a path. */
sealed interface Behaviour {
    /** Serves [bytes], honouring a Range header with a 206 unless [ignoreRange]; stops after [cutAfter] body bytes. */
    data class Serve(val bytes: ByteArray, val ignoreRange: Boolean = false, val cutAfter: Int? = null) : Behaviour

    data class Status(val code: Int, val location: String? = null) : Behaviour
}

/**
 * A tiny HTTPS server for the JVM tests of the pack downloader (ADR-25), on 127.0.0.1 only, with a self-signed
 * certificate for `localhost` made by the JDK's own `keytool` at test time. Platform `javax.net.ssl` only: no test
 * library was added (INV-P4). One request per connection, `Connection: close`.
 */
class TestServer : AutoCloseable {
    val requests = CopyOnWriteArrayList<SeenRequest>()
    val behaviours = mutableMapOf<String, Behaviour>()

    private val context: SSLContext = SSLContext.getInstance("TLS").apply {
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keyStore, PASSWORD) }
        init(keys.keyManagers, null, null)
    }
    private val socket = context.serverSocketFactory.createServerSocket(0, 16, InetAddress.getByName("127.0.0.1")) as SSLServerSocket
    val port: Int = socket.localPort

    /** A client socket factory that trusts this server's certificate and nothing else. */
    val clientSockets: SSLSocketFactory = SSLContext.getInstance("TLS").apply {
        val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(keyStore) }
        init(null, trust.trustManagers, null)
    }.socketFactory

    private val thread = Thread {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (_: SocketException) {
                break
            }
            client.use {
                try {
                    answer(BufferedInputStream(it.getInputStream()), it.getOutputStream())
                } catch (_: java.io.IOException) {
                    // A client that hung up mid-body (a cancel) is expected.
                }
            }
        }
    }.apply {
        isDaemon = true
        start()
    }

    fun url(path: String): String = "https://localhost:$port/$path"

    private fun answer(input: InputStream, output: OutputStream) {
        val line = readLine(input)
        val headers = generateSequence { readLine(input).takeIf { it.isNotEmpty() } }
            .associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
        requests += SeenRequest(line, headers)
        val path = line.split(' ').getOrNull(1).orEmpty().removePrefix("/")
        when (val behaviour = behaviours[path] ?: Behaviour.Status(404)) {
            is Behaviour.Status -> {
                val location = behaviour.location?.let { "Location: $it\r\n" }.orEmpty()
                output.write("HTTP/1.1 ${behaviour.code} X\r\n${location}Content-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            }
            is Behaviour.Serve -> {
                val range = headers["range"]?.let { Regex("bytes=(\\d+)-").matchEntire(it) }?.groupValues?.get(1)?.toInt()
                val from = if (range != null && !behaviour.ignoreRange) range else 0
                val body = behaviour.bytes.copyOfRange(from, behaviour.bytes.size)
                val head = if (from > 0) {
                    "HTTP/1.1 206 Partial\r\nContent-Range: bytes $from-${behaviour.bytes.size - 1}/${behaviour.bytes.size}\r\n"
                } else {
                    "HTTP/1.1 200 OK\r\n"
                }
                output.write("${head}Content-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                val sent = behaviour.cutAfter?.coerceAtMost(body.size) ?: body.size
                output.write(body, 0, sent)
            }
        }
        output.flush()
    }

    private fun readLine(input: InputStream): String = buildString {
        while (true) {
            val byte = input.read()
            if (byte < 0 || byte == '\n'.code) break
            if (byte != '\r'.code) append(byte.toChar())
        }
    }

    override fun close() {
        socket.close()
        thread.join(2000)
    }

    private companion object {
        val PASSWORD = "test-only".toCharArray()

        /** Made once per test run in a temporary folder by the JDK's keytool; never committed. */
        val keyStore: KeyStore by lazy {
            val folder = kotlin.io.path.createTempDirectory("pack-tls").toFile()
            val file = File(folder, "localhost.p12")
            val keytool = File(System.getProperty("java.home"), "bin/keytool").path
            val process = ProcessBuilder(
                keytool, "-genkeypair", "-alias", "localhost", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                "-keystore", file.path, "-storetype", "PKCS12", "-storepass", String(PASSWORD), "-keypass", String(PASSWORD),
            ).redirectErrorStream(true).start()
            val log = process.inputStream.readBytes().toString(Charsets.UTF_8)
            check(process.waitFor() == 0) { "keytool failed: $log" }
            KeyStore.getInstance("PKCS12").apply { file.inputStream().use { load(it, PASSWORD) } }
        }
    }
}
