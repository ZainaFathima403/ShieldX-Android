package com.shieldx.app

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class ScanActivity : AppCompatActivity() {

    private var originalUrl: String? = null // set only when we arrived via a tapped link (ACTION_VIEW)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        val riskIcon = findViewById<TextView>(R.id.riskIcon)
        val riskBadge = findViewById<TextView>(R.id.riskBadge)
        val riskTitle = findViewById<TextView>(R.id.riskTitle)
        val scannedSubject = findViewById<TextView>(R.id.scannedSubject)
        val riskReasons = findViewById<TextView>(R.id.riskReasons)
        val backButton = findViewById<Button>(R.id.backButton)
        val proceedButton = findViewById<Button>(R.id.proceedButton)

        val subjectText = extractSubject()
        if (subjectText.isNullOrBlank()) {
            finish()
            return
        }
        scannedSubject.text = subjectText
        backButton.isEnabled = false
        proceedButton.isEnabled = false
        riskTitle.text = if (ApiKeys.hasSafeBrowsingKey()) "Checking heuristics + Google Safe Browsing…" else "Checking heuristics…"
        riskBadge.text = "SCANNING"

        val soloUrl = ScanEngine.extractUrls(subjectText).let { urls ->
            if (urls.size == 1 && urls[0].trim() == subjectText.trim()) urls[0] else null
        }

        lifecycleScope.launch {
            val result = when {
                originalUrl != null -> ScanEngine.analyzeUrlWithSafeBrowsing(originalUrl!!)
                soloUrl != null -> ScanEngine.analyzeUrlWithSafeBrowsing(soloUrl)
                else -> ScanEngine.analyzeMessageWithSafeBrowsing(subjectText)
            }

            data class Style(val icon: String, val badgeText: String, val title: String, val color: Int)
            val style = when (result.level) {
                RiskLevel.DANGER -> Style("⛔", "DANGEROUS", "This looks like a scam or phishing attempt", getColor(R.color.danger))
                RiskLevel.WARN -> Style("⚠️", "SUSPICIOUS", "This has some risky signs", getColor(R.color.warn))
                else -> Style("✅", "SAFE", "No strong phishing signals found", getColor(R.color.safe))
            }

            riskIcon.text = style.icon
            riskBadge.text = "${style.badgeText} · ${result.score}/100"
            riskBadge.setTextColor(style.color)
            riskTitle.text = style.title
            riskReasons.text = result.reasons.joinToString("\n\n") { "• $it" }

            val cardBg = findViewById<android.view.View>(R.id.scanCard).background
            if (cardBg is GradientDrawable) {
                cardBg.setStroke(4, style.color)
            }

            val labelSubject = originalUrl ?: soloUrl ?: subjectText
            StatsStore.recordScan(
                this@ScanActivity,
                result.level,
                if (originalUrl != null || soloUrl != null) "Link opened via ${labelSubject.take(40)}" else "Shared text scanned"
            )

            backButton.isEnabled = true
            proceedButton.isEnabled = true
            backButton.setOnClickListener { finish() }

            val canProceed = originalUrl != null || soloUrl != null
            if (canProceed) {
                proceedButton.text = if (result.level == RiskLevel.SAFE) "Open Link" else "Proceed Anyway"
                proceedButton.setOnClickListener {
                    openInBrowser(originalUrl ?: soloUrl!!)
                    finish()
                }
            } else {
                proceedButton.text = "Close"
                proceedButton.setOnClickListener { finish() }
            }
        }
    }

    private fun extractSubject(): String? {
        return when (intent?.action) {
            Intent.ACTION_VIEW -> {
                val data: Uri? = intent.data
                originalUrl = data?.toString()
                originalUrl
            }
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
    }

    // Common browser package names, in preference order. Targeting one of these directly
    // (rather than asking Android to resolve/disambiguate) works even when ShieldX itself
    // has been set as the default app for links — that system-level "default handler"
    // assignment only affects *implicit* resolution, not an explicit launch like this.
    private val KNOWN_BROWSERS = listOf(
        "com.android.chrome",
        "com.sec.android.app.sbrowser", // Samsung Internet
        "org.mozilla.firefox",
        "com.microsoft.emmx", // Edge
        "com.opera.browser",
        "com.brave.browser"
    )

    private fun openInBrowser(url: String) {
        val uri = Uri.parse(url)

        // 1) Try known browsers directly, by package name.
        for (pkg in KNOWN_BROWSERS) {
            if (!isPackageInstalled(pkg)) continue
            try {
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(pkg)
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
                startActivity(intent)
                return
            } catch (e: Exception) {
                // try the next known browser
            }
        }

        // 2) None of the known browsers were found/launchable — fall back to asking
        // Android to resolve it, still explicitly excluding ShieldX so this can never
        // loop back into itself.
        val viewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val resolved = packageManager.queryIntentActivities(viewIntent, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
            .filter { it != packageName }

        if (resolved.isNotEmpty()) {
            viewIntent.setPackage(resolved.first())
            try {
                startActivity(viewIntent)
                return
            } catch (e: Exception) {
                // fall through to the excluded-chooser attempt below
            }
        }

        try {
            val chooserIntent = Intent.createChooser(viewIntent, "Open link with").apply {
                putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(
                    ComponentName(this@ScanActivity, ScanActivity::class.java),
                    ComponentName(this@ScanActivity, MainActivity::class.java)
                ))
            }
            startActivity(chooserIntent)
        } catch (e: Exception) {
            // No browser available at all on this device — nothing more we can do.
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
