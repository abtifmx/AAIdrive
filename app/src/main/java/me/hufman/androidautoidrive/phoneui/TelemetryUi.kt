package me.hufman.androidautoidrive.phoneui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import me.hufman.androidautoidrive.R
import java.util.Locale

object TelemetryUi {
    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun column(context: Context, padding: Int = 20) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val px = dp(context, padding); setPadding(px, px, px, px)
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }
    fun text(context: Context, value: String, size: Float = 15f, bold: Boolean = false) = TextView(context).apply {
        text = value; textSize = size
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(ContextCompat.getColor(context, if (bold) R.color.telemetryText else R.color.telemetryMuted))
        setPadding(0, dp(context, 5), 0, dp(context, 7))
        setTextIsSelectable(false)
    }
    fun card(context: Context, parent: LinearLayout, title: String? = null): LinearLayout {
        val card = MaterialCardView(context).apply {
            radius = dp(context, 22).toFloat(); cardElevation = 0f
            strokeWidth = dp(context, 1); strokeColor = ContextCompat.getColor(context, R.color.telemetryBorder)
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.telemetrySurface))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 14) }
        }
        val inner = column(context, 18)
        if (title != null) inner.addView(text(context, title, 18f, true))
        card.addView(inner); parent.addView(card)
        return inner
    }
    fun button(context: Context, label: String, action: () -> Unit) = MaterialButton(context, null,
        com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
        text = label; isAllCaps = false; cornerRadius = dp(context, 16)
        layoutParams = LinearLayout.LayoutParams(-1, -2)
        setOnClickListener { action() }
    }
    fun duration(ms: Long): String {
        val seconds = maxOf(0L, ms / 1000)
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    }
}
