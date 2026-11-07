package com.example.pet

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        val startButton = Button(this).apply {
            text = "启动桌宠"
        }

        val errorText = TextView(this).apply {
            textSize = 12f
            setTextColor(android.graphics.Color.RED)
            setPadding(0, 24, 0, 0)
        }

        startButton.setOnClickListener {
            try {
                if (!Settings.canDrawOverlays(this)) {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                } else {
                    requestNotificationPermissionIfNeeded()
                    startForegroundService(Intent(this, PetService::class.java))
                }
            } catch (e: Exception) {
                errorText.text = "启动失败: ${e.message}"
            }
        }

        box.addView(startButton)
        box.addView(errorText)
        setContentView(box)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        }
    }
}
