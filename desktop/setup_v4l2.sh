#!/bin/bash
# Helper script to setup v4l2loopback
# Usage: sudo ./setup_v4l2.sh

if [ "$EUID" -ne 0 ]
  then echo "Please run as root"
  exit
fi

# Unload if exists
modprobe -r v4l2loopback

# Load with specific ID (e.g., /dev/video10) and Label
# exclusive_caps=1 is often needed for Chrome/Zoom to recognize it as a webcam
modprobe v4l2loopback video_nr=10 card_label="WebDro Virtual Cam" exclusive_caps=1

echo "Virtual Camera created at /dev/video10"
ls -l /dev/video10
