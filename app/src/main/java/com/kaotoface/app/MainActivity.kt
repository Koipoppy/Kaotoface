package com.kaotoface.app

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaotoface.app.ui.KaoToFaceScreen
import com.kaotoface.app.ui.theme.KaoToFaceTheme

class MainActivity : ComponentActivity() {
    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideStatusBar() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        hideStatusBar()
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
            if (insets.isVisible(WindowInsetsCompat.Type.statusBars())) {
                window.decorView.post { hideStatusBar() }
            }
            insets
        }
        val app = application as KaoToFaceApp
        setContent {
            KaoToFaceTheme {
                val state by app.engine.state.collectAsStateWithLifecycle()
                val level by app.engine.level.collectAsStateWithLifecycle()
                val settings by app.settings.state.collectAsStateWithLifecycle()
                KaoToFaceScreen(
                    engine = app.engine,
                    settingsStore = app.settings,
                    state = state,
                    level = level,
                    settings = settings,
                )
            }
        }
    }

    private val hideLoop = object : Runnable {
        override fun run() {
            hideStatusBar()
            hideHandler.postDelayed(this, 500)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideStatusBar()
            hideHandler.removeCallbacks(hideRunnable)
            hideHandler.postDelayed(hideRunnable, 700)
            hideHandler.postDelayed(hideRunnable, 1800)
        }
    }

    override fun onResume() {
        super.onResume()
        // Some devices reveal the status bar when the microphone privacy
        // indicator appears; keep the canvas full-screen while we are visible.
        hideHandler.removeCallbacks(hideLoop)
        hideHandler.post(hideLoop)
    }

    override fun onPause() {
        hideHandler.removeCallbacks(hideLoop)
        super.onPause()
    }

    override fun onDestroy() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.removeCallbacks(hideLoop)
        super.onDestroy()
    }

    /** The in-app canvas owns the whole screen: no Android status bar. */
    private fun hideStatusBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.statusBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
