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
import com.leftovers.app.ai.ChatSession
import com.leftovers.app.ai.ModelTurn
import com.leftovers.app.ai.Prepared
import com.leftovers.app.ai.ProposedChange
import com.leftovers.app.ai.ToolCall
import com.leftovers.app.ai.ToolOutcome
import com.leftovers.app.ai.chatSession
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

sealed interface ChatItem {
    val key: Long

    data class User(override val key: Long, val text: String) : ChatItem
    data class Bot(override val key: Long, val text: String) : ChatItem
    data class Change(override val key: Long, val summary: String, val state: ChangeState) : ChatItem

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

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || busy || waiting > 0 || config.value == null) return
        items += ChatItem.User(nextKey++, message)
        rounds = 0
        changesThisMessage = 0
        run { ensureSession().sendUser(message) }
    }

    fun decide(key: Long, apply: Boolean) {
        val (call, change) = pending.remove(key) ?: return
        viewModelScope.launch {
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
            if (pending.isEmpty()) run { session!!.sendToolResults(takeResults()) }
        }
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

    private fun run(step: suspend () -> ModelTurn) {
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
                        items += ChatItem.Change(key, p.change.summary, ChangeState.PENDING)
                        pending[key] = call to p.change
                    }
                }
            }
        }
        if (shared.isNotEmpty()) items += ChatItem.Note(nextKey++, "Shared with ${config.value?.provider?.label ?: "the AI"}: ${shared.joinToString(" · ")}")
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
                Row(
                    Modifier.frosted().navigationBarsPadding().imePadding().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassTextField(
                        input,
                        { input = it.take(1000) },
                        placeholder = if (viewModel.waiting > 0) "Apply or cancel the changes above" else "Ask or tell it what to change",
                        modifier = Modifier.weight(1f),
                        singleLine = false,
                    )
                    Spacer(Modifier.width(8.dp))
                    RoundButton(
                        Lucide.ArrowUpRight, "Send",
                        {
                            viewModel.send(input)
                            input = ""
                        },
                        size = 52.dp,
                        tint = c.onAccent,
                        container = c.accent,
                        enabled = !locked && input.isNotBlank(),
                    )
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
                        is ChatItem.User -> Bubble(item.text, mine = true)
                        is ChatItem.Bot -> Bubble(item.text, mine = false)
                        is ChatItem.Note -> Text(
                            item.text,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.error) c.negative else c.textTertiary,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        )
                        is ChatItem.Change -> ChangeCard(item, viewModel.waiting, onDecide = { apply -> viewModel.decide(item.key, apply) }, onDecideAll = viewModel::decideAll)
                    }
                }
                if (viewModel.busy) item { Text("Thinking…", style = MaterialTheme.typography.bodySmall, color = c.textTertiary, modifier = Modifier.padding(horizontal = 8.dp)) }
            }
        }
    }

    if (showSetup) AssistantSetupSheet(onDismiss = { showSetup = false })
}

@Composable
private fun ConnectPrompt(modifier: Modifier, onConnect: () -> Unit) {
    val c = LocalAppColors.current
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        IconTile(Lucide.Sparkles, c.accent, size = 64.dp)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Suggestions(onPick: (String) -> Unit) {
    val c = LocalAppColors.current
    Column(Modifier.padding(top = 8.dp)) {
        Text("Try asking", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { Chip(it, { onPick(it) }) }
        }
    }
}

@Composable
private fun Bubble(text: String, mine: Boolean) {
    val c = LocalAppColors.current
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(
            Modifier
                .widthIn(max = 320.dp)
                .background(if (mine) c.accent.copy(alpha = 0.18f) else c.glassStrong, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(if (mine) AnnotatedString(text) else simpleMarkdown(text), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
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
    Glass(Modifier.fillMaxWidth(), strong = item.state == ChangeState.PENDING) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.Pencil, contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Proposed change", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
            }
            Spacer(Modifier.height(6.dp))
            Text(item.summary, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
            Spacer(Modifier.height(12.dp))
            when (item.state) {
                ChangeState.PENDING -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("Cancel", { onDecide(false) }, Modifier.weight(1f))
                        PrimaryButton("Apply", { onDecide(true) }, Modifier.weight(1f))
                    }
                    if (waiting > 1) {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cancel all $waiting", style = MaterialTheme.typography.labelLarge, color = c.textSecondary, modifier = Modifier.pressable({ onDecideAll(false) }, pressedScale = 0.95f).padding(6.dp))
                            Text("Apply all $waiting", style = MaterialTheme.typography.labelLarge, color = c.accent, modifier = Modifier.pressable({ onDecideAll(true) }, pressedScale = 0.95f).padding(6.dp))
                        }
                    }
                }
                ChangeState.APPLIED -> Text("✓ Applied", style = MaterialTheme.typography.labelLarge, color = c.positive)
                ChangeState.CANCELLED -> Text("Cancelled", style = MaterialTheme.typography.labelLarge, color = c.textTertiary)
                ChangeState.FAILED -> Text("Couldn't apply this change", style = MaterialTheme.typography.labelLarge, color = c.negative)
            }
        }
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
            SegmentedToggle(AiProvider.entries, provider, { it.label }, {
                provider = it
                model = it.defaultModel
            }, Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                provider.suggestedModels.forEach { m -> Chip(m, { model = m }, selected = model == m) }
            }
            GlassTextField(model, { model = it.trim().take(60) }, placeholder = provider.defaultModel, label = "Model", keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii)
            GlassTextField(
                key, { key = it.trim().take(300) },
                placeholder = if (current?.provider == provider) "Saved · leave empty to keep it" else provider.keyHint,
                label = "${provider.label} API key",
                password = true,
            )
            Text(
                "Your key is encrypted on this phone and never leaves it except to talk to ${provider.label}. " +
                    "When you ask something, your question and only the data needed to answer it are sent to ${provider.label} and handled under its privacy policy. " +
                    "Card numbers, UPI IDs, phone numbers and emails are hidden first, receipt photos and SMS are never sent, and chats aren't saved on the phone.",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
            Chip(
                "I understand",
                { agreed = !agreed },
                icon = if (agreed) Lucide.CircleCheck else Lucide.Plus,
                selected = agreed,
            )
            // Changing only the model of the connected provider keeps the saved key.
            val keepKey = current != null && current.provider == provider && key.isEmpty()
            PrimaryButton(
                when {
                    current == null -> "Connect"
                    keepKey -> "Save"
                    else -> "Save and reconnect"
                },
                {
                    viewModel.settings.connect(provider, model, if (keepKey) current!!.apiKey else key)
                    onDismiss()
                },
                Modifier.fillMaxWidth(),
                enabled = model.isNotBlank() && (keepKey || (key.length >= 10 && agreed)),
            )

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
