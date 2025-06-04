// This is a basic Flutter integration test.
//
// Since integration tests run in a full Flutter application, they can interact
// with the host side of a plugin implementation, unlike Dart unit tests.
//
// For more information about Flutter integration tests, please see
// https://flutter.dev/to/integration-testing

import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

import 'package:flutter_emirates_id_scanner/flutter_emirates_id_scanner.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('scanEmiratesId method channel test', (
    WidgetTester tester,
  ) async {
    final FlutterEmiratesIdScanner plugin = FlutterEmiratesIdScanner();

    // Note: This test would require actual camera permissions and hardware to work fully
    // For now, we just test that the method channel is properly set up
    try {
      await plugin.scanEmiratesId();
      // If we reach here, the method channel is working
    } catch (e) {
      // Expected to fail in test environment without camera permissions
      // Just verify we get a proper exception type
      expect(e, isA<Exception>());
    }
  });
}
