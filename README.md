# PocketCam

**PocketCam** turns your Android device into a high-quality wireless webcam for your PC. Stream video over WiFi with low latency and control camera features directly from your web browser.

![PocketCam Logo](android/app/src/main/res/drawable/pocketcam_logo.png)

## Features

*   **High Quality Streaming**: Stream video in MJPEG format over WiFi.
*   **Web Control Panel**: Control your camera remotely via any web browser.
    *   **Switch Camera**: Toggle between Front and Back cameras.
    *   **Zoom Control**: Smooth digital zoom slider.
    *   **Flash Toggle**: Turn the torch on/off for better lighting.
    *   **Auto Focus**: Trigger auto-focus with a single click.
    *   **Quality Adjustment**: Optimize for speed or quality.
*   **Clean Interface**: Minimalist Android app design with a dark theme.
*   **Universal Compatibility**: Works with OBS Studio, Zoom, and any browser.

## Installation

### Prerequisites
*   Android Studio
*   Java/Kotlin Development Kit (JDK 17+)

### Building from Source
1.  Clone this repository.
2.  Open the project in **Android Studio**.
3.  Sync Gradle files to download dependencies.
4.  Connect your Android device (Enable USB Debugging).
5.  Click **Run 'app'** (`Shift + F10`) to install.

## Usage Guide

1.  **Connect WiFi**: Ensure your Phone and PC are connected to the **SAME** WiFi network.
2.  **Start App**: Open **PocketCam** on your phone.
3.  **Start Server**: Tap the **Start** button.
4.  **Get IP**: Note the **WiFi IP** and **Port** displayed on the app screen (e.g., `192.168.1.5`).
5.  **Open Browser**: On your PC, open Chrome/Firefox and go to:
    `http://<YOUR_PHONE_IP>:8080`
6.  **Control**: Use the web dashboard to see the live feed and adjust camera settings.

## Credits

*   **Creator**: Gaurav Sharma
*   **GitHub**: [gsgaurav0](https://github.com/gsgaurav0)

## License

This project is open source.
