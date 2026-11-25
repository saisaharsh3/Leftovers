package com.leftovers.app.ui.components

import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.leftovers.app.ui.theme.LocalAppColors
import com.leftovers.app.util.PrivacyMode

/**
 * Privacy mode (#73) for one of the big totals: a balance, or the month's spent or income. While the eye on Home is
 * shut it's blurred past reading, like a banking app's discreet mode; entries, budgets and charts stay as they are.
 * Before Android 12, which can't blur, it's covered with a soft bar instead.
 */
fun Modifier.privateAmount(): Modifier = composed {
    val hidden = PrivacyMode.hidden
    val radius by animateDpAsState(if (hidden) 12.dp else 0.dp, tween(220), label = "privateBlur")
    val cover = LocalAppColors.current.glassStrong
    val hiding = if (hidden) Modifier.clearAndSetSemantics { contentDescription = "Amount hidden" } else Modifier
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.then(hiding).blur(radius, BlurredEdgeTreatment.Unbounded)
    } else {
        this.then(hiding).drawWithContent {
            if (hidden) drawRoundRect(cover, cornerRadius = CornerRadius(size.height / 2)) else drawContent()
        }
    }
}
