package com.example.webdro

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {

    private lateinit var server: WebServer
    private lateinit var cameraStreamer: CameraStreamer
    private val PORT = 8080

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, 10)
        }

        server = WebServer(this, PORT)
        val h264Streamer = H264Streamer()
        cameraStreamer = CameraStreamer(this, this, server, h264Streamer)
        
        // Wire WebServer controls to CameraStreamer
        server.callback = object : WebServer.ControlCallback {
            override fun onSwitchCamera() = runOnUiThread { cameraStreamer.toggleCamera() }
            override fun onZoom(level: Float) = runOnUiThread { cameraStreamer.setZoom(level) }
            override fun onFlash(on: Boolean) = runOnUiThread { cameraStreamer.toggleFlash(on) }
            override fun onQuality(q: Int) { cameraStreamer.setQuality(q) }
            override fun onFocus() = runOnUiThread { cameraStreamer.triggerFocus() }
        }

        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)
        val tvIp = findViewById<TextView>(R.id.tvIp)
        val tvPort = findViewById<TextView>(R.id.tvPort)
        
        // Initial State
        tvIp.text = getIpAddress()
        tvPort.text = PORT.toString()

        val btnMenu = findViewById<android.widget.ImageButton>(R.id.btnMenu)
        btnMenu.setOnClickListener { view ->
            val popup = android.widget.PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.menu_main, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_how_to_use -> {
                        showInfoDialog("How to use this app", 
                            "1. Connect your PC and Phone to the SAME WiFi network.\n\n" +
                            "2. Note the IP Address shown on the screen.\n\n" +
                            "3. Open your browser on PC and type 'http://<IP>:8080'.\n\n" +
                            "4. You can also use this app with OBS, Zoom, etc. by following the online guide.")
                        true
                    }
                    R.id.action_creator_info -> {
                        showInfoDialog("Creator Info", "Name: Gaurav Sharma\nGitHub: gsgaurav0")
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, 10)
        }

        btnStart.setOnClickListener {
            try {
                server.start()
                h264Streamer.start(640, 480) 
                cameraStreamer.start()
                
                btnStart.isEnabled = false
                btnStop.isEnabled = true
                btnStart.backgroundTintList = ContextCompat.getColorStateList(this, R.color.info_box_bg)
                btnStop.backgroundTintList = ContextCompat.getColorStateList(this, R.color.accent_green) // Active state
                
            } catch (e: Exception) {
                // tvStatus.text = "Error: ${e.message}" 
            }
        }
        
        btnStop.setOnClickListener {
            try {
                cameraStreamer.stop()
                server.stop()
                
                btnStart.isEnabled = true
                btnStop.isEnabled = false
                btnStop.backgroundTintList = ContextCompat.getColorStateList(this, R.color.info_box_bg)
                btnStart.backgroundTintList = ContextCompat.getColorStateList(this, R.color.accent_green) // Ready state
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getIpAddress(): String {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (!address.isLoopbackAddress && address is Inet4Address) {
                    return address.hostAddress ?: ""
                }
            }
        }
        return "Unknown"
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.INTERNET)
    }

    private fun showInfoDialog(title: String, message: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
