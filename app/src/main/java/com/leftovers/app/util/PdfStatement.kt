package com.leftovers.app.util

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper

/**
 * Reads a bank statement PDF on the phone: its text comes out here, and [PdfLines] finds the transactions in it.
 */
object PdfStatement {
    /** The statement is protected; [wrong] when a password was tried and didn't open it. */
    class PasswordNeeded(val wrong: Boolean) : Exception("This PDF has a password")

    /** The statement's text, line by line in reading order. */
    fun text(context: Context, bytes: ByteArray, password: String? = null): String {
        PDFBoxResourceLoader.init(context.applicationContext)
        val doc = try {
            PDDocument.load(bytes, password.orEmpty())
        } catch (_: InvalidPasswordException) {
            throw PasswordNeeded(wrong = password != null)
        }
        return doc.use { PDFTextStripper().apply { sortByPosition = true }.getText(it) }
    }

    /** The statement's transactions, from its [text]. */
    fun parse(text: String): CsvImporter.Parsed = PdfLines.parse(text)
}
