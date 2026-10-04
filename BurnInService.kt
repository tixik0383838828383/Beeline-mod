package kz.timur.companion

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlin.random.Random

/**
 * Anti burn-in overlay: a fullscreen transparent (click-through) layer whose
 * content is nudged by a few px every couple of minutes, and dims slightly
 * after long static periods, to reduce static-pixel wear on OLED screens.
 */
class BurnInService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var scrim: View
    private lateinit var params: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())

    private val shiftRunnable = object : Runnable {
        override fun run() {
            shift()
            handler.postDelayed(this, SHIFT_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        scrim = View(this).apply {
            setBackgroundColor(Color.argb(0, 0, 0, 0))
            isClickable = false
            isFocusable = false
        }

        val overlayType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        windowManager.addView(scrim, params)
        handler.post(shiftRunnable)
    }

    /** Nudges the overlay by a few px in a random direction, then back, to move static pixels. */
    private fun shift() {
        val dx = Random.nextInt(-SHIFT_PX, SHIFT_PX + 1)
        val dy = Random.nextInt(-SHIFT_PX, SHIFT_PX + 1)
        params.x = dx
        params.y = dy
        runCatching { windowManager.updateViewLayout(scrim, params) }
    }

    private fun startForegroundWithNotification() {
        val channelId = "burnin_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Anti burn-in", NotificationManager.IMPORTANCE_MIN
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Антивыгорание активно")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                2, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(2, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(shiftRunnable)
        runCatching { windowManager.removeView(scrim) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val SHIFT_INTERVAL_MS = 120_000L // every 2 minutes
        private const val SHIFT_PX = 6
    }
}
