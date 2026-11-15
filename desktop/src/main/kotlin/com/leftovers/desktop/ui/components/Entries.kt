package com.leftovers.desktop.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leftovers.app.data.TransactionItem
import com.leftovers.app.data.TxType
import com.leftovers.app.data.noteText
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.util.LocalMoney
import com.leftovers.app.util.Money
import com.leftovers.app.util.friendlyLabel
import com.leftovers.desktop.ui.LocalApp
import com.leftovers.desktop.ui.theme.Motion
import com.leftovers.desktop.ui.theme.T
import java.time.LocalDate

/**
 * A figure whose characters roll when it changes: a digit that goes up slides up, one that goes down slides down,
 * and the ones that stay put stay still.
 */
@Composable
fun RollingText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    Row(modifier) {
        // Aligned from the right, so "999" to "1,000" rolls the digits that changed rather than shifting them all.
        text.forEachIndexed { i, ch ->
            val fromEnd = text.length - i
            androidx.compose.runtime.key(fromEnd) {
                AnimatedContent(
                    ch,
                    transitionSpec = {
                        val up = (targetState.digitToIntOrNull() ?: 0) > (initialState.digitToIntOrNull() ?: 0)
                        (slideInVertically(Motion.offset) { if (up) it else -it } + fadeIn(Motion.settle()))
                            .togetherWith(slideOutVertically(Motion.offset) { if (up) -it else it } + fadeOut(Motion.quick()))
                    },
                    label = "digit",
                ) { Text(it.toString(), style = style, color = color) }
            }
        }
    }
}

/** An entry in a list: its icon, name, category and day, and the amount, green when money came in. */
@Composable
fun EntryRow(
    item: TransactionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    part: Boolean = false,
    showDay: Boolean = true,
    account: String? = null,
) {
    val c = T.c
    val money = LocalMoney.current
    val icons = LocalApp.current.store.book.icons
    HoverRow(onClick, modifier.fillMaxWidth().padding(start = if (part) 34.dp else 0.dp)) {
        IconBadge(item.categoryEmoji, item.categoryColor, if (part) 30.dp else 36.dp, icons)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                noteText(item.note).ifBlank { item.categoryName }, style = T.type.bodyStrong, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val bits = listOfNotNull(
                item.categoryName.takeIf { noteText(item.note).isNotBlank() },
                account,
                item.date.friendlyLabel().takeIf { showDay },
            )
            if (bits.isNotEmpty()) Text(bits.joinToString(" · "), style = T.type.small, color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.receiptPath != null) {
                    Icon(Lucide.ReceiptText, "Has a photo", Modifier.size(13.dp), tint = c.text3)
                    Spacer(Modifier.width(6.dp))
                }
                Text(amountText(money, item), style = T.type.bodyStrong, color = if (item.type == TxType.INCOME) c.positive else c.text, maxLines = 1)
            }
            if (item.foreignMinor != null && item.foreignCurrency != null) {
                Text(Money(item.foreignCurrency!!).format(item.foreignMinor!!), style = T.type.small, color = c.text3, maxLines = 1)
            }
        }
    }
}

fun amountText(money: Money, item: TransactionItem): String =
    (if (item.type == TxType.INCOME) "+" else "") + money.format(item.amountMinor)

/** "Today", "Yesterday", or the day, for list headings. */
fun dayHeading(day: LocalDate, today: LocalDate = LocalDate.now()): String = day.friendlyLabel(today)

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = T.type.heading, color = T.c.text, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Box(Modifier.pressable(onClick = onAction).padding(vertical = 2.dp)) { Text(action, style = T.type.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = T.c.text2) }
        }
    }
}
