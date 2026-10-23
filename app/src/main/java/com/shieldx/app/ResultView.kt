package com.shieldx.app

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

object ResultView {

    private fun colorFor(context: Context, level: RiskLevel): Int = when (level) {
        RiskLevel.DANGER -> context.getColor(R.color.danger)
        RiskLevel.WARN -> context.getColor(R.color.warn)
        else -> context.getColor(R.color.safe)
    }

    private fun labelFor(level: RiskLevel): String = when (level) {
        RiskLevel.DANGER -> "⛔ Dangerous"
        RiskLevel.WARN -> "⚠️ Suspicious"
        else -> "✅ Safe"
    }

    /** Clears `container` and fills it with a styled result card for the given scan result. */
    fun render(container: LinearLayout, result: ScanResult, subject: String? = null) {
        val context = container.context
        container.removeAllViews()
        container.visibility = View.VISIBLE

        val color = colorFor(context, result.level)
        val pad = (16 * context.resources.displayMetrics.density).toInt()

        val bg = GradientDrawable().apply {
            setColor(context.getColor(R.color.card))
            cornerRadius = 14f * context.resources.displayMetrics.density
            setStroke(2, color)
        }
        container.background = bg
        container.setPadding(pad, pad, pad, pad)
        container.orientation = LinearLayout.VERTICAL

        val badge = TextView(context).apply {
            text = "${labelFor(result.level)}  ·  ${result.score}/100"
            setTextColor(color)
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        container.addView(badge)

        if (!subject.isNullOrBlank()) {
            val subjectView = TextView(context).apply {
                text = subject
                setTextColor(context.getColor(R.color.text))
                textSize = 12f
                setPadding(0, pad / 2, 0, 0)
            }
            container.addView(subjectView)
        }

        val reasonsView = TextView(context).apply {
            text = result.reasons.joinToString("\n\n") { "•  $it" }
            setTextColor(context.getColor(R.color.text_dim))
            textSize = 13f
            setLineSpacing(6f, 1f)
            setPadding(0, pad, 0, 0)
        }
        container.addView(reasonsView)
    }

    fun clear(container: LinearLayout) {
        container.removeAllViews()
        container.background = null
        container.setPadding(0, 0, 0, 0)
        container.visibility = View.GONE
    }
}
