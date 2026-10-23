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

class MessageAnalyzerFragment : Fragment(R.layout.fragment_message_analyzer) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val input = view.findViewById<EditText>(R.id.messageInput)
        val button = view.findViewById<Button>(R.id.analyzeButton)
        val status = view.findViewById<TextView>(R.id.statusText)
        val resultContainer = view.findViewById<LinearLayout>(R.id.resultContainer)

        button.setOnClickListener {
            val value = input.text.toString().trim()
            if (value.isEmpty()) return@setOnClickListener

            button.isEnabled = false
            status.text = "Analyzing…"
            ResultView.clear(resultContainer)

            viewLifecycleOwner.lifecycleScope.launch {
                val result = ScanEngine.analyzeMessageWithSafeBrowsing(value)
                if (!isAdded) return@launch
                status.text = ""
                ResultView.render(resultContainer, result)
                StatsStore.recordScan(requireContext(), result.level, "Message analyzed")
                button.isEnabled = true
            }
        }
    }
}
