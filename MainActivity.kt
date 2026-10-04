package kz.timur.companion

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("companion_prefs", MODE_PRIVATE)

        val btnPermission = findViewById<android.widget.Button>(R.id.btnPermission)
        val btnStartCompanion = findViewById<android.widget.Button>(R.id.btnStartCompanion)
        val btnStopCompanion = findViewById<android.widget.Button>(R.id.btnStopCompanion)
        val btnStartBurnIn = findViewById<android.widget.Button>(R.id.btnStartBurnIn)
        val btnStopBurnIn = findViewById<android.widget.Button>(R.id.btnStopBurnIn)
        val radioGroup = findViewById<android.widget.RadioGroup>(R.id.skinGroup)

        // restore saved skin choice
        val savedSkin = prefs.getString("skin", "claude")
        if (savedSkin == "stickman") radioGroup.check(R.id.radioStickman)
        else radioGroup.check(R.id.radioClaude)

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val skin = if (checkedId == R.id.radioStickman) "stickman" else "claude"
            prefs.edit().putString("skin", skin).apply()
            // if service is already running, ask it to refresh
            sendBroadcast(Intent(ACTION_SKIN_CHANGED).setPackage(packageName))
        }

        btnPermission.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Разрешение уже выдано", Toast.LENGTH_SHORT).show()
            }
        }

        btnStartCompanion.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Сначала выдай разрешение на показ поверх экрана", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val intent = Intent(this, CompanionService::class.java)
            ContextCompat.startForegroundService(this, intent)
        }

        btnStopCompanion.setOnClickListener {
            stopService(Intent(this, CompanionService::class.java))
        }

        btnStartBurnIn.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Сначала выдай разрешение на показ поверх экрана", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val intent = Intent(this, BurnInService::class.java)
            ContextCompat.startForegroundService(this, intent)
        }

        btnStopBurnIn.setOnClickListener {
            stopService(Intent(this, BurnInService::class.java))
        }
    }

    companion object {
        const val ACTION_SKIN_CHANGED = "kz.timur.companion.SKIN_CHANGED"
    }
}
