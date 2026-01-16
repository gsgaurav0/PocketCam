# System Architecture

## Overview
This system streams live video from an Android device to multiple consumers:
1.  **Web Browsers**: Uses MJPEG over HTTP for compatibility (No plugins needed).
2.  **Linux Desktop**: Uses H.264 over TCP/WebSocket for high quality and low latency, piping to a virtual webcam.

## Data Flow Diagram

```mermaid
graph TD
    subgraph Android["Android Device (Kotlin)"]
        Cam[CameraX Input]
        
        %% Path 1: MJPEG for Web
        Cam -->|YUV_420_888| Analyzer[ImageAnalyzer]
        Analyzer -->|NV21/YUV| JpegEnc[YuvImage to JPEG]
        JpegEnc -->|MJPEG Frame| HttpServer[Embedded HTTP Server]
        HttpServer -->|HTTP Stream| WebClient[Web Browser]

        %% Path 2: H.264 for Desktop
        Cam -->|Surface| MediaCodec[MediaCodec H.264]
        MediaCodec -->|NAL Units| StreamSrv[Stream Server (Socket)]
        StreamSrv -->|Raw H.264| DesktopClient[Linux Desktop App]
    end

    subgraph Desktop["Linux Desktop (Python)"]
        DesktopClient -->|Raw H.264| Decoder[Frame Decoder (av/OpenCV)]
        Decoder -->|RGB Frames| Preview[Preview Window]
        Decoder -->|RGB Frames| V4L2[v4l2loopback Device]
        V4L2 -->|Video Feed| VirtualCam[Virtual Webcam /dev/videoX]
    end
```

## Folder Structure

```
web-dro/
├── android/                  # Android Project Root
│   ├── app/                  # App Module
│   │   ├── src/main/java/com/example/webdro/
│   │   │   ├── MainActivity.kt       # UI & Permissions
│   │   │   ├── CameraStreamer.kt     # CameraX + MJPEG Logic
│   │   │   ├── H264Streamer.kt       # MediaCodec + Socket Logic
│   │   │   └── WebServer.kt          # HTTP Server (NanoHTTPD)
│   │   └── src/main/AndroidManifest.xml
│   ├── build.gradle.kts      # App Build Config
│   └── settings.gradle.kts   # Project Settings
├── desktop/                  # Desktop Client
│   ├── main.py               # Entry point
│   ├── decoder.py            # H.264 Decoding Logic
│   ├── v4l2_driver.py        # Virtual Webcam Interface
│   └── requirements.txt      # Python Dependencies
└── ARCHITECTURE.md           # This file
```

## Build Order

1.  **Android MJPEG Stream**:
    *   Implement `CameraX` preview.
    *   Extract frames in `ImageAnalysis`.
    *   Convert to JPEG.
    *   Serve via HTTP at `/stream`.
    *   *Verify*: Open in Chrome.

2.  **Android H.264 Stream**:
    *   Configure `MediaCodec`.
    *   Input logic: `CameraX` Surface -> `MediaCodec`.
    *   Send output via TCP Socket.

3.  **Desktop Receiver**:
    *   Connect to Android Socket.
    *   Decode H.264 stream.
    *   Display in window.

4.  **Virtual Webcam**:
    *   Install `v4l2loopback`.
    *   Pipe decoded frames to `/dev/videoX`.

## Dual Encoder Strategy explanation
*   **MJPEG (Motion JPEG)**: Simple sequence of JPEG images.
    *   *Pros*: Supported by EVERY browser natively (`<img src="...">`). Low latency processing.
    *   *Cons*: High bandwidth. Not efficient.
*   **H.264 (AVC)**: Advanced Video Coding.
    *   *Pros*: Extremely efficient compression (good for WiFi). High quality.
    *   *Cons*: Browsers require MSE/WebRTC (complex). Harder to decode in Python without libraries like ffmpeg.
    *   *Strategy*: We use MJPEG for "easy access" (Web) and H.264 for "high performance" (Desktop App).

## Thread Safety & Performance
*   **CameraX**: Runs on a background `Executor`.
*   **MJPEG**: JPEG compression is CPU intensive. We must offload this to a background thread, not the UI thread.
*   **Network**: Network operations (Server accept, socket write) MUST be on background threads (Android throws NetworkOnMainThreadException otherwise).
*   **Memory**: Reuse buffers. Don't allocate new `byte[]` for every frame. Use `ImageProxy` efficiently and close it immediately.
