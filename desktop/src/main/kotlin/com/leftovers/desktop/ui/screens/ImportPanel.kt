package com.leftovers.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leftovers.app.data.TxType
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.util.LocalMoney
import com.leftovers.desktop.data.ImportPreview
import com.leftovers.desktop.data.ImportProblem
import com.leftovers.desktop.data.PdfPasswordNeeded
import com.leftovers.desktop.data.Statements
import com.leftovers.desktop.data.importStatement
import com.leftovers.desktop.data.previewImport
import com.leftovers.desktop.ui.AppState
import com.leftovers.desktop.ui.LocalApp
import com.leftovers.desktop.ui.components.Button
import com.leftovers.desktop.ui.components.ButtonKind
import com.leftovers.desktop.ui.components.Dialog
import com.leftovers.desktop.ui.components.Field
import com.leftovers.desktop.ui.components.HoverRow
import com.leftovers.desktop.ui.components.SidePanel
import com.leftovers.desktop.ui.components.pressable
import com.leftovers.desktop.ui.theme.T
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** A statement being imported: read, maybe waiting for its PDF password, then shown to check before adding. */
class Importer(private val state: AppState) {
    var file by mutableStateOf<File?>(null)
    var asking by mutableStateOf(false)
    var wrong by mutableStateOf(false)
    var preview by mutableStateOf<ImportPreview?>(null)
    var problem by mutableStateOf<String?>(null)

    fun pick() {
        Files.open("Import a bank statement", listOf("csv", "tsv", "txt", "xlsx", "xls", "pdf", "html", "htm", "json"))?.let(::start)
    }

    fun start(f: File) {
        file = f
        read(null)
    }

    fun read(password: String?) {
        val f = file ?: return
        try {
            val parsed = Statements.read(f.readBytes(), password)
            asking = false
            if (parsed.rows.isEmpty()) {
                problem = "No transactions found in ${f.name}." + if (parsed.unreadable > 0) " ${parsed.unreadable} lines had no date or amount." else ""
                return
            }
            preview = state.store.previewImport(parsed)
        } catch (e: PdfPasswordNeeded) {
            wrong = e.wrong; asking = true
        } catch (e: ImportProblem) {
            problem = e.message
        } catch (e: Exception) {
            problem = "Couldn't read ${f.name}."
        }
    }

    fun close() { file = null; asking = false; wrong = false; preview = null; problem = null }
}

private val day = DateTimeFormatter.ofPattern("d MMM yyyy")

@Composable
fun ImportPanel(importer: Importer) {
    val app = LocalApp.current
    val c = T.c
    val money = LocalMoney.current
    var password by remember { mutableStateOf("") }
    Dialog(importer.asking, { importer.close() }) {
        Text("This statement has a password", style = T.type.title, color = c.text)
        Spacer(Modifier.height(6.dp))
        Text("Banks often use a date of birth or a customer ID.", style = T.type.body, color = c.text2)
        Spacer(Modifier.height(16.dp))
        Field(password, { password = it; importer.wrong = false }, Modifier.fillMaxWidth(), placeholder = "Password", password = true, onEnter = { importer.read(password) })
        if (importer.wrong) Text("That password isn't right.", style = T.type.small, color = c.negative, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            Button("Cancel", { importer.close() }, kind = ButtonKind.Quiet)
            Button("Open", { importer.read(password) }, kind = ButtonKind.Primary, enabled = password.isNotEmpty())
        }
    }
    Dialog(importer.problem != null, { importer.close() }) {
        Text("Couldn't import it", style = T.type.title, color = c.text)
        Spacer(Modifier.height(6.dp))
        Text(importer.problem.orEmpty(), style = T.type.body, color = c.text2)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Button("OK", { importer.close() }, kind = ButtonKind.Primary) }
    }
    val p = importer.preview
    fun doImport() {
        val pv = importer.preview ?: return
        val n = app.store.importStatement(pv)
        importer.close()
        app.tell("$n ${if (n == 1) "entry" else "entries"} imported") { app.store.undo() }
    }
    SidePanel(
        p != null, "Import ${importer.file?.name.orEmpty()}", { importer.close() }, width = 820.dp, onSave = ::doImport,
        footer = {
            val picked = p?.rows?.count { it.include } ?: 0
            Text(
                listOfNotNull(
                    p?.duplicates?.takeIf { it > 0 }?.let { "$it already here, skipped" },
                    p?.transfers?.takeIf { it > 0 }?.let { "$it transfers between your accounts left out" },
                    p?.unreadable?.takeIf { it > 0 }?.let { "$it lines unreadable" },
                ).joinToString(" · "),
                style = T.type.small, color = c.text3, modifier = Modifier.weight(1f),
            )
            Button("Cancel", { importer.close() }, kind = ButtonKind.Quiet)
            Button(if (picked == 1) "Import 1 entry" else "Import $picked entries", ::doImport, kind = ButtonKind.Primary, enabled = picked > 0)
        },
    ) {
        if (p == null) return@SidePanel
        val rows = p.rows
        val spent = rows.filter { it.include && it.entry.type == TxType.EXPENSE }.sumOf { it.entry.amountMinor }
        val earned = rows.filter { it.include && it.entry.type == TxType.INCOME }.sumOf { it.entry.amountMinor }
        val from = rows.minOfOrNull { it.entry.epochDay }?.let(LocalDate::ofEpochDay)
        val to = rows.maxOfOrNull { it.entry.epochDay }?.let(LocalDate::ofEpochDay)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${rows.size} new ${if (rows.size == 1) "entry" else "entries"}" + (if (from != null && to != null) " from ${from.format(day)} to ${to.format(day)}" else ""),
                style = T.type.heading, color = c.text,
            )
            Text(
                listOfNotNull(
                    "${money.format(spent)} spent".takeIf { spent > 0 },
                    "+${money.format(earned)} in".takeIf { earned > 0 },
                    p.guessedCount.takeIf { it > 0 }?.let { "$it categories taken from your past entries" },
                    p.newCategories.takeIf { it.isNotEmpty() }?.let { list -> "new: " + list.joinToString(", ") { it.second } },
                ).joinToString(" · "),
                style = T.type.small, color = c.text2,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button("Pick all", { importer.preview = p.copy(rows = rows.map { it.copy(include = true) }) }, kind = ButtonKind.Quiet)
            Button("Pick none", { importer.preview = p.copy(rows = rows.map { it.copy(include = false) }) }, kind = ButtonKind.Quiet)
        }
        Column {
            rows.forEachIndexed { i, r ->
                androidx.compose.runtime.key(i) {
                    ImportRow(r, onToggle = {
                        importer.preview = p.copy(rows = rows.toMutableList().also { it[i] = r.copy(include = !r.include) })
                    }) { categoryId, name ->
                        importer.preview = p.copy(rows = rows.toMutableList().also { it[i] = r.copy(entry = r.entry.copy(categoryId = categoryId), categoryName = name, newCategory = null, guessed = false) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportRow(r: ImportPreview.Row, onToggle: () -> Unit, onCategory: (Long, String) -> Unit) {
    val c = T.c
    val money = LocalMoney.current
    val app = LocalApp.current
    var menu by remember { mutableStateOf(false) }
    HoverRow(onToggle, Modifier.fillMaxWidth().alpha(if (r.include) 1f else 0.45f), padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
        Box(
            Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(if (r.include) c.accent else c.raised),
            contentAlignment = Alignment.Center,
        ) { if (r.include) Icon(Lucide.Check, null, Modifier.size(13.dp), tint = c.onAccent) }
        Spacer(Modifier.width(14.dp))
        Text(LocalDate.ofEpochDay(r.entry.epochDay).format(day), style = T.type.small, color = c.text2, modifier = Modifier.width(96.dp))
        Column(Modifier.weight(1f)) {
            Text(r.entry.note.ifBlank { r.categoryName }, style = T.type.bodyStrong, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (r.entry.details.isNotBlank()) Text(r.entry.details, style = T.type.small, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        Box {
            Row(
                Modifier.clip(CircleShape).background(c.raised).pressable(scaleTo = 0.96f) { menu = true }.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (r.guessed) { Icon(Lucide.Sparkles, "From past entries", Modifier.size(12.dp), tint = c.text2); Spacer(Modifier.width(4.dp)) }
                Text(r.categoryName + if (r.newCategory != null) " (new)" else "", style = T.type.small.copy(fontWeight = FontWeight.SemiBold), color = c.text, maxLines = 1)
                Icon(Lucide.ChevronDown, null, Modifier.size(13.dp), tint = c.text3)
            }
            DropdownMenu(menu, { menu = false }, shape = RoundedCornerShape(12.dp), containerColor = if (c.dark) Color(0xFF1E1E26) else Color.White) {
                app.store.book.categories.filter { it.type == r.entry.type && !it.hidden }.forEach { cat ->
                    DropdownMenuItem({ Text(cat.name, style = T.type.body, color = c.text) }, { onCategory(cat.id, cat.name); menu = false })
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            (if (r.entry.type == TxType.INCOME) "+" else "") + money.format(r.entry.amountMinor), style = T.type.bodyStrong,
            color = if (r.entry.type == TxType.INCOME) c.positive else c.text, modifier = Modifier.width(110.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}
