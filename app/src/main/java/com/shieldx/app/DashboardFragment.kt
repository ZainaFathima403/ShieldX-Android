package com.shieldx.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.setDefaultButton).setOnClickListener {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS).apply {
                    data = Uri.parse("package:${requireContext().packageName}")
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${requireContext().packageName}"))
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${requireContext().packageName}")))
            }
        }

        view.findViewById<Button>(R.id.openScreenshotScanner).setOnClickListener {
            (requireActivity() as MainActivity).showScreenshotScanner()
        }

        view.findViewById<TextView>(R.id.clearAllActivity).setOnClickListener {
            StatsStore.clearActivity(requireContext())
            refresh()
        }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh() // pick up any scans done in other tabs since we were last shown
    }

    fun refresh() {
        val view = view ?: return
        val context = requireContext()
        val stats = StatsStore.getStats(context)
        val activity = StatsStore.getActivity(context)

        view.findViewById<TextView>(R.id.statThreats).text = stats.threats.toString()
        view.findViewById<TextView>(R.id.statScans).text = stats.scans.toString()
        view.findViewById<TextView>(R.id.statScore).text = stats.score.toString()
        view.findViewById<TextView>(R.id.statWarn).text = stats.warn.toString()

        val statusLabel = view.findViewById<TextView>(R.id.statusLabel)
        statusLabel.text = when {
            stats.score >= 80 -> "🟢 Protected"
            stats.score >= 50 -> "🟡 At Risk"
            else -> "🔴 High Risk"
        }

        val clearAll = view.findViewById<TextView>(R.id.clearAllActivity)
        clearAll.visibility = if (activity.isEmpty()) View.GONE else View.VISIBLE

        val list = view.findViewById<LinearLayout>(R.id.activityList)
        list.removeAllViews()
        if (activity.isEmpty()) {
            val empty = TextView(context).apply {
                text = "No scans yet — try the URL Scanner, QR Scanner, or Message Analyzer."
                setTextColor(context.getColor(R.color.text_dim))
                textSize = 12f
            }
            list.addView(empty)
        } else {
            activity.forEach { entry ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 10, 0, 10)
                }
                val label = TextView(context).apply {
                    text = "${entry.level.uppercase()}  ${entry.label}"
                    setTextColor(context.getColor(R.color.text))
                    textSize = 12f
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                val time = TextView(context).apply {
                    text = entry.time
                    setTextColor(context.getColor(R.color.text_dim))
                    textSize = 11f
                    setPadding(8, 0, 8, 0)
                }
                val delete = TextView(context).apply {
                    text = "✕"
                    setTextColor(context.getColor(R.color.text_dim))
                    textSize = 14f
                    setPadding(12, 0, 0, 0)
                    setOnClickListener {
                        StatsStore.removeActivityEntry(context, entry.id)
                        refresh()
                    }
                }
                row.addView(label)
                row.addView(time)
                row.addView(delete)
                list.addView(row)
            }
        }
    }
}
