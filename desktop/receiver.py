import cv2
import socket
import av
import numpy as np
import sys
import pyfakewebcam
import time

def main():
    if len(sys.argv) < 3:
        print("Usage: python receiver.py <ANDROID_IP> <PORT> [VIRTUAL_DEVICE_PATH]")
        print("Example: python receiver.py 192.168.1.50 8081 /dev/video4")
        return

    ip = sys.argv[1]
    port = int(sys.argv[2])
    v4l2_path = sys.argv[3] if len(sys.argv) > 3 else None

    # Connect to Android
    print(f"Connecting to {ip}:{port}...")
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect((ip, port))
        print("Connected!")
    except Exception as e:
        print(f"Connection failed: {e}")
        return

    # Setup Virtual Webcam
    fake_cam = None
    if v4l2_path:
        try:
            # Assume 720p for now, this should match Android sender
            fake_cam = pyfakewebcam.FakeWebcam(v4l2_path, 1280, 720)
            print(f"Virtual camera opened on {v4l2_path}")
        except Exception as e:
            print(f"Could not open virtual camera: {e}")
            print("Make sure v4l2loopback is loaded: 'sudo modprobe v4l2loopback video_nr=4 card_label=\"WebDro\"'")

    # Use PyAV to decode stream directly from socket-like object
    try:
        container = av.open(sock.makefile('rb'), format='h264') # forcing format is important
    except Exception as e:
        print(f"Failed to open stream container: {e}")
        return

    print("Starting stream decoding...")

    try:
        for frame in container.decode(video=0):
            # frame is an av.VideoFrame
            
            # Convert to numpy for OpenCV (BGR)
            img = frame.to_ndarray(format='bgr24')
            
            # Display
            cv2.imshow("WebDro Receiver", img)
            
            # Output to Virtual Camera (RGB)
            if fake_cam:
                # pyfakewebcam expects RGB
                img_rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
                fake_cam.schedule_frame(img_rgb)
            
            if cv2.waitKey(1) & 0xFF == ord('q'):
                break
                
    except Exception as e:
        print(f"Stream error: {e}")
    finally:
        sock.close()
        cv2.destroyAllWindows()

if __name__ == "__main__":
    main()
