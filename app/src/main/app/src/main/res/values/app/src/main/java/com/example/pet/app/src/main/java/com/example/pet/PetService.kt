package com.example.pet

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

class PetService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var root: FrameLayout
    private lateinit var petView: TextView
    private lateinit var bubble: TextView
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var engine: EmotionEngine

    private val handler = Handler(Looper.getMainLooper())

    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var moved = false

    private val hideBubble = Runnable {
        bubble.visibility = View.GONE
    }

    private val ticker = object : Runnable {
        override fun run() {
            engine.tick()
            engine.maybeProactive()?.let { showBubble(it) }
            handler.postDelayed(this, 60_000)
        }
    }

    override fun onCreate() {
        super.onCreate()

        createChannel()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                1,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(1, buildNotification())
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        engine = EmotionEngine(this)
        addOverlay()
        handler.postDelayed(ticker, 60_000)
    }

    private fun addOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        root = FrameLayout(this)

        bubble = TextView(this).apply {
            setTextColor(Color.BLACK)
            textSize = 14f
            setPadding(24, 12, 24, 12)
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.WHITE)
            }
            visibility = View.GONE
        }

        petView = TextView(this).apply {
            text = "🤖"
            textSize = 56f
            gravity = Gravity.CENTER
        }

        root.addView(
            bubble,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }
        )

        root.addView(
            petView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            }
        )

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        root.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()

                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                        moved = true
                    }

                    params.x = startX + dx
                    params.y = startY + dy
                    windowManager.updateViewLayout(root, params)
                }

                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        showBubble(engine.handle(PetEvent.DRAG))
                    } else {
                        showBubble(engine.handle(PetEvent.PET))
                    }
                }
            }
            true
        }

        windowManager.addView(root, params)
    }

    private fun showBubble(text: String) {
        bubble.text = text
        bubble.visibility = View.VISIBLE
        handler.removeCallbacks(hideBubble)
        handler.postDelayed(hideBubble, 3500)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::root.isInitialized) {
            try {
                windowManager.removeView(root)
            } catch (_: Exception) {
            }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL,
                "桌宠",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, PetService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("桌宠在陪你")
            .setContentText("点击打开")
            .setContentIntent(openIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "停止",
                stopIntent
            )
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "pet_channel"
    }
}
