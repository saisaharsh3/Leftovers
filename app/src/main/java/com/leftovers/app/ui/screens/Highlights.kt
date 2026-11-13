package com.leftovers.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.leftovers.app.ui.components.pressable
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.util.WhatsNew
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** How a section's colour washes in behind it: from the top, the bottom, or across. */
enum class Field { TOP, BOTTOM, ACROSS }

/** A soft wash of [tint] over near-black, like light falling on the section; [t] (0..1) moves it a little. */
fun DrawScope.drawField(kind: Field, tint: Color, t: Float) {
    drawRect(Color(0xFF07070B))
    val drift = sin(t * 2 * PI).toFloat()
    // Black at both edges, so one section runs into the next with no seam; the colour peaks high, low or in the middle.
    val peak = when (kind) {
        Field.TOP -> 0.3f
        Field.BOTTOM -> 0.7f
        Field.ACROSS -> 0.5f
    } + 0.03f * drift
    drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.12f to Color.Transparent, peak to tint.copy(alpha = 0.45f), 0.88f to Color.Transparent, 1f to Color.Transparent))
}

/** 0..1 every [millis], looping, for a field's slow movement. */
@Composable
fun rememberDrift(millis: Int): Float {
    val loop = rememberInfiniteTransition(label = "drift")
    val t by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(millis, easing = LinearEasing), RepeatMode.Restart), label = "t")
    return t
}

/** One highlight: its big words, the kicker over its title, its line, the field behind it, and a piece of the app. */
private class Section(
    val big: String,
    val note: WhatsNew.Headline,
    val field: Field,
    val tint: Color,
    val sample: @Composable () -> Unit,
)

private val ink = Color(0xFFF2F3F7)
private val ink2 = Color(0xFFA9ADBA)
private val lime = Color(0xFFCDF46F)

/**
 * What's new, as a phone maker shows a new system: the version huge, then one change per screen with a few big words,
 * its title and line, and a piece of the app as it looks, over a drawn field of its own. Sections rise in and their
 * big words drift as they pass. The rest of the changes, in a list, then Continue.
 */
@Composable
fun HighlightsScreen(version: String, onClose: () -> Unit) {
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val list = rememberLazyListState()
        val t = rememberDrift(24_000)
        val sections = remember { highlightSections() }
        Box(Modifier.fillMaxSize().background(Color(0xFF05060A))) {
            LazyColumn(Modifier.fillMaxSize(), state = list) {
                item { Opening(version, list, t) }
                itemsIndexed(sections) { i, s -> Highlight(s, list, i + 1, t) }
                item { TheRest(onClose) }
            }
            // Always there to leave by.
            Box(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(12.dp).align(Alignment.TopEnd)) {
                com.leftovers.app.ui.components.RoundButton(Lucide.X, "Close", onClose, size = 44.dp, tint = ink, container = Color.White.copy(alpha = 0.1f))
            }
        }
    }
}

/** How far [index] is through the screen: 0 as it comes up from the bottom, 1 once it's well in view. */
@Composable
private fun arrival(list: LazyListState, index: Int): Float {
    val p by remember(list, index) {
        derivedStateOf {
            val info = list.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return@derivedStateOf if (index < list.firstVisibleItemIndex) 1f else 0f
            val view = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
            ((view - item.offset) / (view * 0.55f)).coerceIn(0f, 1f)
        }
    }
    return p
}

/** How far [index] has scrolled past its resting place, in pixels: for the big words to drift against it. */
@Composable
private fun passed(list: LazyListState, index: Int): Float {
    val p by remember(list, index) {
        derivedStateOf { list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.offset?.toFloat() ?: 0f }
    }
    return p
}

@Composable
private fun Opening(version: String, list: LazyListState, t: Float) {
    val number = version.substringBefore("-").split('.').take(2).joinToString(".")
    val off = passed(list, 0)
    Box(Modifier.fillMaxWidth().height(760.dp)) {
        Canvas(Modifier.fillMaxSize()) { drawField(Field.BOTTOM, Color(0xFF6C63FF), t) }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp).graphicsLayer { translationY = -off * 0.35f; alpha = (1f + off / 900f).coerceIn(0f, 1f) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("LEFTOVERS", style = TextStyle(fontSize = 13.sp, letterSpacing = 6.sp, fontWeight = FontWeight.SemiBold), color = ink2)
            Spacer(Modifier.height(12.dp))
            Text(
                number,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 148.sp, lineHeight = 150.sp, letterSpacing = (-7).sp, fontWeight = FontWeight.Bold,
                    brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFB9C1FF))),
                ),
            )
            Spacer(Modifier.height(8.dp))
            Text("Money that keeps up with you.", style = MaterialTheme.typography.titleLarge, color = ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("Scroll to see what's new", style = MaterialTheme.typography.bodyMedium, color = ink2)
            Spacer(Modifier.height(18.dp))
            Icon(Lucide.ChevronDown, contentDescription = null, tint = ink2, modifier = Modifier.size(22.dp).graphicsLayer { translationY = sin(t * 2 * PI.toFloat() * 12) * 6f })
        }
    }
}

@Composable
private fun Highlight(s: Section, list: LazyListState, index: Int, t: Float) {
    val p = arrival(list, index)
    val off = passed(list, index)
    Box(Modifier.fillMaxWidth().heightIn(min = 720.dp)) {
        Canvas(Modifier.matchParentSize()) {
            drawField(s.field, s.tint, t)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // The big words: they drift against the scroll, and light up as they arrive.
            Text(
                s.big,
                style = TextStyle(
                    fontSize = 64.sp, lineHeight = 60.sp, letterSpacing = (-2).sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                    brush = Brush.verticalGradient(listOf(Color.White, s.tint.copy(alpha = 0.55f))),
                ),
                modifier = Modifier.graphicsLayer {
                    // Drifts up as the section comes in, never down into the words under it.
                    translationY = (-off * 0.1f).coerceIn(-70f, 0f)
                    alpha = p
                    scaleX = 0.92f + 0.08f * p
                    scaleY = 0.92f + 0.08f * p
                },
            )
            Spacer(Modifier.height(48.dp))
            Column(
                Modifier.widthIn(max = 520.dp).graphicsLayer { alpha = p; translationY = (1f - p) * 60f },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(s.note.kicker, style = TextStyle(fontSize = 13.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, brush = Brush.horizontalGradient(listOf(s.tint, lime))))
                Spacer(Modifier.height(10.dp))
                Text(s.note.title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp), color = ink, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(s.note.text, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp), color = ink2, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(36.dp))
            // A piece of the app, as it looks: it rises a little later than the words.
            val q = ((p - 0.2f) / 0.8f).coerceIn(0f, 1f)
            Box(Modifier.widthIn(max = 420.dp).fillMaxWidth().graphicsLayer { alpha = q; translationY = (1f - q) * 90f; scaleX = 0.94f + 0.06f * q; scaleY = 0.94f + 0.06f * q }) {
                s.sample()
            }
        }
    }
}

/** A card in the app's glass, for the samples: they're how the real screens look. */
@Composable
private fun SampleCard(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.04f))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.06f))), shape)
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun SampleRow(icon: ImageVector, title: String, sub: String, amount: String, check: Boolean = false, amountColor: Color = ink) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = ink)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = ink2)
        }
        Text(amount, style = MaterialTheme.typography.titleSmall, color = amountColor)
        if (check) {
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(34.dp).clip(CircleShape).background(lime), contentAlignment = Alignment.Center) {
                Icon(Lucide.Check, contentDescription = null, tint = Color(0xFF151A04), modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun SamplePill(text: String, icon: ImageVector, filled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().height(54.dp).clip(CircleShape).background(if (filled) lime else Color.White.copy(alpha = 0.08f))
            .border(1.dp, if (filled) Color.Transparent else Color.White.copy(alpha = 0.16f), CircleShape),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (filled) Color(0xFF151A04) else ink, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = if (filled) Color(0xFF151A04) else ink)
    }
}

@Composable
private fun SampleBubble(text: String, mine: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(
            Modifier.widthIn(max = 280.dp).clip(if (mine) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
                .background(if (mine) lime else Color.White.copy(alpha = 0.1f)).padding(horizontal = 14.dp, vertical = 10.dp),
        ) { Text(text, style = MaterialTheme.typography.bodyMedium, color = if (mine) Color(0xFF151A04) else ink) }
    }
}

private fun highlightSections(): List<Section> = listOf(
    Section(
        "PAID\nBACK", WhatsNew.headlines[0],
        Field.TOP, Color(0xFF7FD9A8),
    ) {
        SampleCard { SampleRow(Lucide.Smartphone, "Money in", "Arjun paying you back?", "+₹2,500", check = true, amountColor = Color(0xFF8FE3B4)) }
    },
    Section(
        "ONE\nTAP", WhatsNew.headlines[1],
        Field.BOTTOM, Color(0xFFCDF46F),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SamplePill("Pay ₹750 with UPI", Lucide.Wallet)
            SamplePill("Pay with paypal.me", Lucide.ExternalLink, filled = false)
        }
    },
    Section(
        "SPLIT\nFOUR\nWAYS", WhatsNew.headlines[2],
        Field.ACROSS, Color(0xFFF291B4),
    ) {
        SampleCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Dinner · ₹3,000, split 4 ways", style = MaterialTheme.typography.bodySmall, color = ink2)
                listOf("Rahul", "Priya", "Nick").forEach { n ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 14.dp, vertical = 11.dp)) {
                        Text(n, style = MaterialTheme.typography.titleSmall, color = ink, modifier = Modifier.weight(1f))
                        Text("₹750", style = MaterialTheme.typography.titleSmall, color = ink)
                    }
                }
            }
        }
    },
    Section(
        "ON\nTIME", WhatsNew.headlines[3],
        Field.TOP, Color(0xFF7DB7F5),
    ) {
        SampleCard {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SampleRow(Lucide.Repeat, "Rent", "Due Sun, 1 Nov · ₹18,000", "Paid")
                SampleRow(Lucide.Smartphone, "Spotify", "Your Spotify? · bank SMS", "₹119", check = true)
            }
        }
    },
    Section(
        "YOUR\nBANK,\nIN SYNC", WhatsNew.headlines[4],
        Field.BOTTOM, Color(0xFFF2A65A),
    ) {
        SampleCard { SampleRow(Lucide.Landmark, "HDFC Bank", "Bank: ₹2,07,672 · today", "₹2,05,812") }
    },
    Section(
        "ON\nBUDGET", WhatsNew.headlines[5],
        Field.ACROSS, Color(0xFFC59AF2),
    ) {
        SampleCard {
            Column {
                Text("New daily limit", style = MaterialTheme.typography.labelLarge, color = ink2)
                Text("₹400", style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold), color = ink)
                Text("₹900 over today · from tomorrow", style = MaterialTheme.typography.bodySmall, color = ink2)
            }
        }
    },
    Section(
        "ASK\nANYTHING", WhatsNew.headlines[6],
        Field.TOP, Color(0xFF9C9CF8),
    ) {
        SampleCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SampleBubble("What did I spend on food this month?", mine = true)
                SampleBubble("₹6,240 on Food & Dining, ₹1,100 less than last month.", mine = false)
            }
        }
    },
    Section(
        "FIND\nIT", WhatsNew.headlines[7],
        Field.BOTTOM, Color(0xFF6FD0D0),
    ) {
        SampleCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.Search, contentDescription = null, tint = ink2, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Search settings", style = MaterialTheme.typography.bodyLarge, color = ink2)
            }
        }
    },
    Section(
        "MAKE\nIT\nYOURS", WhatsNew.headlines[8],
        Field.ACROSS, Color(0xFFF4A68A),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(listOf(Color(0xFF2A3D66), Color(0xFFC9B18F)), listOf(Color(0xFF3B3B44), Color(0xFFDADCE2)), listOf(Color(0xFF1B0F2E), Color(0xFFF08A4B))).forEach { g ->
                Box(Modifier.weight(1f).height(150.dp).clip(RoundedCornerShape(22.dp)).background(Brush.verticalGradient(g)).border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(22.dp)))
            }
        }
    },
)

/** Everything else in the version, in a list, then Continue. */
@Composable
private fun TheRest(onClose: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().background(Color(0xFF05060A)).padding(horizontal = 24.dp, vertical = 64.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("And more", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = ink)
            Spacer(Modifier.height(24.dp))
            Column(Modifier.widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                WhatsNew.items.forEach { n ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(whatsNewIcon(n.icon), contentDescription = null, tint = lime, modifier = Modifier.padding(top = 2.dp).size(22.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(n.title, style = MaterialTheme.typography.titleSmall, color = ink)
                            Text(n.text, style = MaterialTheme.typography.bodyMedium, color = ink2)
                        }
                    }
                }
            }
            Spacer(Modifier.height(36.dp))
            Box(Modifier.widthIn(max = 520.dp)) { SamplePillButton("Continue", onClose) }
        }
    }
}

@Composable
private fun SamplePillButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(56.dp).clip(CircleShape).background(lime)
            .pressable(onClick, pressedScale = 0.97f),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.titleMedium, color = Color(0xFF151A04)) }
}

private fun Color.compositeOver(under: Color): Color {
    val a = alpha
    return Color(red * a + under.red * (1 - a), green * a + under.green * (1 - a), blue * a + under.blue * (1 - a), 1f)
}
