package com.leftovers.desktop.ui.screens

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.leftovers.desktop.ui.Page
import com.leftovers.desktop.ui.components.Empty
import com.leftovers.desktop.ui.theme.T

/** A page: its title and actions at the top, then its content, scrolling, at a comfortable reading width. */
@Composable
fun Screen(
    title: String,
    subtitle: String? = null,
    maxWidth: Dp = 1080.dp,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = maxWidth).fillMaxWidth().padding(horizontal = 40.dp, vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = T.type.display, color = T.c.text)
                        if (subtitle != null) Text(subtitle, style = T.type.body, color = T.c.text2, modifier = Modifier.padding(top = 4.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
                }
                content()
            }
        }
        VerticalScrollbar(
            rememberScrollbarAdapter(scroll),
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp, horizontal = 2.dp),
            style = defaultScrollbarStyle().copy(
                unhoverColor = T.c.text.copy(alpha = 0.12f), hoverColor = T.c.text.copy(alpha = 0.28f), shape = RoundedCornerShape(4.dp),
            ),
        )
    }
}

/** A page still to come in this build. */
@Composable
fun Placeholder(page: Page) {
    Screen(page.title) {
        Empty(page.icon, "${page.title} is on its way", "This part of the desktop app is being built.")
    }
}
