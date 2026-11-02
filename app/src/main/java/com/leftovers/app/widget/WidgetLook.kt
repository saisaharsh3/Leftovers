package com.leftovers.app.widget

import androidx.compose.ui.graphics.Color
import com.leftovers.app.R
import com.leftovers.app.data.WidgetStyle

/** How the home-screen widgets look, as chosen in Settings: glass, dark or light. */
internal data class WidgetLook(
    /** The widget's background drawable. */
    val background: Int,
    val text: Color,
    val muted: Color,
    /** Behind a bar, and the plain buttons. */
    val track: Color,
    val button: Color,
    /** A bar's fill, and what's over budget. */
    val bar: Color,
    val negative: Color,
) {
    companion object {
        fun of(style: WidgetStyle) = when (style) {
            WidgetStyle.GLASS -> WidgetLook(R.drawable.widget_glass, Color(0xFFF5F5F7), Color(0xFFC2C2CC), Color(0x22FFFFFF), Color(0x24FFFFFF), Color(0xFFCDF46F), Color(0xFFF58A8A))
            WidgetStyle.DARK -> WidgetLook(R.drawable.widget_dark, Color(0xFFF5F5F7), Color(0xFFA1A1AA), Color(0x22FFFFFF), Color(0xFF26262F), Color(0xFFCDF46F), Color(0xFFF58A8A))
            // On white the accent is too pale for a thin bar, so bars take a deeper green.
            WidgetStyle.LIGHT -> WidgetLook(R.drawable.widget_light, Color(0xFF16161D), Color(0xFF6B6B76), Color(0x14000000), Color(0x0F000000), Color(0xFF6E9F12), Color(0xFFD94848))
        }
    }
}
