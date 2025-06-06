import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_emirates_id_scanner/flutter_emirates_id_scanner_method_channel.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  MethodChannelFlutterEmiratesIdScanner platform =
      MethodChannelFlutterEmiratesIdScanner();
  const MethodChannel channel = MethodChannel('flutter_emirates_id_scanner');

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (MethodCall methodCall) async {
      if (methodCall.method == 'scanEmiratesId') {
        return {
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
      }
      return null;
    });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  test('scanEmiratesId', () async {
    final result = await platform.scanEmiratesId();
    expect(result?.fullName, 'Mohamed Abdou');
    expect(result?.idNumber, '784-1911-1111111-1');
    expect(result?.cardNumber, '130000000');
    expect(result?.occupation, 'Software Developer');
    expect(result?.issuingPlace, 'Abu Dhabi');
  });
}
