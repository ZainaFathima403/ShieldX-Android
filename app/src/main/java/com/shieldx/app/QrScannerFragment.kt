package com.shieldx.app

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch

class QrScannerFragment : Fragment(R.layout.fragment_qr_scanner) {

    private lateinit var previewImage: ImageView
    private lateinit var statusText: TextView
    private lateinit var decodedText: TextView
    private lateinit var resultContainer: LinearLayout

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) handleImage(uri)
    }

    private val scanWithCamera = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val value = result.data?.getStringExtra(QrCameraActivity.EXTRA_DECODED_VALUE)
        if (result.resultCode == android.app.Activity.RESULT_OK && value != null) {
            handleDecodedValue(value)
        }
    }

    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            scanWithCamera.launch(Intent(requireContext(), QrCameraActivity::class.java))
        } else {
            Toast.makeText(requireContext(), "Camera permission is needed to scan QR codes live.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        previewImage = view.findViewById(R.id.previewImage)
        statusText = view.findViewById(R.id.statusText)
        decodedText = view.findViewById(R.id.decodedText)
        resultContainer = view.findViewById(R.id.resultContainer)

        view.findViewById<Button>(R.id.pickImageButton).setOnClickListener {
            pickImage.launch("image/*")
        }

        view.findViewById<Button>(R.id.scanCameraButton).setOnClickListener {
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                requireContext(), android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                scanWithCamera.launch(Intent(requireContext(), QrCameraActivity::class.java))
            } else {
                requestCameraPermission.launch(android.Manifest.permission.CAMERA)
            }
        }
    }

    private fun handleDecodedValue(value: String) {
        ResultView.clear(resultContainer)
        previewImage.visibility = View.GONE
        decodedText.text = value
        decodedText.visibility = View.VISIBLE
        statusText.text = if (ApiKeys.hasSafeBrowsingKey()) "Checking heuristics + Google Safe Browsing…" else "Checking heuristics…"

        viewLifecycleOwner.lifecycleScope.launch {
            val result = ScanEngine.analyzeUrlWithSafeBrowsing(value)
            if (!isAdded) return@launch
            statusText.text = ""
            ResultView.render(resultContainer, result, value)
            StatsStore.recordScan(requireContext(), result.level, "QR code scan (camera)")
        }
    }

    private fun handleImage(uri: Uri) {
        ResultView.clear(resultContainer)
        decodedText.visibility = View.GONE
        statusText.text = "Reading QR code…"

        try {
            val bitmap: Bitmap = MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
            previewImage.setImageBitmap(bitmap)
            previewImage.visibility = View.VISIBLE

            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            val scanner = BarcodeScanning.getClient(options)
            val image = InputImage.fromBitmap(bitmap, 0)

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val value = barcodes.firstOrNull()?.rawValue
                    if (value == null) {
                        statusText.text = ""
                        ResultView.render(
                            resultContainer,
                            ScanResult(RiskLevel.WARN, 40, listOf("Could not detect a QR code in this image. Try a clearer photo.")),
                            null
                        )
                        return@addOnSuccessListener
                    }
                    decodedText.text = value
                    decodedText.visibility = View.VISIBLE
                    statusText.text = if (ApiKeys.hasSafeBrowsingKey()) "Checking heuristics + Google Safe Browsing…" else "Checking heuristics…"

                    viewLifecycleOwner.lifecycleScope.launch {
                        val result = ScanEngine.analyzeUrlWithSafeBrowsing(value)
                        if (!isAdded) return@launch
                        statusText.text = ""
                        ResultView.render(resultContainer, result, value)
                        StatsStore.recordScan(requireContext(), result.level, "QR code scan")
                    }
                }
                .addOnFailureListener {
                    statusText.text = "Could not read this image — please try another."
                }
        } catch (e: Exception) {
            statusText.text = "Could not read this image — please try another."
        }
    }
}
