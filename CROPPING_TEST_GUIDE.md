# Emirates ID Scanner - Enhanced Cropping Test Guide

## Overview

This guide helps you test the enhanced image cropping functionality with the new semi-black overlay and precise cropping within the scanning rectangle.

## Enhanced Features ✨

### 1. **Enhanced Overlay View**

- **Darker Semi-Black Overlay**: 70% opacity black overlay outside scanning area (was 50%)
- **Larger Scanning Rectangle**: 85% of screen width (was 80%) for better visibility
- **Green Corner Guides**: Bright green corner indicators for better alignment
- **Center Alignment Guides**: Dashed lines showing horizontal and vertical center
- **Improved Visual Contrast**: Better distinction between scanning area and overlay

### 2. **Precise Image Cropping**

- **Accurate Coordinate Mapping**: Fixed transformation from preview coordinates to image coordinates
- **Aspect Ratio Handling**: Proper handling of different camera resolutions and preview aspect ratios
- **Exact Rectangle Cropping**: Images are cropped to contain ONLY the scanning rectangle area
- **Enhanced Logging**: Detailed logging for debugging crop calculations

## Testing Instructions 🧪

### Prerequisites

- Android device with USB debugging enabled
- ADB installed and device connected
- FVM (Flutter Version Manager) installed

### Step 1: Run the Test Script

```bash
# Navigate to the project directory
cd /Users/mohamedabdo/Desktop/MohamedAbdo/flutter_emirates_id_scanner

# Run the enhanced test script
./test_cropping.sh
```

### Step 2: Verify Visual Enhancements

When the scanner opens, verify:

1. **Overlay Appearance**:

   - [ ] Semi-black overlay is darker (70% opacity)
   - [ ] Scanning rectangle is larger and more visible
   - [ ] Green corner guides are clearly visible
   - [ ] Center alignment guides help with positioning

2. **Scanning Rectangle**:
   - [ ] White frame clearly defines the scanning area
   - [ ] Green corners indicate proper ID card alignment
   - [ ] Emirates ID card fits well within the rectangle
   - [ ] Rectangle maintains proper 0.63 aspect ratio

### Step 3: Test Image Cropping

1. **Position Emirates ID**: Place the Emirates ID within the scanning rectangle
2. **Scan Front Side**: Watch for "جيد" (Good) message and automatic capture
3. **Check Logs**: Monitor the terminal for cropping logs:

   ```
   🔍 Original image size: 4000 x 3000
   🔍 Preview size: 1080 x 2400
   🔍 Card rectangle: left=135.0, top=1020.0, width=810.0, height=510.3
   🔍 Scale: x=3.7, y=3.7, Offset: x=0.0, y=555.0
   🔍 Crop bounds: left=499, top=4329, width=2997, height=1888
   🔍 Cropped image saved: /path/to/image (810 x 510)
   ```

4. **Scan Back Side**: Repeat for the back side
5. **Verify Results**: Check the final images in the Flutter app

### Step 4: Verify Cropped Images

In the Flutter app results:

- [ ] Front image shows ONLY the Emirates ID card (no background)
- [ ] Back image shows ONLY the Emirates ID card (no background)
- [ ] Images maintain proper aspect ratio
- [ ] Text is clearly readable
- [ ] No distortion or stretching

## Log Messages to Monitor 📋

### Success Indicators ✅

- `Original image size: X x Y` - Shows captured image dimensions
- `Preview size: X x Y` - Shows preview/overlay dimensions
- `Card rectangle: ...` - Shows calculated ID card bounds
- `Scale: x=..., y=...` - Shows coordinate mapping scale factors
- `Crop bounds: ...` - Shows final crop coordinates
- `Cropped image saved: ...` - Confirms successful cropping

### Error Indicators ❌

- `Failed to decode image` - Image loading failed
- `Invalid crop dimensions` - Crop calculation error
- `Crop bounds exceed image bounds` - Coordinate mapping error
- `Failed to crop image` - General cropping failure

## Troubleshooting 🔧

### Issue: Cropped Image Too Large/Small

**Solution**: Check scale calculations in logs. The scale factors should properly map preview coordinates to image coordinates.

### Issue: Cropped Image Shows Background

**Solution**: Verify the scanning rectangle bounds and ensure proper coordinate transformation.

### Issue: App Crashes During Cropping

**Solution**: Check for memory issues with large images. The implementation includes proper bitmap recycling.

### Issue: Duplicate Detection Not Working

**Solution**: Verify MD5 hash calculations are working correctly in logs.

## Technical Details 🔧

### Android Implementation

- File: `android/src/main/kotlin/.../EmiratesIdScannerActivity.kt`
- Key Function: `cropImageToRectangle()`
- Overlay: `createOverlayView()`

### iOS Implementation

- File: `ios/Classes/EmiratesIdScannerViewController.swift`
- Key Function: `cropImageToRectangle()`
- Overlay: `CardOverlayView.draw()`

### Coordinate Transformation

```kotlin
// Map preview coordinates to image coordinates
val scaleX = if (imageAspectRatio > previewAspectRatio) {
    originalBitmap.height.toFloat() / previewHeight
} else {
    originalBitmap.width.toFloat() / previewWidth
}

val cropLeft = (rectLeft * scaleX + offsetX).toInt()
val cropTop = (rectTop * scaleY + offsetY).toInt()
```

## Expected Results ✅

After successful testing, you should have:

1. **Enhanced Visual Experience**: Darker overlay with better contrast
2. **Precise Cropping**: Images containing only the Emirates ID card
3. **Improved Usability**: Larger scanning area with better alignment guides
4. **Reliable Duplicate Detection**: Prevention of scanning the same side twice
5. **High-Quality Images**: Clear, properly cropped Emirates ID images

## Support

If you encounter issues:

1. Check the ADB logs for detailed error messages
2. Verify device compatibility and camera permissions
3. Ensure proper lighting and Emirates ID positioning
4. Test with different Emirates ID cards to verify consistency
