package com.leftovers.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class WebDavPlaceTest {
    @Test
    fun readsFileNamesFromNextcloudReply() {
        val xml = """<?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:" xmlns:s="http://sabredav.org/ns">
              <d:response><d:href>/remote.php/dav/files/sai/Leftovers/</d:href></d:response>
              <d:response><d:href>/remote.php/dav/files/sai/Leftovers/leftovers-sync-pixel-7-k3f9q2.json</d:href></d:response>
              <d:response><d:href>/remote.php/dav/files/sai/Leftovers/My%20notes.txt</d:href></d:response>
            </d:multistatus>"""
        assertEquals(listOf("leftovers-sync-pixel-7-k3f9q2.json", "My notes.txt"), WebDavPlace.names(xml))
    }

    @Test
    fun readsOtherServersPrefixes() {
        val xml = """<D:multistatus xmlns:D="DAV:"><D:response><D:href>https://dav.example.org/sync/leftovers-sync-a.json</D:href></D:response></D:multistatus>"""
        assertEquals(listOf("leftovers-sync-a.json"), WebDavPlace.names(xml))
    }
}
