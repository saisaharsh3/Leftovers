package com.leftovers.app.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/** Where Sync between devices keeps each phone's file: a folder on the phone, or the user's own WebDAV server. */
interface SyncPlace {
    /** Names of the files there. */
    fun list(): List<String>
    fun read(name: String): ByteArray
    fun write(name: String, bytes: ByteArray)
}

/** A folder the user picked, which Syncthing, Nextcloud's app or similar keeps the same on each phone. */
class FolderPlace(private val context: Context, private val tree: Uri) : SyncPlace {
    private val resolver get() = context.contentResolver

    private fun children(): List<Pair<Uri, String>> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val found = mutableListOf<Pair<Uri, String>>()
        resolver.query(uri, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1) ?: continue
                found += DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)) to name
            }
        }
        return found
    }

    override fun list() = children().map { it.second }

    override fun read(name: String): ByteArray {
        val uri = children().firstOrNull { it.second == name }?.first ?: error("$name isn't in the folder")
        return resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Couldn't open $name")
    }

    override fun write(name: String, bytes: ByteArray) {
        val uri = children().firstOrNull { it.second == name }?.first
            ?: DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)), "application/json", name)
            ?: error("Couldn't create a file in that folder")
        resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("Couldn't write $name")
    }
}

/**
 * A folder on the user's own WebDAV server, such as Nextcloud (https://cloud.example.com/remote.php/dav/files/NAME/Leftovers)
 * or ownCloud. Only https addresses; the folder is made if it isn't there yet.
 */
class WebDavPlace(
    url: String,
    private val user: String,
    private val password: String,
    private val client: OkHttpClient = defaultClient,
) : SyncPlace {
    private val folder = url.trim().trimEnd('/') + "/"

    init {
        require(folder.startsWith("https://", ignoreCase = true)) { "Use an https:// address" }
    }

    private fun request(path: String = "") = Request.Builder()
        .url(folder + java.net.URLEncoder.encode(path, "UTF-8").replace("+", "%20"))
        .header("Authorization", Credentials.basic(user, password))

    private fun failure(code: Int, what: String): Nothing = error(
        when (code) {
            401, 403 -> "The server didn't accept the name and password. For Nextcloud, use an app password"
            404 -> "The server couldn't find that address"
            else -> "The server said $code while $what"
        },
    )

    override fun list(): List<String> {
        val body = """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""
        val propfind = { request().header("Depth", "1").method("PROPFIND", body.toRequestBody(XML)).build() }
        client.newCall(propfind()).execute().use { response ->
            when {
                response.code == 404 -> {
                    // First time: make the folder.
                    client.newCall(request().method("MKCOL", null).build()).execute().use { if (!it.isSuccessful && it.code != 405) failure(it.code, "making the folder") }
                    return emptyList()
                }
                !response.isSuccessful -> failure(response.code, "listing the folder")
                else -> return names(response.body?.string().orEmpty())
            }
        }
    }

    override fun read(name: String): ByteArray =
        client.newCall(request(name).get().build()).execute().use { response ->
            if (!response.isSuccessful) failure(response.code, "reading $name")
            response.body?.bytes() ?: ByteArray(0)
        }

    override fun write(name: String, bytes: ByteArray) {
        client.newCall(request(name).put(bytes.toRequestBody(JSON)).build()).execute().use { response ->
            if (!response.isSuccessful) failure(response.code, "saving $name")
        }
    }

    companion object {
        private val defaultClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()
        }
        private val XML = "application/xml; charset=utf-8".toMediaType()
        private val JSON = "application/json".toMediaType()
        private val HREF = Regex("""<(?:[A-Za-z0-9]+:)?href>([^<]+)</(?:[A-Za-z0-9]+:)?href>""")

        /** What went wrong reaching the server, in plain words. */
        fun problem(error: Throwable): String = when (error) {
            is java.net.UnknownHostException -> "Couldn't find that server. Check the address and that you're online"
            is java.net.SocketTimeoutException, is java.net.ConnectException -> "The server didn't answer. Check the address and try again"
            is javax.net.ssl.SSLException -> "The server's security certificate isn't trusted by this phone"
            else -> error.message ?: "Couldn't reach the server"
        }

        /** File names in a PROPFIND reply (the folder itself, ending in /, is left out). */
        fun names(xml: String): List<String> = HREF.findAll(xml)
            .map { it.groupValues[1].trim() }
            .filterNot { it.endsWith("/") }
            .map { URLDecoder.decode(it.substringAfterLast('/').replace("+", "%2B"), "UTF-8") }
            .toList()
    }
}
