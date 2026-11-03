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
    /** Gradient header card with a small overline, a large title and a subtitle */
    fun hero(context: Context, parent: LinearLayout, overline: String, title: String, subtitle: String, titleSize: Float = 34f): LinearLayout {
        val hero = column(context, 22).apply {
            setBackgroundResource(R.drawable.hero_card)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 16) }
        }
        fun line(value: String, size: Float, color: Int, medium: Boolean) = TextView(context).apply {
            text = value; textSize = size; setTextColor(ContextCompat.getColor(context, color))
            if (medium) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        hero.addView(line(overline.uppercase(Locale.getDefault()), 11f, R.color.onHeroMuted, true).apply { letterSpacing = .1f })
        hero.addView(line(title, titleSize, R.color.onHero, true).apply { setPadding(0, dp(context, 6), 0, dp(context, 2)) })
        hero.addView(line(subtitle, 14f, R.color.onHeroMuted, false))
        parent.addView(hero)
        return hero
    }
    /** A rounded status pill, e.g. "Recording" */
    fun pill(context: Context, value: String, onHero: Boolean = false) = TextView(context).apply {
        text = value; textSize = 12f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(ContextCompat.getColor(context, if (onHero) R.color.onHero else R.color.colorAccent))
        setBackgroundResource(if (onHero) R.drawable.pill_hero else R.drawable.pill_soft)
        setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6))
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(context, 14) }
    }
    /** A horizontal row of equally wide value/label tiles */
    fun stats(context: Context, parent: LinearLayout, items: List<Pair<String, String>>, onHero: Boolean = false) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 14) }
        }
        items.forEachIndexed { index, (label, value) ->
            val tile = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(if (onHero) R.drawable.pill_hero else R.drawable.pill_soft)
                setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10))
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { if (index > 0) marginStart = dp(context, 8) }
            }
            tile.addView(TextView(context).apply {
                text = value; textSize = 17f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(ContextCompat.getColor(context, if (onHero) R.color.onHero else R.color.telemetryText))
            })
            tile.addView(TextView(context).apply {
                text = label; textSize = 11f
                setTextColor(ContextCompat.getColor(context, if (onHero) R.color.onHeroMuted else R.color.telemetryMuted))
            })
            row.addView(tile)
        }
        parent.addView(row)
    }
    fun duration(ms: Long): String {
        val seconds = maxOf(0L, ms / 1000)
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    }
}
