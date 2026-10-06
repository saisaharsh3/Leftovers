package com.leftovers.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leftovers.app.AppContainer
import com.leftovers.app.ai.AiException
import com.leftovers.app.ai.AiProvider
import com.leftovers.app.ai.AssistantConfig
import com.leftovers.app.ai.AssistantPrivacy
import com.leftovers.app.ai.AssistantSettings
import com.leftovers.app.ai.AssistantTools
import com.leftovers.app.ai.ChangeKind
import com.leftovers.app.ai.ChatSession
import com.leftovers.app.ai.ModelTurn
import com.leftovers.app.ai.Prepared
import com.leftovers.app.ai.ProposedChange
import com.leftovers.app.ai.ToolCall
import com.leftovers.app.ai.ToolOutcome
import com.leftovers.app.ai.chatSession
import com.leftovers.app.ai.listModels
import com.leftovers.app.ai.requireHttps
import com.leftovers.app.ui.AppViewModelProvider
import com.leftovers.app.ui.components.Chip
import com.leftovers.app.ui.components.Glass
import com.leftovers.app.ui.components.GlassScreen
import com.leftovers.app.ui.components.IconTile
import com.leftovers.app.ui.components.PrimaryButton
import com.leftovers.app.ui.components.RoundButton
import com.leftovers.app.ui.components.SecondaryButton
import com.leftovers.app.ui.components.SegmentedToggle
import com.leftovers.app.ui.components.frosted
import com.leftovers.app.ui.components.pressable
import com.leftovers.app.ui.icons.Lucide
import com.leftovers.app.ui.theme.LocalAppColors
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

sealed interface ChatItem {
    val key: Long

    data class User(override val key: Long, val text: String, val image: android.graphics.Bitmap? = null) : ChatItem
    data class Bot(override val key: Long, val text: String) : ChatItem
    data class Change(override val key: Long, val kind: ChangeKind, val summary: String, val state: ChangeState) : ChatItem

    /** What the AI looked at to answer, e.g. "Looked at totals for 1–31 Oct". */
    data class Seen(override val key: Long, val text: String) : ChatItem

    /** Small grey line, e.g. what the AI looked at; [error] lines are red. */
    data class Note(override val key: Long, val text: String, val error: Boolean = false) : ChatItem
}

enum class ChangeState { PENDING, APPLIED, CANCELLED, FAILED }

/**
 * Runs the conversation: sends the user's message, answers the AI's lookups, and holds every
 * change it proposes until the user taps Apply or Cancel. Nothing is stored after the screen closes.
 */
class AssistantViewModel(private val container: AppContainer, private val assistant: AssistantSettings) : ViewModel() {
    val config = assistant.config
    val privacy = assistant.privacy
    val items = mutableStateListOf<ChatItem>()
    var busy by mutableStateOf(false)
        private set
    /** Changes waiting for Apply/Cancel; the chat input is locked until they're decided. */
    var waiting by mutableIntStateOf(0)
        private set

    private var nextKey = 0L
    private val decisions = kotlinx.coroutines.sync.Mutex()
    private var session: ChatSession? = null
    private var tools: AssistantTools? = null
    private val results = mutableListOf<ToolOutcome>()
    private val pending = mutableMapOf<Long, Pair<ToolCall, ProposedChange>>()
    private var rounds = 0
    private var changesThisMessage = 0

    init {
        // A new provider, key or privacy setting starts a fresh conversation.
        viewModelScope.launch {
            combine(config, privacy) { c, p -> c to p }.drop(1).collect {
                reset(if (items.any { it is ChatItem.User }) "Settings changed, so this is a new conversation." else null)
            }
        }
    }

    /**
     * Sends a message, optionally with a photo of a bill. The photo is resized and re-encoded on the
     * phone first, and [cleanup] runs once it's been read (e.g. to delete a camera capture).
     */
    fun send(text: String, photo: android.net.Uri? = null, context: android.content.Context? = null, cleanup: () -> Unit = {}) {
        val message = text.trim().ifEmpty { if (photo != null) "Here's a bill. Add it as entries, grouped by category." else "" }
        if (message.isEmpty() || busy || waiting > 0 || config.value == null) return
        rounds = 0
        changesThisMessage = 0
        if (photo == null || context == null) {
            items += ChatItem.User(nextKey++, message)
            runTurn { ensureSession().sendUser(message) }
            return
        }
        busy = true
        viewModelScope.launch {
            val image = runCatching { com.leftovers.app.ai.ImagePrep.prepare(context, photo) }
            cleanup()
            image.onSuccess { img ->
                val bytes = android.util.Base64.decode(img.base64, android.util.Base64.NO_WRAP)
                val preview = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 })
                items += ChatItem.User(nextKey++, message, preview)
                runTurn { ensureSession().sendUser(message, img) }
            }.onFailure {
                busy = false
                items += ChatItem.Note(nextKey++, it.message ?: "Couldn't read that image", error = true)
            }
        }
    }

    fun decide(key: Long, apply: Boolean) {
        viewModelScope.launch {
            // One decision at a time, so "Apply all" sends the results back exactly once.
            decisions.withLock { decideLocked(key, apply) }
        }
    }

    private suspend fun decideLocked(key: Long, apply: Boolean) {
        val (call, change) = pending.remove(key) ?: return
        val outcome = if (apply) {
            runCatching { change.apply() }.fold(
                { mark(key, ChangeState.APPLIED); ToolOutcome(call, "Applied: $it") },
                { mark(key, ChangeState.FAILED); ToolOutcome(call, "Failed: ${it.message}", isError = true) },
            )
        } else {
            mark(key, ChangeState.CANCELLED)
            ToolOutcome(call, "The user cancelled this change. Don't retry it.")
        }
        results += outcome
        waiting = pending.size
        if (pending.isEmpty()) runTurn { session!!.sendToolResults(takeResults()) }
    }

    fun decideAll(apply: Boolean) = pending.keys.toList().forEach { decide(it, apply) }

    fun reset(note: String? = null) {
        dropSession()
        items.clear()
        note?.let { items += ChatItem.Note(nextKey++, it) }
    }

    private fun dropSession() {
        session = null
        tools = null
        results.clear()
        pending.clear()
        waiting = 0
    }

    private suspend fun ensureSession(): ChatSession {
        session?.let { return it }
        val cfg: AssistantConfig = config.value ?: throw AiException("Connect an AI first")
        val t = AssistantTools(container, privacy.value)
        val currency = container.settings.settings.first().currencyCode
        tools = t
        return chatSession(cfg, t.systemPrompt(currency), t.specs).also { session = it }
    }

    /** Runs one exchange with the model; named so it can never resolve to Kotlin's scope function `run`. */
    private fun runTurn(step: suspend () -> ModelTurn) {
        busy = true
        viewModelScope.launch {
            try {
                var turn = step()
                while (!handle(turn)) turn = session!!.sendToolResults(takeResults())
            } catch (e: AiException) {
                items += ChatItem.Note(nextKey++, e.message ?: "Something went wrong", error = true)
                // A failed request can leave the provider's history half-finished; start clean next time.
                dropSession()
            } catch (e: Exception) {
                dropSession()
                items += ChatItem.Note(nextKey++, "Something went wrong: ${e.message}", error = true)
            } finally {
                busy = false
            }
        }
    }

    /** Shows the reply and answers its lookups. Returns true when the turn is over or waiting on the user. */
    private suspend fun handle(turn: ModelTurn): Boolean {
        if (turn.text.isNotBlank()) {
            items += ChatItem.Bot(nextKey++, turn.text)
        } else if (turn.calls.isEmpty() && items.lastOrNull() is ChatItem.Change) {
            // Some models end silently after a change was applied; close the loop for the user.
            items += ChatItem.Bot(nextKey++, "Done.")
        }
        if (turn.truncated) items += ChatItem.Note(nextKey++, "The reply was cut short.")
        if (turn.calls.isEmpty()) return true

        rounds++
        if (rounds > HARD_ROUNDS) {
            // The model keeps calling tools; start over rather than loop.
            reset("That took too many steps, so the conversation was reset. Try a simpler request.")
            return true
        }
        val shared = mutableListOf<String>()
        val t = tools!!
        for (call in turn.calls) {
            if (rounds > SOFT_ROUNDS) {
                results += ToolOutcome(call, "Step limit reached. Don't call more tools; answer with what you have.", isError = true)
                continue
            }
            when (val p = t.prepare(call)) {
                is Prepared.Answer -> {
                    results += ToolOutcome(call, p.content, p.isError)
                    p.shared?.let(shared::add)
                }
                is Prepared.Change -> {
                    if (changesThisMessage >= MAX_CHANGES) {
                        results += ToolOutcome(call, "Too many changes in one message. Ask the user to continue in a new message.", isError = true)
                    } else {
                        changesThisMessage++
                        val key = nextKey++
                        items += ChatItem.Change(key, p.change.kind, p.change.summary, ChangeState.PENDING)
                        pending[key] = call to p.change
                    }
                }
            }
        }
        if (shared.isNotEmpty()) items += ChatItem.Seen(nextKey++, "Looked at ${shared.joinToString(", ")}")
        waiting = pending.size
        return pending.isNotEmpty()
    }

    private fun takeResults(): List<ToolOutcome> = results.toList().also { results.clear() }

    private fun mark(key: Long, state: ChangeState) {
        val i = items.indexOfFirst { it.key == key }
        if (i >= 0) items[i] = (items[i] as ChatItem.Change).copy(state = state)
    }

    private companion object {
        const val SOFT_ROUNDS = 8
        const val HARD_ROUNDS = 10
        const val MAX_CHANGES = 15
    }
}

private val suggestions = listOf(
    "How much did I spend on food this month?",
    "Am I on track with my budget?",
    "Add 250 for coffee today",
    "What are my biggest expenses this month?",
    "Which subscriptions renew this week?",
)

@Composable
fun AssistantScreen(onBack: () -> Unit, viewModel: AssistantViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val c = LocalAppColors.current
    val config by viewModel.config.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var showSetup by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var attached by rememberSaveable { mutableStateOf<android.net.Uri?>(null) }
    var cameraFile by remember { mutableStateOf<java.io.File?>(null) }
    var photoMenu by remember { mutableStateOf(false) }
    val pickImage = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) attached = uri
    }
    val takePhoto = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { ok ->
        val file = cameraFile
        if (ok && file != null) {
            attached = android.net.Uri.fromFile(file)
        } else {
            file?.delete()
            cameraFile = null
        }
    }
    LaunchedEffect(viewModel.items.size, viewModel.busy) {
        if (viewModel.items.isNotEmpty()) listState.animateScrollToItem(viewModel.items.size)
    }
    val cfg = config

    GlassScreen(
        title = "Assistant",
        subtitle = cfg?.let { "${it.provider.label} · ${it.model}" } ?: "Not connected",
        onBack = onBack,
        actions = {
            if (cfg != null && viewModel.items.isNotEmpty()) RoundButton(Lucide.Plus, "New conversation", { viewModel.reset() })
            RoundButton(Lucide.Settings, "AI settings", { showSetup = true })
        },
        bottomBar = {
            if (cfg != null) {
                val locked = viewModel.busy || viewModel.waiting > 0
                Column(Modifier.frosted().navigationBarsPadding().imePadding().padding(12.dp)) {
                attached?.let { uri ->
                    Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        val thumb by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, uri) {
                            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                runCatching {
                                    context.contentResolver.openInputStream(uri)?.use {
                                        android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 })
                                    }
                                }.getOrNull()
                            }
                        }
                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(c.glassStrong)) {
                            thumb?.let {
                                androidx.compose.foundation.Image(it.asImageBitmap(), contentDescription = "Photo to send", contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("Photo attached. Only this image is sent, without location data.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f))
                        RoundButton(Lucide.X, "Remove photo", {
                            cameraFile?.delete()
                            cameraFile = null
                            attached = null
                        }, size = 36.dp, tint = c.textSecondary)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RoundButton(Lucide.Camera, "Attach a bill or receipt", { photoMenu = true }, size = 52.dp, tint = c.textSecondary, enabled = !locked)
                    Spacer(Modifier.width(8.dp))
                    GlassTextField(
                        input,
                        { input = it.take(1000) },
                        placeholder = if (viewModel.waiting > 0) "Confirm or skip the change above" else "Message",
                        modifier = Modifier.weight(1f),
                        singleLine = false,
                    )
                    Spacer(Modifier.width(8.dp))
                    RoundButton(
                        Lucide.ArrowUp, "Send",
                        {
                            val file = cameraFile
                            viewModel.send(input, attached, context.applicationContext) { file?.delete() }
                            input = ""
                            attached = null
                            cameraFile = null
                        },
                        size = 52.dp,
                        tint = c.onAccent,
                        container = c.accent,
                        enabled = !locked && (input.isNotBlank() || attached != null),
                    )
                }
                }
            }
        },
    ) { padding ->
        if (cfg == null) {
            ConnectPrompt(Modifier.padding(top = padding.calculateTopPadding())) { showSetup = true }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (viewModel.items.isEmpty()) item { Suggestions { viewModel.send(it) } }
                items(viewModel.items, key = { it.key }) { item ->
                    when (item) {
                        is ChatItem.User -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                            item.image?.let { bmp ->
                                androidx.compose.foundation.Image(
                                    bmp.asImageBitmap(),
                                    contentDescription = "Attached photo",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.padding(bottom = 6.dp).size(width = 160.dp, height = 200.dp).clip(RoundedCornerShape(16.dp)),
                                )
                            }
                            Bubble(item.text, mine = true)
                        }
                        is ChatItem.Bot -> Bubble(item.text, mine = false)
                        is ChatItem.Seen -> SeenLine(item.text)
                        is ChatItem.Note -> Text(
                            item.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.error) c.negative else c.textTertiary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                        )
                        // "Confirm all" sits once, under the last card still waiting.
                        is ChatItem.Change -> ChangeCard(
                            item,
                            waiting = if (item.key == viewModel.items.lastOrNull { it is ChatItem.Change && it.state == ChangeState.PENDING }?.key) viewModel.waiting else 0,
                            onDecide = { apply -> viewModel.decide(item.key, apply) },
                            onDecideAll = viewModel::decideAll,
                        )
                    }
                }
                if (viewModel.busy) item { TypingBubble() }
            }
        }
    }

    if (showSetup) AssistantSetupSheet(onDismiss = { showSetup = false })

    if (photoMenu) {
        GlassSheet(onDismiss = { photoMenu = false }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Add a bill or receipt", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
                Text(
                    "The AI reads it and suggests entries, grouped by category. Nothing is added until you confirm.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
                Glass(Modifier.fillMaxWidth(), onClick = {
                    photoMenu = false
                    val (uri, file) = com.leftovers.app.util.ReceiptStore.newCameraUri(context)
                    cameraFile = file
                    takePhoto.launch(uri)
                }) { com.leftovers.app.ui.components.ListRow("Take a photo", leading = { Icon(Lucide.Camera, null, tint = c.textPrimary) }) }
                Glass(Modifier.fillMaxWidth(), onClick = {
                    photoMenu = false
                    pickImage.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { com.leftovers.app.ui.components.ListRow("Choose a photo or screenshot", leading = { Icon(Lucide.ReceiptText, null, tint = c.textPrimary) }) }
            }
        }
    }
}

@Composable
private fun ConnectPrompt(modifier: Modifier, onConnect: () -> Unit) {
    val c = LocalAppColors.current
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        IconTile(Lucide.MessageCircle, c.accent, size = 64.dp)
        Spacer(Modifier.height(16.dp))
        Text("Ask about your money, or tell it what to log", style = MaterialTheme.typography.titleLarge, color = c.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Connect Claude, ChatGPT or Gemini with your own API key. It only sees what each question needs, and nothing changes until you tap Apply.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Connect an AI", onConnect, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Suggestions(onPick: (String) -> Unit) {
    val c = LocalAppColors.current
    Column(Modifier.padding(top = 4.dp)) {
        Text("Ask about your money, or tell it what to log.", style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.padding(start = 6.dp))
        Text(
            "Changes always wait for you to confirm.",
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
            modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 12.dp),
        )
        Glass(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 4.dp)) {
                suggestions.forEachIndexed { i, text ->
                    if (i > 0) com.leftovers.app.ui.components.RowDivider(inset = 16.dp)
                    Row(
                        Modifier.fillMaxWidth().pressable({ onPick(text) }, pressedScale = 0.98f).padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
                        Icon(Lucide.ChevronRight, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Bubble(text: String, mine: Boolean) {
    val c = LocalAppColors.current
    // Small "tail" corner on the speaker's side, like a messaging app.
    val shape = if (mine) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .background(if (mine) c.accent else c.glassStrong, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                if (mine) AnnotatedString(text) else simpleMarkdown(text),
                style = MaterialTheme.typography.bodyMedium,
                color = if (mine) c.onAccent else c.textPrimary,
            )
        }
    }
}

@Composable
private fun SeenLine(text: String) {
    val c = LocalAppColors.current
    Row(Modifier.padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Lucide.Search, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
    }
}

/** Three softly pulsing dots while waiting for a reply. */
@Composable
private fun TypingBubble() {
    val c = LocalAppColors.current
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "typing")
    Box(
        Modifier
            .background(c.glassStrong, RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
            .semantics { contentDescription = "Thinking" }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(3) { i ->
                val alpha by pulse.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                        androidx.compose.animation.core.tween(500, delayMillis = i * 160),
                        androidx.compose.animation.core.RepeatMode.Reverse,
                    ),
                    label = "dot$i",
                )
                Box(Modifier.size(7.dp).graphicsLayer { this.alpha = alpha }.background(c.textSecondary, CircleShape))
            }
        }
    }
}

/** Renders the bits of Markdown chat models like to use: **bold**, *italic*, `code` and "* " / "- " bullets. */
private fun simpleMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    text.lines().forEachIndexed { i, raw ->
        if (i > 0) append('\n')
        val bullet = Regex("""^\s*[*-]\s+""").find(raw)
        val line = if (bullet != null) {
            append("•  ")
            raw.substring(bullet.range.last + 1)
        } else {
            raw.trimStart('#', ' ').let { if (raw.startsWith("#")) it else raw }
        }
        var rest = line
        val token = Regex("""\*\*(.+?)\*\*|`(.+?)`|(?<![*\w])\*(?!\s)(.+?)(?<!\s)\*(?![*\w])""")
        while (true) {
            val m = token.find(rest) ?: break
            append(rest.substring(0, m.range.first))
            when {
                m.groups[1] != null -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(m.groupValues[1]) }
                m.groups[2] != null -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(m.groupValues[2]) }
                else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(m.groupValues[3]) }
            }
            rest = rest.substring(m.range.last + 1)
        }
        append(rest)
    }
}

@Composable
private fun ChangeCard(item: ChatItem.Change, waiting: Int, onDecide: (Boolean) -> Unit, onDecideAll: (Boolean) -> Unit) {
    val c = LocalAppColors.current
    val icon = when (item.kind) {
        ChangeKind.ADD -> Lucide.Plus
        ChangeKind.EDIT -> Lucide.Pencil
        ChangeKind.DELETE -> Lucide.Trash2
        ChangeKind.BUDGET, ChangeKind.LIMIT -> Lucide.Target
        ChangeKind.SUBSCRIPTION -> Lucide.Repeat
        ChangeKind.TRANSFER -> Lucide.Wallet
        ChangeKind.GOAL -> Lucide.PiggyBank
    }
    val tint = if (item.kind == ChangeKind.DELETE) c.negative else c.accent
    val pending = item.state == ChangeState.PENDING
    Glass(Modifier.fillMaxWidth().alpha(if (pending) 1f else 0.75f), strong = pending, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(icon, tint, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.kind.title, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                    Text(item.summary, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                }
            }
            when (item.state) {
                ChangeState.PENDING -> {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        DecisionButton("Not now", primary = false, Modifier.weight(1f)) { onDecide(false) }
                        DecisionButton(if (item.kind == ChangeKind.DELETE) "Delete" else "Confirm", primary = true, Modifier.weight(1f), danger = item.kind == ChangeKind.DELETE) { onDecide(true) }
                    }
                    if (waiting > 1) {
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Skip all $waiting", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.pressable({ onDecideAll(false) }, pressedScale = 0.95f).padding(6.dp))
                            Text("Confirm all $waiting", style = MaterialTheme.typography.labelMedium, color = c.accent, modifier = Modifier.pressable({ onDecideAll(true) }, pressedScale = 0.95f).padding(6.dp))
                        }
                    }
                }
                ChangeState.APPLIED -> Status(Lucide.CircleCheck, item.kind.done, c.positive)
                ChangeState.CANCELLED -> Status(Lucide.X, "Skipped", c.textTertiary)
                ChangeState.FAILED -> Status(Lucide.Info, "Couldn't make this change", c.negative)
            }
        }
    }
}

@Composable
private fun DecisionButton(text: String, primary: Boolean, modifier: Modifier, danger: Boolean = false, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val bg = when {
        !primary -> c.glass
        danger -> c.negative
        else -> c.accent
    }
    Box(
        modifier.height(42.dp).pressable(onClick, pressedScale = 0.96f).background(bg, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (primary) c.onAccent else c.textPrimary)
    }
}

@Composable
private fun Status(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.padding(start = 48.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Connect, change or disconnect the AI, and choose what it may see and do. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantSetupSheet(onDismiss: () -> Unit, viewModel: AssistantSetupViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val c = LocalAppColors.current
    val config by viewModel.settings.config.collectAsStateWithLifecycle()
    val privacy by viewModel.settings.privacy.collectAsStateWithLifecycle()
    var provider by rememberSaveable { mutableStateOf(config?.provider ?: viewModel.settings.lastProvider) }
    var model by rememberSaveable { mutableStateOf(config?.model ?: viewModel.settings.lastModel(provider)) }
    var key by rememberSaveable { mutableStateOf("") }
    var agreed by rememberSaveable { mutableStateOf(false) }
    var customUrl by rememberSaveable { mutableStateOf(config?.takeIf { it.provider == AiProvider.CUSTOM }?.baseUrl ?: viewModel.settings.lastCustomUrl) }
    var thorough by rememberSaveable { mutableStateOf(viewModel.settings.thorough) }
    var loaded by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    GlassSheet(onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("AI assistant", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
            val current = config
            if (current != null) {
                Glass(Modifier.fillMaxWidth(), tint = c.positive.copy(alpha = 0.12f)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Connected to ${current.provider.label}", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                        Text(current.model, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        Spacer(Modifier.height(12.dp))
                        SecondaryButton("Disconnect and delete key", {
                            viewModel.settings.disconnect()
                            key = ""
                            agreed = false
                        }, Modifier.fillMaxWidth())
                    }
                }
            }

            Text(if (current == null) "Connect" else "Switch AI", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(start = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AiProvider.entries.forEach { p ->
                    Chip(p.label, {
                        if (p != provider) {
                            provider = p
                            model = if (current?.provider == p) current.model else p.defaultModel
                            loaded = emptyList()
                            loadError = null
                        }
                    }, selected = provider == p)
                }
            }
            provider.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.warning, modifier = Modifier.padding(start = 6.dp)) }

            if (provider.baseUrl == null) {
                GlassTextField(
                    customUrl, { customUrl = it.trim().take(200) },
                    placeholder = "https://my-server.example/v1",
                    label = "Server address (OpenAI-compatible)",
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
                )
            }

            val savedKey = current?.takeIf { it.provider == provider }?.apiKey
            GlassTextField(
                key, { key = it.trim().take(300) },
                placeholder = if (savedKey != null) "Saved · leave empty to keep it" else provider.keyHint,
                label = "${provider.label} API key",
                password = true,
            )
            provider.keyUrl?.let { url ->
                Text(
                    "Get a ${provider.label} key ↗",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.accent,
                    modifier = Modifier.padding(start = 6.dp).pressable({ uriHandler.openUri(url) }, pressedScale = 0.97f),
                )
            }

            Text("Model", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(start = 6.dp))
            val modelChoices = (provider.suggestedModels + loaded).distinct()
            if (modelChoices.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    modelChoices.take(40).forEach { m -> Chip(m, { model = m }, selected = model == m) }
                }
            }
            GlassTextField(model, { model = it.trim().take(100) }, placeholder = provider.defaultModel.ifEmpty { "model name" }, keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii)
            val keyForLoad = key.ifEmpty { savedKey.orEmpty() }
            Text(
                when {
                    loading -> "Loading models…"
                    loadError != null -> loadError!!
                    loaded.isNotEmpty() -> "${loaded.size} models available to your key"
                    else -> "Load models your key can use"
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (loadError != null) c.negative else c.accent,
                modifier = Modifier.padding(start = 6.dp).pressable({
                    if (!loading) {
                        val base = provider.baseUrl ?: runCatching { requireHttps(customUrl) }.getOrNull()
                        when {
                            base == null -> loadError = "The server address must start with https://"
                            keyForLoad.isEmpty() && provider != AiProvider.CUSTOM -> loadError = "Enter your API key first"
                            else -> {
                                loading = true
                                loadError = null
                                scope.launch {
                                    runCatching { listModels(provider, base, keyForLoad) }
                                        .onSuccess {
                                            loaded = it
                                            if (it.isEmpty()) loadError = "No chat models found for this key"
                                        }
                                        .onFailure { loadError = it.message ?: "Couldn't load models" }
                                    loading = false
                                }
                            }
                        }
                    }
                }, pressedScale = 0.97f),
            )

            Text("Answer style", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(start = 6.dp))
            SegmentedToggle(listOf(false, true), thorough, { if (it) "Thorough" else "Quick" }, {
                thorough = it
                if (current != null) viewModel.settings.setThorough(it)
            }, Modifier.fillMaxWidth())
            Text(
                if (thorough) "The AI thinks longer before answering. Slower, and uses more of your credit." else "Fast replies for everyday questions and logging.",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                modifier = Modifier.padding(start = 6.dp),
            )

            Text(
                "Your key is encrypted on this phone and is only ever sent to ${provider.label}. " +
                    "When you ask something, your question and only the data needed to answer it are sent to ${provider.label} and handled under its privacy policy. " +
                    "Card numbers, UPI IDs, phone numbers and emails are hidden first. Saved receipt photos and SMS are never sent; a photo goes only when you attach it to a message. Chats aren't saved on the phone.",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
            Chip(
                "I understand",
                { agreed = !agreed },
                icon = if (agreed) Lucide.CircleCheck else Lucide.Plus,
                selected = agreed,
            )
            // Changing only the model or style of the connected provider keeps the saved key.
            val urlOk = provider.baseUrl != null || runCatching { requireHttps(customUrl) }.isSuccess
            val sameServer = provider.baseUrl != null || runCatching { requireHttps(customUrl) }.getOrNull() == current?.baseUrl
            val keepKey = savedKey != null && key.isEmpty() && sameServer
            // Custom servers may not need a key at all.
            val keyOk = keepKey || key.length >= 10 || provider == AiProvider.CUSTOM
            PrimaryButton(
                when {
                    current == null -> "Connect"
                    keepKey -> "Save"
                    else -> "Save and reconnect"
                },
                {
                    viewModel.settings.setThorough(thorough)
                    viewModel.settings.connect(provider, model, if (keepKey) savedKey!! else key, customUrl)
                    onDismiss()
                },
                Modifier.fillMaxWidth(),
                enabled = model.isNotBlank() && urlOk && keyOk && (keepKey || agreed),
            )
            if (provider.baseUrl == null && customUrl.isNotBlank() && !urlOk) {
                Text("The address must start with https://", style = MaterialTheme.typography.bodySmall, color = c.negative, modifier = Modifier.padding(start = 6.dp))
            }

            Text("Privacy", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
            Glass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    PrivacyToggle(
                        "Share entry notes",
                        "Off: the AI sees amounts, dates and categories, but not what you wrote in notes.",
                        privacy.shareNotes,
                    ) { viewModel.settings.setPrivacy(privacy.copy(shareNotes = it)) }
                    PrivacyToggle(
                        "Let it propose changes",
                        "Off: read-only. On: every add, edit or delete still waits for your Apply.",
                        privacy.allowChanges,
                    ) { viewModel.settings.setPrivacy(privacy.copy(allowChanges = it)) }
                }
            }
        }
    }
}

@Composable
private fun PrivacyToggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.accent,
                checkedThumbColor = c.onAccent,
                uncheckedTrackColor = c.glass,
                uncheckedBorderColor = c.borderTop,
                uncheckedThumbColor = c.textSecondary,
            ),
        )
    }
}

class AssistantSetupViewModel(val settings: AssistantSettings) : ViewModel()
