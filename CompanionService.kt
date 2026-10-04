package kz.timur.companion

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.*
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import kotlin.random.Random

/**
 * Floating "pet" overlay: walks along the bottom edge, can be dragged anywhere,
 * reacts to a tap, and switches sprite (mini-Claude / stickman) live via prefs.
 *
 * This is a self-contained overlay window — it does not read or modify any
 * other app's data or screen content.
 */
class CompanionService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var petView: ImageView
    private lateinit var params: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())

    private var screenWidth = 0
    private var direction = 1 // 1 = right, -1 = left
    private var isDragging = false
    private var isWalking = true

    private val skinReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            applySkin()
        }
    }

    private val walkRunnable = object : Runnable {
        override fun run() {
            if (isWalking && !isDragging) {
                step()
            }
            handler.postDelayed(this, 40L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels

        petView = ImageView(this).apply {
            setImageResource(R.drawable.companion_claude)
            setPadding(8, 8, 8, 8)
        }

        val overlayType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            140, 140,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = metrics.heightPixels - 400
        }

        attachTouchHandling()
        windowManager.addView(petView, params)
        applySkin()

        registerReceiver(
            skinReceiver,
            IntentFilter(MainActivity.ACTION_SKIN_CHANGED),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_NOT_EXPORTED else 0
        )

        handler.post(walkRunnable)
    }

    private fun applySkin() {
        val prefs = getSharedPreferences("companion_prefs", MODE_PRIVATE)
        val skin = prefs.getString("skin", "claude")
        petView.setImageResource(
            if (skin == "stickman") R.drawable.companion_stickman else R.drawable.companion_claude
        )
    }

    private fun step() {
        params.x += direction * 6
        if (params.x <= 0) direction = 1
        if (params.x + 140 >= screenWidth) direction = -1
        // flip sprite to face walking direction
        petView.scaleX = direction.toFloat()
        windowManager.updateViewLayout(petView, params)
    }

    private fun attachTouchHandling() {
        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var downTime = 0L

        petView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    downTime = System.currentTimeMillis()
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    if (abs(dx) > 12 || abs(dy) > 12) {
                        isDragging = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager.updateViewLayout(petView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val elapsed = System.currentTimeMillis() - downTime
                    if (!isDragging && elapsed < 300) {
                        react()
                    }
                    isDragging = false
                    true
                }
                else -> false
            }
        }
    }

    /** Small tap reaction: a quick hop animation. */
    private fun react() {
        isWalking = false
        petView.animate()
            .translationYBy(-40f)
            .setDuration(140)
            .withEndAction {
                petView.animate()
                    .translationYBy(40f)
                    .setDuration(140)
                    .withEndAction { isWalking = true }
                    .start()
            }
            .start()

        // occasionally reverse direction after a reaction, for variety
        if (Random.nextBoolean()) direction *= -1
    }

    private fun startForegroundWithNotification() {
        val channelId = "companion_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Companion", NotificationManager.IMPORTANCE_MIN
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Компаньон активен")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(1, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(walkRunnable)
        runCatching { unregisterReceiver(skinReceiver) }
        runCatching { windowManager.removeView(petView) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
