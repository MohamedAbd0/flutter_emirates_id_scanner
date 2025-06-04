import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_emirates_id_scanner/flutter_emirates_id_scanner.dart';
import 'package:flutter_emirates_id_scanner/flutter_emirates_id_scanner_platform_interface.dart';
import 'package:flutter_emirates_id_scanner/flutter_emirates_id_scanner_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockFlutterEmiratesIdScannerPlatform
    with MockPlatformInterfaceMixin
    implements FlutterEmiratesIdScannerPlatform {
  @override
  Future<EmiratesIdScanResult?> scanEmiratesId() => Future.value(
    EmiratesIdScanResult(
      fullName: 'John Doe',
      idNumber: '123-4567-1234567-1',
      nationality: 'UAE',
      dateOfBirth: '01/01/1990',
      issueDate: '01/01/2020',
      expiryDate: '01/01/2030',
      frontImagePath: '/path/to/front.jpg',
      backImagePath: '/path/to/back.jpg',
    ),
  );
}

void main() {
  final FlutterEmiratesIdScannerPlatform initialPlatform =
      FlutterEmiratesIdScannerPlatform.instance;

  test('$MethodChannelFlutterEmiratesIdScanner is the default instance', () {
    expect(
      initialPlatform,
      isInstanceOf<MethodChannelFlutterEmiratesIdScanner>(),
    );
  });

  test('scanEmiratesId', () async {
    FlutterEmiratesIdScanner flutterEmiratesIdScannerPlugin =
        FlutterEmiratesIdScanner();
    MockFlutterEmiratesIdScannerPlatform fakePlatform =
        MockFlutterEmiratesIdScannerPlatform();
    FlutterEmiratesIdScannerPlatform.instance = fakePlatform;

    final result = await flutterEmiratesIdScannerPlugin.scanEmiratesId();
    expect(result?.fullName, 'John Doe');
    expect(result?.idNumber, '123-4567-1234567-1');
    expect(result?.nationality, 'UAE');
  });

  test('EmiratesIdScanResult fromMap and toMap', () {
    final map = {
      'fullName': 'John Doe',
      'idNumber': '123-4567-1234567-1',
      'nationality': 'UAE',
      'dateOfBirth': '01/01/1990',
      'issueDate': '01/01/2020',
      'expiryDate': '01/01/2030',
      'frontImagePath': '/path/to/front.jpg',
      'backImagePath': '/path/to/back.jpg',
    };

    final result = EmiratesIdScanResult.fromMap(map);
    expect(result.fullName, 'John Doe');
    expect(result.idNumber, '123-4567-1234567-1');

    final resultMap = result.toMap();
    expect(resultMap, equals(map));
  });
}
