# flutter_emirates_id_scanner

A Flutter plugin for scanning Emirates ID cards with native camera and OCR capabilities on Android and iOS.

## Features

- 📱 **Native Camera Interface**: Full-screen camera view with guided overlay
- 🔍 **Automatic Detection**: Auto-detects and captures both front and back sides
- 🧠 **OCR Processing**: Extracts text data using ML Kit (Android) and Vision (iOS)
- 🌍 **Arabic Support**: Instructions displayed in Arabic for UAE users
- 📸 **Image Storage**: Returns captured images along with extracted data
- ⚡ **Real-time Processing**: Live text recognition during scanning

## Extracted Information

The plugin extracts the following information from Emirates ID cards:

- **Full Name**
- **ID Number** (XXX-XXXX-XXXXXXX-X format)
- **Nationality**
- **Date of Birth**
- **Issue Date**
- **Expiry Date**
- **Front Image Path**
- **Back Image Path**

## Installation

Add this to your package's `pubspec.yaml` file:

```yaml
dependencies:
  flutter_emirates_id_scanner: ^0.0.1
```

## Platform Setup

### Android

Add the following permissions to your `android/app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

### iOS

Add the following to your `ios/Runner/Info.plist`:

```xml
<key>NSCameraUsageDescription</key>
<string>This app needs camera access to scan Emirates ID cards.</string>
```

## Usage

### Basic Implementation

```dart
import 'package:flutter_emirates_id_scanner/flutter_emirates_id_scanner.dart';

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final _scanner = FlutterEmiratesIdScanner();
  EmiratesIdScanResult? _result;

  Future<void> _scanEmiratesId() async {
    try {
      final result = await _scanner.scanEmiratesId();
      setState(() {
        _result = result;
      });

      if (result != null) {
        print('Name: ${result.fullName}');
        print('ID: ${result.idNumber}');
        print('Nationality: ${result.nationality}');
        // Access other fields...
      }
    } on PlatformException catch (e) {
      print('Error: ${e.message}');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Emirates ID Scanner')),
      body: Center(
        child: Column(
          children: [
            ElevatedButton(
              onPressed: _scanEmiratesId,
              child: Text('Scan Emirates ID'),
            ),
            if (_result != null) ...[
              Text('Name: ${_result!.fullName ?? "N/A"}'),
              Text('ID: ${_result!.idNumber ?? "N/A"}'),
              Text('Nationality: ${_result!.nationality ?? "N/A"}'),
              // Display other fields...
            ],
          ],
        ),
      ),
    );
  }
}
```

### Advanced Usage with Error Handling

```dart
Future<void> _scanWithErrorHandling() async {
  try {
    final result = await _scanner.scanEmiratesId();

    if (result != null) {
      // Validate extracted data
      if (result.idNumber != null && result.fullName != null) {
        // Process valid result
        _processValidResult(result);
      } else {
        _showError('Incomplete data extracted. Please try again.');
      }
    }
  } on PlatformException catch (e) {
    switch (e.code) {
      case 'SCAN_CANCELLED':
        _showMessage('Scan cancelled by user');
        break;
      case 'CAMERA_PERMISSION_DENIED':
        _showError('Camera permission is required');
        break;
      case 'SCAN_ERROR':
        _showError('Scanning failed: ${e.message}');
        break;
      default:
        _showError('Unknown error: ${e.message}');
    }
  }
}
```

## Scanning Flow

The plugin follows a guided scanning process:

1. **Front Side Scanning**

   - Display: "قُم بمسح الوجه الأمامي للهوية" (Scan your front ID)
   - Auto-detection of Emirates ID front side
   - Shows "جيد" (Good) when properly aligned
   - Auto-captures the image

2. **Back Side Scanning**

   - Display: "قُم بمسح الوجه الخلفي للهوية" (Scan your back ID)
   - Auto-detection of Emirates ID back side
   - Shows "جيد" (Good) when properly aligned
   - Auto-captures the image

3. **Processing**
   - OCR processing on both images
   - Data extraction and validation
   - Returns results to Flutter

## Data Model

```dart
class EmiratesIdScanResult {
  final String? fullName;
  final String? idNumber;
  final String? nationality;
  final String? dateOfBirth;
  final String? issueDate;
  final String? expiryDate;
  final String? frontImagePath;
  final String? backImagePath;

  // Constructor and methods...
}
```

## Error Handling

The plugin can throw the following `PlatformException` codes:

- `SCAN_CANCELLED`: User cancelled the scanning process
- `SCAN_ERROR`: General scanning error
- `CAMERA_PERMISSION_DENIED`: Camera permission not granted
- `ACTIVITY_NOT_AVAILABLE`: Android activity not available
- `SCAN_IN_PROGRESS`: Another scan is already in progress

## Technical Details

### Android Implementation

- **CameraX**: For camera preview and image capture
- **ML Kit Text Recognition**: For OCR processing
- **Full-screen Activity**: Custom scanner interface
- **Arabic Text Support**: RTL text recognition

### iOS Implementation

- **AVCaptureSession**: For camera functionality
- **Vision Framework**: For text recognition
- **Native UI**: Custom view controller with overlay
- **VNRecognizeTextRequest**: Advanced OCR capabilities

## Minimum Requirements

- **Flutter**: 3.3.0+
- **Dart**: 3.0.0+
- **Android**: API level 21+ (Android 5.0)
- **iOS**: 12.0+

## Permissions

The plugin automatically handles permission requests, but you must declare them in your app manifests as shown in the setup section.

## Contributing

Contributions are welcome! Please read our contributing guidelines and submit pull requests to our repository.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For support, please create an issue on our GitHub repository.ates_id_scanner

A new Flutter plugin project.

## Getting Started

This project is a starting point for a Flutter
[plug-in package](https://flutter.dev/to/develop-plugins),
a specialized package that includes platform-specific implementation code for
Android and/or iOS.

For help getting started with Flutter development, view the
[online documentation](https://docs.flutter.dev), which offers tutorials,
samples, guidance on mobile development, and a full API reference.
