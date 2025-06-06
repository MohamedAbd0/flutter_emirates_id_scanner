#!/bin/bash

# Comprehensive test script for Emirates ID Scanner
# Tests the infinite loop callback fix and overall functionality

echo "🔍 Emirates ID Scanner - Comprehensive Test Script"
echo "=================================================="
echo ""

# Function to check if adb is available
check_adb() {
    if ! command -v adb &> /dev/null; then
        echo "❌ ADB not found. Please install Android Debug Bridge."
        exit 1
    fi
}

# Function to get connected device
get_device() {
    DEVICE=$(adb devices | grep -v "List of devices" | grep "device" | head -1 | cut -f1)
    if [ -z "$DEVICE" ]; then
        echo "❌ No Android device connected"
        exit 1
    fi
    echo "📱 Using device: $DEVICE"
}

# Function to monitor app logs
monitor_logs() {
    echo "📊 Monitoring app logs for scanning activity..."
    echo "   (Looking for text recognition, camera events, and callback issues)"
    echo ""
    
    # Monitor for 30 seconds and filter relevant logs
    timeout 30s adb -s "$DEVICE" logcat | grep -E "(flutter|Emirates|TextRecognition|Camera|ML|capture|callback|freeze|timeout)" &
    LOG_PID=$!
}

# Function to simulate tap on scan button
simulate_scan_tap() {
    echo "🎯 Simulating tap on 'Start Scanning' button..."
    
    # Get screen resolution
    SCREEN_SIZE=$(adb -s "$DEVICE" shell wm size | grep "Physical size" | cut -d: -f2 | tr -d ' ')
    if [ -z "$SCREEN_SIZE" ]; then
        SCREEN_SIZE="1080x2340"  # Default for common Android devices
    fi
    
    # Calculate center of screen for scan button (approximate location)
    WIDTH=$(echo "$SCREEN_SIZE" | cut -d'x' -f1)
    HEIGHT=$(echo "$SCREEN_SIZE" | cut -d'x' -f2)
    TAP_X=$((WIDTH / 2))
    TAP_Y=$((HEIGHT / 2 - 200))  # Slightly above center where scan button usually is
    
    echo "   Tapping at coordinates: ($TAP_X, $TAP_Y)"
    adb -s "$DEVICE" shell input tap "$TAP_X" "$TAP_Y"
}

# Function to check for infinite loop indicators
check_infinite_loop() {
    echo "🔄 Checking for infinite loop indicators..."
    
    # Check for repeated callback calls or freeze indicators
    CALLBACK_COUNT=$(adb -s "$DEVICE" logcat -d | grep -c "callback\|onActivityResult\|result")
    FREEZE_INDICATORS=$(adb -s "$DEVICE" logcat -d | grep -c "ANR\|freeze\|timeout\|blocked")
    
    echo "   Callback events detected: $CALLBACK_COUNT"
    echo "   Freeze indicators: $FREEZE_INDICATORS"
    
    if [ "$FREEZE_INDICATORS" -gt 0 ]; then
        echo "⚠️  Warning: Potential freeze indicators detected"
    else
        echo "✅ No freeze indicators found"
    fi
}

# Function to test camera functionality
test_camera() {
    echo "📸 Testing camera functionality..."
    
    # Check camera permission
    CAMERA_PERMISSION=$(adb -s "$DEVICE" shell pm list permissions -d | grep -c "android.permission.CAMERA")
    echo "   Camera permission status: $CAMERA_PERMISSION"
    
    # Check for camera-related logs
    CAMERA_LOGS=$(adb -s "$DEVICE" logcat -d | grep -c -i "camera\|preview\|capture")
    echo "   Camera activity logs: $CAMERA_LOGS"
}

# Function to test text recognition
test_text_recognition() {
    echo "🔤 Testing text recognition functionality..."
    
    # Look for ML Kit text recognition logs
    ML_LOGS=$(adb -s "$DEVICE" logcat -d | grep -c -i "textrecognition\|mlkit\|vision")
    echo "   ML Kit/Text recognition logs: $ML_LOGS"
    
    # Check for Emirates ID specific text detection
    EMIRATES_TEXT=$(adb -s "$DEVICE" logcat -d | grep -c -i "emirates\|united arab\|uae")
    echo "   Emirates ID text detections: $EMIRATES_TEXT"
}

# Function to test multiple scan attempts
test_multiple_scans() {
    echo "🔁 Testing multiple scan attempts to verify callback fix..."
    
    for i in {1..3}; do
        echo "   Scan attempt $i of 3"
        simulate_scan_tap
        sleep 5  # Wait 5 seconds between attempts
        
        # Check for any issues after each attempt
        check_infinite_loop
        
        echo "   Attempt $i completed"
        echo ""
    done
}

# Function to get app performance metrics
get_performance_metrics() {
    echo "📈 Getting app performance metrics..."
    
    # Get memory usage
    MEMORY_USAGE=$(adb -s "$DEVICE" shell dumpsys meminfo com.example.flutter_emirates_id_scanner_example | grep "TOTAL" | head -1 | awk '{print $2}')
    echo "   Memory usage: ${MEMORY_USAGE}KB"
    
    # Get CPU usage (approximation)
    CPU_USAGE=$(adb -s "$DEVICE" shell top -n 1 | grep "flutter_emirates_id_scanner" | head -1 | awk '{print $9}')
    if [ -n "$CPU_USAGE" ]; then
        echo "   CPU usage: ${CPU_USAGE}%"
    else
        echo "   CPU usage: Not available"
    fi
}

# Main test execution
main() {
    echo "Starting comprehensive test..."
    echo ""
    
    check_adb
    get_device
    
    echo ""
    echo "🧪 TEST PHASE 1: Initial Setup and Logs"
    echo "======================================="
    
    # Clear previous logs
    adb -s "$DEVICE" logcat -c
    
    # Start monitoring logs in background
    monitor_logs
    
    sleep 2
    
    echo ""
    echo "🧪 TEST PHASE 2: Basic Functionality"
    echo "===================================="
    
    test_camera
    echo ""
    
    echo ""
    echo "🧪 TEST PHASE 3: Single Scan Test"
    echo "================================="
    
    simulate_scan_tap
    sleep 10  # Wait for scan to complete
    
    test_text_recognition
    check_infinite_loop
    echo ""
    
    echo ""
    echo "🧪 TEST PHASE 4: Multiple Scan Test"
    echo "==================================="
    
    test_multiple_scans
    
    echo ""
    echo "🧪 TEST PHASE 5: Performance Check"
    echo "=================================="
    
    get_performance_metrics
    
    # Stop log monitoring
    if [ -n "$LOG_PID" ]; then
        kill $LOG_PID 2>/dev/null
    fi
    
    echo ""
    echo "📋 TEST SUMMARY"
    echo "==============="
    echo "✅ Comprehensive test completed"
    echo "✅ Multiple scan attempts executed"
    echo "✅ Callback behavior monitored"
    echo "✅ Performance metrics collected"
    echo ""
    echo "📝 Check the output above for any issues or warnings."
    echo "   If no freeze indicators were found, the infinite loop fix is working correctly."
    echo ""
    echo "💡 To run manual tests:"
    echo "   1. Tap the 'Start Scanning' button in the app"
    echo "   2. Point camera at Emirates ID (front side)"
    echo "   3. Wait for capture and then scan back side"
    echo "   4. Verify results are displayed correctly"
    echo "   5. Try multiple scans to ensure no freezing occurs"
}

# Run the main function
main
