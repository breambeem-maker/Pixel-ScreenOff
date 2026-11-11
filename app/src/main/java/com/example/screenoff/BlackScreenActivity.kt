package com.example.screenoff

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewAnimationUtils
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
import kotlin.math.hypot
import kotlin.math.max

class BlackScreenActivity : Activity() {

    private val PREFS = "screen_off_prefs"
    private val KEY_ORIGINAL_TIMEOUT = "original_timeout"
    private val MIN_TIMEOUT = 5000 // 5 วินาที

    private lateinit var blackOverlay: FrameLayout

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                restoreTimeoutAndFinish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Settings.System.canWrite(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
            finish()
            return
        }

        hideSystemBars()

        blackOverlay = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.INVISIBLE
        }
        setContentView(blackOverlay)

        blackOverlay.post {
            startCircularBlackoutAnimation()
        }
    }

    private fun startCircularBlackoutAnimation() {
        val bounds = intent.sourceBounds
        val centerX = bounds?.centerX() ?: (blackOverlay.width / 2)
        val centerY = bounds?.centerY() ?: (blackOverlay.height / 3)

        val maxRadius = hypot(
            max(centerX, blackOverlay.width - centerX).toDouble(),
            max(centerY, blackOverlay.height - centerY).toDouble()
        ).toFloat()

        blackOverlay.visibility = View.VISIBLE

        val anim = ViewAnimationUtils.createCircularReveal(
            blackOverlay,
            centerX,
            centerY,
            0f,
            maxRadius
        ).apply {
            duration = 380
            interpolator = AccelerateInterpolator()
        }

        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                dimScreenBrightnessToZero()
                prepareSystemScreenTimeout()
            }
        })

        anim.start()
    }

    private fun dimScreenBrightnessToZero() {
        val params = window.attributes
        params.screenBrightness = 0.0f
        window.attributes = params
    }

    private fun prepareSystemScreenTimeout() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val currentTimeout = Settings.System.getInt(
            contentResolver,
            Settings.System.SCREEN_OFF_TIMEOUT,
            30000
        )
        if (currentTimeout > MIN_TIMEOUT) {
            prefs.edit().putInt(KEY_ORIGINAL_TIMEOUT, currentTimeout).apply()
        }

        Settings.System.putInt(
            contentResolver,
            Settings.System.SCREEN_OFF_TIMEOUT,
            MIN_TIMEOUT
        )

        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))

        Handler(Looper.getMainLooper()).postDelayed({
            restoreTimeoutAndFinish()
        }, 12000)
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }

    private fun restoreTimeoutAndFinish() {
        try {
            val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val originalTimeout = prefs.getInt(KEY_ORIGINAL_TIMEOUT, 30000)
            Settings.System.putInt(
                contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                originalTimeout
            )
            unregisterReceiver(screenOffReceiver)
        } catch (_: Exception) {}
        finishAffinity()
    }

    override fun onDestroy() {
        super.onDestroy()
        restoreTimeoutAndFinish()
    }
}
