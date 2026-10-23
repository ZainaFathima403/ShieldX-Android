package com.shieldx.app

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class UrlScannerFragment : Fragment(R.layout.fragment_url_scanner) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val input = view.findViewById<EditText>(R.id.urlInput)
        val button = view.findViewById<Button>(R.id.scanButton)
        val status = view.findViewById<TextView>(R.id.statusText)
        val resultContainer = view.findViewById<LinearLayout>(R.id.resultContainer)

        button.setOnClickListener {
            val value = input.text.toString().trim()
            if (value.isEmpty()) return@setOnClickListener

            button.isEnabled = false
            status.text = if (ApiKeys.hasSafeBrowsingKey()) "Checking heuristics + Google Safe Browsing…" else "Checking heuristics…"
            ResultView.clear(resultContainer)

            viewLifecycleOwner.lifecycleScope.launch {
                val result = ScanEngine.analyzeUrlWithSafeBrowsing(value)
                if (!isAdded) return@launch
                status.text = ""
                ResultView.render(resultContainer, result, value)
                StatsStore.recordScan(requireContext(), result.level, "URL scan: ${(result.host.ifBlank { value }).take(40)}")
                button.isEnabled = true
            }
        }
    }
}
