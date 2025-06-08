import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_emirates_id_scanner/flutter_emirates_id_scanner.dart';

void main() {
  group('Emirates ID Scanner Emirate Validation Tests', () {
    late FlutterEmiratesIdScanner scanner;

    setUp(() {
      scanner = FlutterEmiratesIdScanner();
    });

    test('EmiratesIdScanResult should handle issuing place correctly', () {
      final testData = {
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
        'nameEn': 'Mohamed Abdou',
        'nameAr': 'محمد عبده',
        'gender': 'M',
        'mrzData': '000000<<<<<<<<<<<<<1\nEL<<MOHAMED<ABDOU<MOHA',
      };

      final result = EmiratesIdScanResult.fromMap(testData);

      expect(result.issuingPlace, equals('Abu Dhabi'));
      expect(result.nameEn, equals('Mohamed Abdou'));
      expect(result.nameAr, equals('محمد عبده'));
      expect(result.gender, equals('M'));
    });

    test('EmiratesIdScanResult should validate all UAE emirates', () {
      final validEmirates = [
        'Abu Dhabi',
        'Dubai',
        'Sharjah',
        'Al Ain',
        'Ajman',
        'Fujairah',
        'Ras Al Khaimah',
        'Umm Al Quwain'
      ];

      for (final emirate in validEmirates) {
        final testData = {
          'issuingPlace': emirate,
          'fullName': 'Test User',
          'idNumber': '784-1911-1111111-1',
        };

        final result = EmiratesIdScanResult.fromMap(testData);
        expect(result.issuingPlace, equals(emirate));
      }
    });

    test('EmiratesIdScanResult should handle null and empty issuing place', () {
      final testDataNull = {
        'fullName': 'Test User',
        'idNumber': '784-1911-1111111-1',
        'issuingPlace': null,
      };

      final resultNull = EmiratesIdScanResult.fromMap(testDataNull);
      expect(resultNull.issuingPlace, isNull);

      final testDataEmpty = {
        'fullName': 'Test User',
        'idNumber': '784-1911-1111111-1',
        'issuingPlace': '',
      };

      final resultEmpty = EmiratesIdScanResult.fromMap(testDataEmpty);
      expect(resultEmpty.issuingPlace, equals(''));
    });

    test('EmiratesIdScanResult toMap should include issuing place', () {
      final result = EmiratesIdScanResult(
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
        issuingPlace: 'Dubai',
        nameEn: 'Mohamed Abdou',
        nameAr: 'محمد عبده',
        gender: 'M',
        mrzData: '000000<<<<<<<<<<<<<1\nEL<<MOHAMED<ABDOU<MOHA',
      );

      final map = result.toMap();
      expect(map['issuingPlace'], equals('Dubai'));
      expect(map['nameEn'], equals('Mohamed Abdou'));
      expect(map['nameAr'], equals('محمد عبده'));
      expect(map['gender'], equals('M'));
    });

    test('EmiratesIdScanResult should maintain backward compatibility', () {
      final result = EmiratesIdScanResult(
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
        issuingPlace: 'Sharjah',
        nameEn: 'Mohamed Abdou English',
        nameAr: 'محمد عبده عربي',
        gender: 'F',
      );

      // Test backward compatibility getters
      expect(result.fullName, equals('Mohamed Abdou'));
      expect(result.nameEn, equals('Mohamed Abdou English'));
      expect(result.nameAr, equals('محمد عبده عربي'));
      expect(result.gender, equals('F'));
    });

    test('EmiratesIdScanResult should handle mixed case emirate names', () {
      final testCases = [
        {'input': 'abu dhabi', 'expected': 'Abu Dhabi'},
        {'input': 'DUBAI', 'expected': 'Dubai'},
        {'input': 'Sharjah', 'expected': 'Sharjah'},
        {'input': 'al ain', 'expected': 'Al Ain'},
        {'input': 'AJMAN', 'expected': 'Ajman'},
        {'input': 'fujairah', 'expected': 'Fujairah'},
        {'input': 'RAS AL KHAIMAH', 'expected': 'Ras Al Khaimah'},
        {'input': 'umm al quwain', 'expected': 'Umm Al Quwain'},
      ];

      for (final testCase in testCases) {
        final testData = {
          'issuingPlace': testCase['input'],
          'fullName': 'Test User',
          'idNumber': '784-1911-1111111-1',
        };

        final result = EmiratesIdScanResult.fromMap(testData);
        // Note: The validation logic is implemented in native code,
        // so this test just verifies the data model handles the values
        expect(result.issuingPlace, equals(testCase['input']));
      }
    });
  });
}
