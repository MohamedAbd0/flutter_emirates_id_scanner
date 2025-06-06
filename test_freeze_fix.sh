#!/bin/bash

# Emirates ID Scanner Freeze Fix Test Script
# This script tests the fix for the freezing issue after front image capture

echo "🧪 Emirates ID Scanner Freeze Fix Test"
echo "======================================="

# Check if FVM is available
if ! command -v fvm &> /dev/null; then
    echo "❌ FVM not found. Please install FVM first."
    exit 1
fi

# Change to project directory
cd "$(dirname "$0")"

echo "📍 Current directory: $(pwd)"
echo ""

# Clean and get dependencies
echo "🧹 Cleaning and getting dependencies..."
fvm flutter clean
fvm flutter pub get

cd example
fvm flutter pub get
cd ..

echo ""
echo "🏗️  Building debug APK..."
cd example
fvm flutter build apk --debug

if [ $? -ne 0 ]; then
    echo "❌ Build failed!"
    exit 1
fi

echo ""
echo "✅ Build successful!"
echo ""
echo "📱 Installing on connected device..."

# Check if device is connected
if ! adb devices | grep -q device; then
    echo "❌ No Android device connected. Please connect a device and enable USB debugging."
    exit 1
fi

# Install the APK
adb install -r build/app/outputs/flutter-apk/app-debug.apk

if [ $? -eq 0 ]; then
    echo "✅ Installation successful!"
    echo ""
    echo "🧪 Test Instructions:"
    echo "===================="
    echo "1. Open the Emirates ID Scanner Example app on your device"
    echo "2. Tap 'Scan Emirates ID'"
    echo "3. Point camera at the FRONT side of an Emirates ID"
    echo "4. Wait for 'جيد' (Good) message and automatic capture"
    echo "5. Check if the app transitions smoothly to back side scanning"
    echo "6. The app should NOT freeze during this transition"
    echo "7. Complete the back side scanning"
    echo ""
    echo "✅ Expected Behavior (FIXED):"
    echo "- Smooth transition from front to back scanning"
    echo "- No freezing or hanging"
    echo "- Camera restarts properly for back side"
    echo "- Both front and back images captured successfully"
    echo ""
    echo "❌ Previous Bug (SHOULD BE FIXED):"
    echo "- App would freeze after front image capture"
    echo "- BufferQueueProducer timeout errors"
    echo "- Camera would not restart for back side"
    echo ""
    echo "📊 Monitor Android logs with:"
    echo "adb logcat | grep EmiratesIdScanner"
    echo ""
    echo "🔧 Key Fixes Applied:"
    echo "- Added capture state management to prevent concurrent requests"
    echo "- Implemented proper ImageAnalysis buffer cleanup"
    echo "- Added throttling to reduce OCR processing frequency"
    echo "- Enhanced camera resource management during transitions"
    echo "- Added proper lifecycle management with onResume()"
else
    echo "❌ Installation failed!"
    exit 1
fi

cd ..
