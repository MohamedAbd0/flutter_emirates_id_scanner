#!/bin/bash

# Script to test Emirates ID Scanner with enhanced logging using FVM

# Set up variables
PROJECT_DIR="/Users/mohamedabdo/Desktop/MohamedAbdo/flutter_emirates_id_scanner/example"
APK_PATH="$PROJECT_DIR/build/app/outputs/flutter-apk/app-debug.apk"

echo "🔧 Emirates ID Scanner Test Script (FVM)"
echo "========================================"

# Check if FVM is available
if ! command -v fvm &> /dev/null; then
    echo "❌ FVM not found. Please install FVM first."
    exit 1
fi

echo "✅ FVM is available"

# Navigate to project directory
cd "$PROJECT_DIR"

# Check if .fvm directory exists
if [ ! -d ".fvm" ]; then
    echo "⚠️  FVM not configured for this project. Setting up Flutter 3.29.0..."
    fvm use 3.29.0
fi

echo "📍 Using Flutter version:"
fvm flutter --version

# Check if APK exists
if [ ! -f "$APK_PATH" ]; then
    echo "❌ APK not found at: $APK_PATH"
    echo "🔨 Building APK with FVM Flutter..."
    fvm flutter clean
    fvm flutter pub get
    fvm flutter build apk --debug
    
    if [ ! -f "$APK_PATH" ]; then
        echo "❌ Failed to build APK"
        exit 1
    fi
fi

echo "✅ APK found: $APK_PATH"

# Check for connected devices
DEVICES=$(adb devices -l | grep -v "List of devices" | grep device)
if [ -z "$DEVICES" ]; then
    echo "❌ No Android devices found"
    echo "Please connect an Android device and enable USB debugging"
    exit 1
fi

echo "📱 Connected devices:"
echo "$DEVICES"

# Install the APK
echo ""
echo "📦 Installing APK..."
adb install -r "$APK_PATH"

if [ $? -eq 0 ]; then
    echo "✅ APK installed successfully"
else
    echo "❌ Failed to install APK"
    exit 1
fi

# Start the app
echo ""
echo "🚀 Starting Emirates ID Scanner..."
adb shell am start -n com.example.flutter_emirates_id_scanner_example/com.example.flutter_emirates_id_scanner.EmiratesIdScannerActivity

# Monitor logs
echo ""
echo "📋 Monitoring logs (Press Ctrl+C to stop):"
echo "Look for these key log messages:"
echo "  - 'Original image size: X x Y' - Shows captured image dimensions"
echo "  - 'Preview size: X x Y' - Shows preview/overlay dimensions"
echo "  - 'Card rectangle: ...' - Shows calculated ID card bounds"
echo "  - 'Scale: x=..., y=...' - Shows coordinate mapping scale factors"
echo "  - 'Crop bounds: ...' - Shows final crop coordinates"
echo "  - 'Cropped image saved: ...' - Confirms successful cropping"
echo ""
echo "----------------------------------------"

adb logcat -s EmiratesIdScanner | while read line; do
    if [[ $line == *"Original image size"* ]] || 
       [[ $line == *"Preview size"* ]] || 
       [[ $line == *"Card rectangle"* ]] || 
       [[ $line == *"Scale:"* ]] || 
       [[ $line == *"Crop bounds"* ]] || 
       [[ $line == *"Cropped image saved"* ]] || 
       [[ $line == *"Failed to crop"* ]] || 
       [[ $line == *"Invalid crop"* ]]; then
        echo "🔍 $line"
    fi
done
