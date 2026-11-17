package com.leftovers.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.leftovers.desktop.Snapshots
import com.leftovers.desktop.data.Theme
import com.leftovers.desktop.ui.screens.BackupDialogs
import com.leftovers.desktop.ui.screens.BackupOpener
import com.leftovers.desktop.ui.screens.BackupSaveDialog
import com.leftovers.desktop.ui.screens.CurrencyDialog
import com.leftovers.desktop.ui.theme.LeftoversTheme
import kotlin.test.Test

/** Every dialog opens and draws without throwing. */
class DialogsTest {
    private fun draw(content: @Composable () -> Unit) {
        val state = AppState(Snapshots.demoStore(Theme.LIGHT, false, false))
        ImageComposeScene(1240, 820, Density(1f)) {
            androidx.compose.runtime.CompositionLocalProvider(LocalApp provides state) { LeftoversTheme(Theme.LIGHT, false) { content() } }
        }.use { scene -> var t = 0L; repeat(40) { scene.render(t); t += 33_000_000L } }
    }

    @Test fun currency() = draw { CurrencyDialog(true, {}) {} }
    @Test fun saveBackup() = draw { BackupSaveDialog(true) {} }
    @Test fun password() = draw { BackupDialogs(BackupOpener().apply { asking = true }, askHow = true) { _, _ -> } }
    @Test fun replaceOrAdd() = draw { BackupDialogs(BackupOpener().apply { opened = org.json.JSONObject("""{"app":"leftovers","transactions":[]}""") }, askHow = true) { _, _ -> } }
}
