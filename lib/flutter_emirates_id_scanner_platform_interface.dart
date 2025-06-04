import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'flutter_emirates_id_scanner_method_channel.dart';
import 'flutter_emirates_id_scanner.dart';

abstract class FlutterEmiratesIdScannerPlatform extends PlatformInterface {
  /// Constructs a FlutterEmiratesIdScannerPlatform.
  FlutterEmiratesIdScannerPlatform() : super(token: _token);

  static final Object _token = Object();

  static FlutterEmiratesIdScannerPlatform _instance =
      MethodChannelFlutterEmiratesIdScanner();

  /// The default instance of [FlutterEmiratesIdScannerPlatform] to use.
  ///
  /// Defaults to [MethodChannelFlutterEmiratesIdScanner].
  static FlutterEmiratesIdScannerPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [FlutterEmiratesIdScannerPlatform] when
  /// they register themselves.
  static set instance(FlutterEmiratesIdScannerPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<EmiratesIdScanResult?> scanEmiratesId() {
    throw UnimplementedError('scanEmiratesId() has not been implemented.');
  }
}
