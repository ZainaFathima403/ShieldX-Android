package com.shieldx.app

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class AiAssistantFragment : Fragment(R.layout.fragment_ai_assistant) {

    private val history = mutableListOf<ChatMessage>()
    private lateinit var chatContainer: LinearLayout
    private lateinit var chatScroll: ScrollView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chatContainer = view.findViewById(R.id.chatContainer)
        chatScroll = view.findViewById(R.id.chatScroll)
        val input = view.findViewById<EditText>(R.id.chatInput)
        val sendButton = view.findViewById<Button>(R.id.sendButton)
        val keyNotice = view.findViewById<TextView>(R.id.keyNotice)

        if (!ApiKeys.hasGroqKey()) keyNotice.visibility = View.VISIBLE

        if (history.isEmpty()) {
            addBubble("Hi! I'm the ShieldX assistant. Ask me anything about phishing, scams, or staying safe online.", false)
        }

        fun send() {
            val text = input.text.toString().trim()
            if (text.isEmpty()) return
            addBubble(text, true)
            history.add(ChatMessage("user", text))
            input.setText("")

            val thinkingBubble = addBubble("Thinking…", false, italic = true)

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val reply = ChatClient.sendMessage(history)
                    if (!isAdded) return@launch
                    thinkingBubble.text = reply
                    history.add(ChatMessage("assistant", reply))
                } catch (e: ChatClient.MissingApiKeyException) {
                    thinkingBubble.text = "I need a free Groq API key to think for real. Open ApiKeys.kt, paste your key from console.groq.com/keys, and rebuild the app."
                } catch (e: Exception) {
                    thinkingBubble.text = "Sorry, I couldn't reach the AI service just now. Please try again in a moment."
                }
                scrollToBottom()
            }
        }

        sendButton.setOnClickListener { send() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { send(); true } else false
        }
    }

    private fun addBubble(text: String, isUser: Boolean, italic: Boolean = false): TextView {
        val context = requireContext()
        val bubble = TextView(context).apply {
            this.text = text
            setTextColor(if (isUser) context.getColor(android.R.color.white) else context.getColor(R.color.text))
            textSize = 13f
            setPadding(28, 20, 28, 20)
            if (italic) setTypeface(typeface, android.graphics.Typeface.ITALIC)

            val bg = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 24f
                if (isUser) {
                    setColor(context.getColor(R.color.accent))
                } else {
                    setColor(context.getColor(R.color.card2))
                }
            }
            background = bg
        }
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = if (isUser) android.view.Gravity.END else android.view.Gravity.START
            bottomMargin = 16
            val maxWidthPx = (resources.displayMetrics.widthPixels * 0.75).toInt()
            width = LinearLayout.LayoutParams.WRAP_CONTENT
        }
        bubble.layoutParams = params
        bubble.maxWidth = (resources.displayMetrics.widthPixels * 0.75).toInt()
        chatContainer.addView(bubble)
        scrollToBottom()
        return bubble
    }

    private fun scrollToBottom() {
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }
}
