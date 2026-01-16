package com.example.webdro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

class WebServer(private val context: Context, port: Int) : NanoHTTPD(port) {

    interface ControlCallback {
        fun onSwitchCamera()
        fun onZoom(level: Float)
        fun onFlash(on: Boolean)
        fun onQuality(q: Int)
        fun onFocus()
    }

    var callback: ControlCallback? = null

    private var currentJpeg: ByteArray? = null
    private val lock = Object()
    private val listeners = ArrayList<MjpegInputStream>()

    fun updateFrame(jpeg: ByteArray) {
        synchronized(lock) {
            currentJpeg = jpeg
            // Notify all active streams
            for (stream in listeners) {
                stream.onNewFrame(jpeg)
            }
        }
    }

    override fun serve(session: IHTTPSession): Response {
        return when (session.uri) {
            "/" -> newFixedLengthResponse(Response.Status.OK, MIME_HTML, getHtmlIndex())
            "/logo.png" -> {
                try {
                    val inputStream = context.resources.openRawResource(R.drawable.pocketcam_logo)
                    return newChunkedResponse(Response.Status.OK, "image/png", inputStream)
                } catch (e: Exception) {
                    newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Logo Not Found")
                }
            }
            "/api/switch" -> {
                callback?.onSwitchCamera()
                newFixedLengthResponse("OK")
            }
            "/api/flash" -> {
                val state = session.parameters["on"]?.get(0).toBoolean()
                callback?.onFlash(state)
                newFixedLengthResponse("OK")
            }
            "/api/focus" -> {
                callback?.onFocus()
                newFixedLengthResponse("OK")
            }
            "/api/zoom" -> {
                val z = session.parameters["val"]?.get(0)?.toFloatOrNull() ?: 0f
                callback?.onZoom(z)
                newFixedLengthResponse("OK")
            }
            "/api/quality" -> {
                val q = session.parameters["val"]?.get(0)?.toIntOrNull() ?: 60
                callback?.onQuality(q)
                newFixedLengthResponse("OK")
            }
            "/stream" -> {
                val stream = MjpegInputStream()
                synchronized(lock) { listeners.add(stream) }
                val response = newChunkedResponse(Response.Status.OK, "multipart/x-mixed-replace; boundary=frame", stream)
                response
            }
            "/status" -> newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Running: ${currentJpeg != null}")
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
        }
    }

    private fun getHtmlIndex(): String {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>PocketCam Stream</title>
                <style>
                    :root { --bg: #121212; --card: #1e1e1e; --primary: #bb86fc; --sec: #03dac6; --text: #e0e0e0; }
                    body { margin: 0; background: var(--bg); color: var(--text); font-family: 'Segoe UI', sans-serif; display: flex; flex-direction: column; align-items: center; min-height: 100vh; }
                    
                    header { display: flex; align-items: center; gap: 1rem; margin: 1.5rem; }
                    h1 { margin: 0; color: var(--primary); font-size: 2rem; }
                    .logo { height: 80px; width: auto; max-width: 100%; border-radius: 12px; }
                    
                    .card { background: var(--card); border-radius: 12px; padding: 1rem; box-shadow: 0 8px 16px rgba(0,0,0,0.5); width: 95%; max-width: 900px; text-align: center; }
                    img#stream { width: 100%; border-radius: 8px; background: #000; min-height: 300px; }
                    
                    .panel { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 1rem; margin-top: 1.5rem; padding-top: 1rem; border-top: 1px solid #333; }
                    .control-group { background: #2c2c2c; padding: 0.8rem; border-radius: 8px; display: flex; flex-direction: column; gap: 0.5rem; align-items: center; }
                    label { font-size: 0.8rem; color: #aaa; text-transform: uppercase; letter-spacing: 1px; }
                    
                    button { background: var(--primary); color: #000; border: none; padding: 0.6rem 1rem; border-radius: 20px; font-weight: bold; cursor: pointer; width: 100%; }
                    button:active { transform: scale(0.98); }
                    button.toggle { background: #444; color: #fff; }
                    button.toggle.active { background: var(--sec); color: #000; }
                    
                    input[type=range] { width: 100%; accent-color: var(--sec); cursor: pointer; }
                </style>
            </head>
            <body>
                <header>
                    <h1>PocketCam</h1>
                </header>
                <div class="card">
                    <img id="stream" src="/stream">
                    
                    <div class="panel">
                        <!-- Camera Switch -->
                        <div class="control-group">
                            <label>Camera</label>
                            <button onclick="api('/api/switch')">Switch Front/Back</button>
                        </div>
                        
                        <!-- Flash -->
                        <div class="control-group">
                            <label>Flash</label>
                            <button id="flashBtn" class="toggle" onclick="toggleFlash()">OFF</button>
                        </div>

                        <!-- Focus -->
                        <div class="control-group">
                            <label>Focus</label>
                            <button onclick="api('/api/focus')">AUTO FOCUS</button>
                        </div>
                        
                        <!-- Zoom -->
                        <div class="control-group">
                            <label>Zoom</label>
                            <input type="range" min="0" max="100" value="0" oninput="api('/api/zoom?val=' + (this.value/100))">
                        </div>
                        
                        <!-- Quality -->
                        <div class="control-group">
                            <label>Quality</label>
                            <select onchange="api('/api/quality?val=' + this.value)" style="padding: 0.5rem; width: 100%; border-radius: 4px; background: #444; color: white;">
                                <option value="30">Low (30%)</option>
                                <option value="60" selected>Medium (60%)</option>
                                <option value="90">High (90%)</option>
                            </select>
                        </div>
                        
                         <!-- Actions -->
                        <div class="control-group">
                            <label>Actions</label>
                            <button onclick="document.getElementById('stream').requestFullscreen()">Fullscreen</button>
                        </div>
                    </div>
                </div>

                <script>
                    let flashState = false;
                    function api(url) { fetch(url); }
                    function toggleFlash() {
                        flashState = !flashState;
                        const btn = document.getElementById('flashBtn');
                        btn.innerText = flashState ? "ON" : "OFF";
                        btn.classList.toggle('active', flashState);
                        api('/api/flash?on=' + flashState);
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    // Custom InputStream that feeds MJPEG frames
    inner class MjpegInputStream : InputStream() {
        private val queue = java.util.concurrent.LinkedBlockingQueue<ByteArray>(1)
        private var currentStream: ByteArrayInputStream? = null
        private var isOpen = true

        fun onNewFrame(jpeg: ByteArray) {
            if (!isOpen) return
            // Drop old frame if exists
            queue.offer(jpeg) 
        }

        override fun read(): Int {
             // Single byte read - avoid if possible
             val b = ByteArray(1)
             if (read(b, 0, 1) == -1) return -1
             return b[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (currentStream == null || currentStream!!.available() == 0) {
                // Get next frame (blocking)
                try {
                    val jpeg = queue.take() // Blocks until frame available
                    val header = "--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${jpeg.size}\r\n\r\n"
                    val packet = header.toByteArray() + jpeg + "\r\n\r\n".toByteArray()
                    currentStream = ByteArrayInputStream(packet)
                } catch (e: Exception) {
                    isOpen = false
                    synchronized(lock) { listeners.remove(this) }
                    return -1
                }
            }
            return currentStream!!.read(b, off, len)
        }
        
        override fun close() {
            isOpen = false
            synchronized(lock) { listeners.remove(this) }
        }
    }
}
