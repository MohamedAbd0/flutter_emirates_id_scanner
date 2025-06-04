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
              'fullName': 'John Doe',
              'idNumber': '123-4567-1234567-1',
              'nationality': 'UAE',
              'dateOfBirth': '01/01/1990',
              'issueDate': '01/01/2020',
              'expiryDate': '01/01/2030',
              'frontImagePath': '/path/to/front.jpg',
              'backImagePath': '/path/to/back.jpg',
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
    expect(result?.fullName, 'John Doe');
    expect(result?.idNumber, '123-4567-1234567-1');
  });
}
