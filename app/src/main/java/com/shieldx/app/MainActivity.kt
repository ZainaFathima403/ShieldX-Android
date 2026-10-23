package com.shieldx.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        if (savedInstanceState == null) {
            showFragment(DashboardFragment())
        }

        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_dashboard -> DashboardFragment()
                R.id.nav_url -> UrlScannerFragment()
                R.id.nav_qr -> QrScannerFragment()
                R.id.nav_msg -> MessageAnalyzerFragment()
                R.id.nav_ai -> AiAssistantFragment()
                else -> DashboardFragment()
            }
            showFragment(fragment)
            true
        }
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    /** Called by DashboardFragment's "Set as Default" button. */
    fun refreshDashboard() {
        val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        if (current is DashboardFragment) current.refresh()
    }

    /** Called by DashboardFragment's "Open Screenshot Scanner" button, since Screenshot
     *  Scanner no longer has its own bottom-nav tab (BottomNavigationView caps out at 5 items). */
    fun showScreenshotScanner() {
        showFragment(ScreenshotScannerFragment())
    }
}
