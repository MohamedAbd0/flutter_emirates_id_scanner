import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'flutter_emirates_id_scanner_platform_interface.dart';
import 'flutter_emirates_id_scanner.dart';

/// An implementation of [FlutterEmiratesIdScannerPlatform] that uses method channels.
class MethodChannelFlutterEmiratesIdScanner
    extends FlutterEmiratesIdScannerPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('flutter_emirates_id_scanner');

  @override
  Future<EmiratesIdScanResult?> scanEmiratesId() async {
    try {
      final result = await methodChannel.invokeMethod<Map<Object?, Object?>>(
        'scanEmiratesId',
      );
      if (result == null) return null;

      // Convert to Map<String, dynamic>
      final Map<String, dynamic> resultMap = Map<String, dynamic>.from(result);
      return EmiratesIdScanResult.fromMap(resultMap);
    } on PlatformException {
      rethrow;
    }
  }
}
