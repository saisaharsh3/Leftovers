package com.leftovers.app.util

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** WebDavPlace against a local HTTPS server: the requests a Nextcloud-style server sees. */
class WebDavServerTest {
    private val server = MockWebServer()
    private lateinit var client: OkHttpClient

    @Before
    fun start() {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        server.useHttps(HandshakeCertificates.Builder().heldCertificate(cert).build().sslSocketFactory(), false)
        server.start()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        client = OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager).build()
    }

    @After
    fun stop() = server.shutdown()

    private fun place() = WebDavPlace("https://localhost:${server.port}/dav/files/sai/Leftovers", "sai", "app-pass", client)

    @Test
    fun firstListMakesTheFolder() {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(201))
        assertTrue(place().list().isEmpty())
        val propfind = server.takeRequest()
        assertEquals("PROPFIND", propfind.method)
        assertEquals("1", propfind.getHeader("Depth"))
        assertTrue(propfind.getHeader("Authorization")!!.startsWith("Basic "))
        val mkcol = server.takeRequest()
        assertEquals("MKCOL", mkcol.method)
        assertEquals("/dav/files/sai/Leftovers/", mkcol.path)
    }

    @Test
    fun writesReadsAndLists() {
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setBody("{\"app\":\"leftovers\"}"))
        server.enqueue(
            MockResponse().setResponseCode(207).setBody(
                "<d:multistatus xmlns:d=\"DAV:\"><d:response><d:href>/dav/files/sai/Leftovers/</d:href></d:response>" +
                    "<d:response><d:href>/dav/files/sai/Leftovers/leftovers-sync-a.json</d:href></d:response></d:multistatus>",
            ),
        )
        val p = place()
        p.write("leftovers-sync-a.json", "{}".toByteArray())
        assertEquals("{\"app\":\"leftovers\"}", String(p.read("leftovers-sync-a.json")))
        assertEquals(listOf("leftovers-sync-a.json"), p.list())
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("/dav/files/sai/Leftovers/leftovers-sync-a.json", put.path)
        assertEquals("{}", put.body.readUtf8())
        assertEquals("GET", server.takeRequest().method)
    }

    @Test
    fun wrongPasswordSaysSo() {
        server.enqueue(MockResponse().setResponseCode(401))
        val message = runCatching { place().list() }.exceptionOrNull()?.message.orEmpty()
        assertTrue(message, message.contains("app password"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun plainHttpIsRefused() {
        WebDavPlace("http://cloud.example.com/dav", "sai", "x")
    }
}
