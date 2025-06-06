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
          fullName: 'Mohamed Abdou',
          idNumber: '784-1911-1111111-1',
          nationality: 'Egypt',
          dateOfBirth: '11/11/1911',
          issueDate: '11/12/2023',
          expiryDate: '11/12/2025',
          frontImagePath: '/path/to/front.jpg',
          backImagePath: '/path/to/back.jpg',
          cardNumber: '130000000',
          occupation: 'Software Developer',
          employer: 'Dscale',
          issuingPlace: 'Abu Dhabi',
          mrzData: '000000<<<<<<<<<<<<<1\nEL<<MOHAMED<ABDOU<MOHA',
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
    expect(result?.fullName, 'Mohamed Abdou');
    expect(result?.idNumber, '784-1911-1111111-1');
    expect(result?.nationality, 'Egypt');
    expect(result?.dateOfBirth, '11/11/1911');
    expect(result?.issueDate, '11/12/2023');
    expect(result?.expiryDate, '11/12/2025');
    expect(result?.cardNumber, '130000000');
    expect(result?.occupation, 'Software Developer');
    expect(result?.employer, 'Dscale');
    expect(result?.issuingPlace, 'Abu Dhabi');
  });

  test('EmiratesIdScanResult fromMap and toMap', () {
    final map = {
      'fullName': 'Mohamed Abdou',
      'idNumber': '784-1911-1111111-1',
      'nationality': 'Egypt',
      'dateOfBirth': '11/11/1911',
      'issueDate': '11/12/2023',
      'expiryDate': '11/12/2025',
      'frontImagePath': '/path/to/front.jpg',
      'backImagePath': '/path/to/back.jpg',
      'cardNumber': '130000000',
      'occupation': 'Software Developer',
      'employer': 'Dscale',
      'issuingPlace': 'Abu Dhabi',
      'mrzData': '000000000000<<<<<<<<<<1\n<<MOHAMED<ABDOU',
    };

    final result = EmiratesIdScanResult.fromMap(map);
    expect(result.fullName, 'Mohamed Abdou');
    expect(result.idNumber, '784-1911-1111111-1');
    expect(result.occupation, 'Software Developer');
    expect(result.cardNumber, '130000000');

    final resultMap = result.toMap();
    expect(resultMap, equals(map));
  });
}
