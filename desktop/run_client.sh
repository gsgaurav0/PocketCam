#!/bin/bash
set -e

# Directory of this script
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
VENV_DIR="$DIR/venv"

# check if ip provided
if [ -z "$1" ]; then
    echo "Usage: ./run_client.sh <ANDROID_IP> [PORT]"
    echo "Example: ./run_client.sh 192.168.1.50"
    exit 1
fi

IP=$1
PORT=${2:-8081}
DEVICE="/dev/video10"

# 1. Setup Python Venv if not exists
if [ ! -d "$VENV_DIR" ]; then
    echo "Creating Python Virtual Environment..."
    python3 -m venv "$VENV_DIR"
    echo "Installing dependencies..."
    "$VENV_DIR/bin/pip" install -r "$DIR/requirements.txt"
fi

# 2. Check Virtual Camera
if [ ! -e "$DEVICE" ]; then
    echo "Error: $DEVICE not found!"
    echo "Please run: sudo ./desktop/setup_v4l2.sh"
    exit 1
fi

# 3. Run Receiver
echo "Starting Receiver connecting to $IP:$PORT -> $DEVICE"
"$VENV_DIR/bin/python" "$DIR/receiver.py" "$IP" "$PORT" "$DEVICE"
