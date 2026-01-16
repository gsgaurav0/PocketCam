package com.example.webdro

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.Executors

class H264Streamer {
    private var encoder: MediaCodec? = null
    private var clientSocket: Socket? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var isRunning = false

    fun start(width: Int, height: Int) {
        isRunning = true
        executor.submit {
            try {
                // Setup Encoding
                val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
                format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) // NV21/NV12
                format.setInteger(MediaFormat.KEY_BIT_RATE, 2000000) // 2Mbps
                format.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

                encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                encoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                encoder?.start()

                // Setup Networking (Simple TCP Server for one client)
                val serverSocket = ServerSocket(8081)
                Log.d("H264", "Waiting for client on 8081...")
                clientSocket = serverSocket.accept()
                Log.d("H264", "Client connected!")

                runEncoderLoop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun queueFrame(nv21: ByteArray) {
        if (!isRunning || encoder == null) return
        try {
            val inputBufferIndex = encoder!!.dequeueInputBuffer(10000)
            if (inputBufferIndex >= 0) {
                val inputBuffer = encoder!!.getInputBuffer(inputBufferIndex)
                inputBuffer?.clear()
                inputBuffer?.put(nv21)
                encoder!!.queueInputBuffer(inputBufferIndex, 0, nv21.size, System.nanoTime() / 1000, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun runEncoderLoop() {
        val bufferInfo = MediaCodec.BufferInfo()
        val stream = clientSocket?.getOutputStream()
        
        while (isRunning && clientSocket != null && !clientSocket!!.isClosed) {
            val outputBufferIndex = encoder!!.dequeueOutputBuffer(bufferInfo, 10000)
            if (outputBufferIndex >= 0) {
                val outputBuffer = encoder!!.getOutputBuffer(outputBufferIndex)
                
                // Write data to socket
                // We send raw NAL units. For a robust stream, we might want to frame them (length-prefixed).
                // Let's use simple length-prefix framing: [4 bytes length][data]
                val outData = ByteArray(bufferInfo.size)
                outputBuffer?.get(outData)
                
                try {
                    // Start Code Emulation Prevention or just raw?
                    // raw H.264 stream is fine for ffmpeg if we just dump it, but for a custom receiver, length-prefix is safer.
                    // For simply piping to ffmpeg via stdin, raw is often okay if it has Annex B start codes (00 00 00 01).
                    // MediaCodec usually outputs Annex B.
                    
                    stream?.write(outData)
                } catch (e: IOException) {
                    isRunning = false
                    break
                }

                encoder!!.releaseOutputBuffer(outputBufferIndex, false)
            }
        }
    }
}
