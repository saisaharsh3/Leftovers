package com.leftovers.app.ai

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDeviceTest {
    /** Answers with [replies] in turn, and keeps what it was asked. */
    private class Fake(vararg replies: String, var ready: Boolean = true) : LocalModel {
        val queue = ArrayDeque(replies.toList())
        val prompts = mutableListOf<String>()
        override suspend fun status() = if (ready) LocalStatus.READY else LocalStatus.UNAVAILABLE
        override suspend fun download() {}
        val images = mutableListOf<ByteArray?>()
        override suspend fun generate(prompt: String, maxOutputTokens: Int, image: ByteArray?): String {
            prompts += prompt
            images += image
            return queue.removeFirstOrNull() ?: ""
        }
        override suspend fun tokenLimit() = 4_000
    }

    @org.junit.Before fun quick() { OnDevice.startupMs = 0 }

    @After fun reset() {
        OnDevice.override = null
        OnDevice.startupMs = 15_000
    }

    @Test fun waitsForThePhonesAiToStart() = runBlocking {
        // Android says "not available" for a moment after the app opens, then it's ready.
        var asked = 0
        OnDevice.override = object : LocalModel {
            override suspend fun status() = if (++asked < 3) LocalStatus.UNAVAILABLE else LocalStatus.READY
            override suspend fun download() {}
            override suspend fun generate(prompt: String, maxOutputTokens: Int, image: ByteArray?) = "Groceries"
            override suspend fun tokenLimit() = 4_000
        }
        OnDevice.startupMs = 5_000
        var told = false
        assertEquals(LocalStatus.READY, OnDevice.awaitReady { told = true })
        assertTrue(told)
        assertEquals(3, asked)
    }

    @Test fun readsAPaymentButNeverInventsTheAmount() = runBlocking {
        val msg = "Acct 1234: INR 640.00 gone via NEFT to RAVI TRADERS on 07-10-26"
        OnDevice.override = Fake("""```json {"payment": true, "direction": "out", "amount": 640.00, "who": "Ravi Traders"} ```""")
        val read = OnDevice.readPayment(msg)!!
        assertEquals(640_00L, read.amountMinor)
        assertEquals("Ravi Traders", read.merchant)
        assertEquals(false, read.isIncome)
        // An amount that isn't in the message is thrown away.
        OnDevice.override = Fake("""{"payment": true, "direction": "out", "amount": 6400, "who": "Ravi"}""")
        assertNull(OnDevice.readPayment(msg))
        // Not a payment, or no model ready: nothing.
        OnDevice.override = Fake("""{"payment": false}""")
        assertNull(OnDevice.readPayment(msg))
        OnDevice.override = Fake("""{"payment": true, "amount": 640}""", ready = false)
        assertNull(OnDevice.readPayment(msg))
    }

    @Test fun picksOnlyARealCategory() = runBlocking {
        val names = listOf("Food & Dining", "Groceries", "Transport")
        OnDevice.override = Fake("Groceries.")
        assertEquals("Groceries", OnDevice.pickCategory("Fresh mart", names))
        OnDevice.override = Fake("I think **Food & Dining**")
        assertEquals("Food & Dining", OnDevice.pickCategory("Biryani house", names))
        OnDevice.override = Fake("Shopping")
        assertNull(OnDevice.pickCategory("Mall", names))
    }

    @Test fun theAssistantCallsToolsFromPlainText() = runBlocking {
        val tools = listOf(
            ToolSpec("summarize", "Totals for a period.", listOf(Param("month", "string", "YYYY-MM", required = true))),
            ToolSpec("add_entry", "Add an entry.", listOf(Param("amount", "number", "Amount", required = true)), changesData = true),
        )
        val fake = Fake("""CALL {"tool": "summarize", "args": {"month": "2026-10"}}""", "You spent ₹2,800 in October.")
        val session = OnDeviceSession("You are the assistant. Today is 2026-11-05.\n\nLong rules here", tools) { fake }
        val first = session.sendUser("How much did I spend in October?")
        assertEquals("summarize", first.calls.single().name)
        assertEquals("2026-10", first.calls.single().args.getString("month"))
        val second = session.sendToolResults(listOf(ToolOutcome(first.calls.single(), """{"spent": 2800}""")))
        assertEquals("You spent ₹2,800 in October.", second.text)
        assertTrue(second.calls.isEmpty())
        // The prompt has the app's facts and the tools, not the long rules, and the tool's result.
        val last = fake.prompts.last()
        assertTrue(last.contains("Today is 2026-11-05") && !last.contains("Long rules"))
        assertTrue(last.contains("- add_entry(amount:number*)"))
        assertTrue(last.contains("""Result of summarize: {"spent": 2800}"""))
    }

    @Test fun aFailedCheckSaysWhy() = runBlocking {
        val broken = object : LocalModel {
            override suspend fun status(): LocalStatus = throw IllegalStateException()
            override suspend fun download() {}
            override suspend fun generate(prompt: String, maxOutputTokens: Int, image: ByteArray?) = ""
            override suspend fun tokenLimit() = 4_000
            override fun explain(e: Throwable) = "Android AICore is missing (code -101)"
        }
        OnDevice.override = broken
        assertEquals(LocalStatus.UNAVAILABLE, OnDevice.status())
        assertEquals("Android AICore is missing (code -101)", OnDevice.problem)
        val e = runCatching { OnDeviceSession("Facts.", emptyList()) { broken }.sendUser("Hi") }.exceptionOrNull()
        assertTrue(e is AiException && e.message!!.contains("code -101"))
    }

    @Test fun aBillPhotoGoesToTheModel() = runBlocking {
        val tools = listOf(ToolSpec("add_entry", "Add an entry.", listOf(Param("amount", "number", "Amount", required = true)), changesData = true))
        val fake = Fake("""CALL {"tool": "add_entry", "args": {"amount": 3879.03}}""", "Added it.")
        val session = OnDeviceSession("Facts.", tools) { fake }
        val bytes = byteArrayOf(1, 2, 3)
        val first = session.sendUser("Add this", ImageInput("image/jpeg", java.util.Base64.getEncoder().encodeToString(bytes)))
        assertEquals("add_entry", first.calls.single().name)
        assertTrue(fake.prompts.last().contains("The photo is a bill"))
        // The photo stays with every step of the answer to it, and goes once it's answered.
        session.sendToolResults(listOf(ToolOutcome(first.calls.single(), "Applied")))
        assertTrue(fake.images.all { it.contentEquals(bytes) })
        fake.queue.add("Nothing else.")
        session.sendUser("Thanks")
        assertNull(fake.images.last())
    }

    @Test fun unknownToolsAreJustText() {
        assertNull(OnDeviceSession.parseCall("""CALL {"tool": "wipe_phone", "args": {}}""", setOf("summarize")))
        assertNull(OnDeviceSession.parseCall("You spent ₹500.", setOf("summarize")))
    }

    @Test fun aLongChatStillFits() = runBlocking {
        val session = OnDeviceSession("Facts.", emptyList()) { Fake(*Array(40) { "ok" }) }
        repeat(40) { session.sendUser("Question $it " + "words ".repeat(60)) }
        val prompt = session.prompt(4_000)
        assertTrue(prompt.length < (4_000 - 400) * 3.5 + 50)
        // The newest question is kept; the oldest went first.
        assertTrue(prompt.contains("Question 39") && !prompt.contains("Question 0 "))
    }
}
