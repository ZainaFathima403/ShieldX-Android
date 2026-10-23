package com.shieldx.app

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch

class ScreenshotScannerFragment : Fragment(R.layout.fragment_screenshot_scanner) {

    private lateinit var previewImage: ImageView
    private lateinit var statusText: TextView
    private lateinit var extractedText: TextView
    private lateinit var resultContainer: LinearLayout

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) handleImage(uri)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        previewImage = view.findViewById(R.id.previewImage)
        statusText = view.findViewById(R.id.statusText)
        extractedText = view.findViewById(R.id.extractedText)
        resultContainer = view.findViewById(R.id.resultContainer)

        view.findViewById<Button>(R.id.pickImageButton).setOnClickListener {
            pickImage.launch("image/*")
        }
    }

    private fun handleImage(uri: Uri) {
        ResultView.clear(resultContainer)
        extractedText.visibility = View.GONE
        statusText.text = "Reading text from image…"

        try {
            val bitmap: Bitmap = MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
            previewImage.setImageBitmap(bitmap)
            previewImage.visibility = View.VISIBLE

            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromBitmap(bitmap, 0)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val text = visionText.text
                    if (text.isBlank()) {
                        statusText.text = ""
                        ResultView.render(
                            resultContainer,
                            ScanResult(RiskLevel.WARN, 40, listOf("Could not read any text from this image. Try a clearer screenshot.")),
                            null
                        )
                        return@addOnSuccessListener
                    }
                    extractedText.text = text
                    extractedText.visibility = View.VISIBLE
                    statusText.text = "Analyzing extracted text…"

                    viewLifecycleOwner.lifecycleScope.launch {
                        val result = ScanEngine.analyzeMessageWithSafeBrowsing(text)
                        if (!isAdded) return@launch
                        statusText.text = ""
                        ResultView.render(resultContainer, result)
                        StatsStore.recordScan(requireContext(), result.level, "Screenshot analyzed")
                    }
                }
                .addOnFailureListener {
                    statusText.text = "OCR failed — please try a different image."
                }
        } catch (e: Exception) {
            statusText.text = "Could not read this image — please try another."
        }
    }
}
