package com.example.webdro

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors

class CameraStreamer(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val server: WebServer,
    private val h264Streamer: H264Streamer
) {

    private var camera: androidx.camera.core.Camera? = null
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var jpegQuality = 60

    private val executor = Executors.newSingleThreadExecutor()
    private var imageAnalysis: ImageAnalysis? = null

    fun start() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases(cameraProvider)
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder().build()
        
        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis?.setAnalyzer(executor) { image ->
            processImage(image)
        }

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner, cameraSelector, imageAnalysis
            )
        } catch (exc: Exception) {
            Log.e("WebDro", "Use case binding failed", exc)
        }
    }

    private fun processImage(image: ImageProxy) {
        if (image.format != ImageFormat.YUV_420_888) {
            image.close()
            return
        }

        val nv21 = yuv420888ToNv21(image)
        
        // 1. MJPEG with dynamic quality
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), jpegQuality, out)
        server.updateFrame(out.toByteArray())

        // 2. H.264
        h264Streamer.queueFrame(nv21)
        
        image.close()
    }
    
    // --- Controls ---

    fun toggleCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        start() // Re-bind
    }

    fun setZoom(level: Float) {
        // level 0.0 to 1.0
        camera?.cameraControl?.setLinearZoom(level)
    }

    fun toggleFlash(on: Boolean) {
        camera?.cameraControl?.enableTorch(on)
    }
    
    fun setQuality(q: Int) {
        // 10 to 100
        jpegQuality = q.coerceIn(10, 100)
    }

    fun triggerFocus() {
        val factory = androidx.camera.core.SurfaceOrientedMeteringPointFactory(1f, 1f)
        val point = factory.createPoint(0.5f, 0.5f) // Center
        val action = androidx.camera.core.FocusMeteringAction.Builder(point, androidx.camera.core.FocusMeteringAction.FLAG_AF)
            .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    fun stop() {
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
            Log.d("WebDro", "Camera Stopped")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 2
        val nv21 = ByteArray(ySize + uvSize)

        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        // Y Channel (Luma) - Just copy
        yBuffer.get(nv21, 0, ySize)

        // UV Channels (Chroma)
        // NV21 expects VU interleaved.
        // YUV_420_888 buffers might be separate or interleaved.
        // We need to robustly copy them.
        
        val pixelStride = image.planes[1].pixelStride
        val rowStride = image.planes[1].rowStride
        
        var pos = ySize
        
        if (pixelStride == 2 && rowStride == width && uBuffer.get(0) == vBuffer.get(1)) {
            // Optimization: If they are already interleaved correctly, we might be able to block copy.
            // But usually V is first for NV21, U first for NV12.
        }

        // Reliable (but slower) pixel-by-pixel copy
        val vPixels = ByteArray(vBuffer.remaining())
        val uPixels = ByteArray(uBuffer.remaining())
        vBuffer.get(vPixels)
        uBuffer.get(uPixels)
        
        // Loop for chroma
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val index = row * rowStride + col * pixelStride
                // NV21: V first, then U
                if (index < vPixels.size) nv21[pos++] = vPixels[index]
                if (index < uPixels.size) nv21[pos++] = uPixels[index]
            }
        }
        
        return nv21
    }
}
