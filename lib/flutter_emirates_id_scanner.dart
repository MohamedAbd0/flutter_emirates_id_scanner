import 'flutter_emirates_id_scanner_platform_interface.dart';

/// Result of Emirates ID scanning containing extracted data and image paths
class EmiratesIdScanResult {
  final String? fullName; // English name (backward compatibility)
  final String? nameEn; // English name (new field)
  final String? nameAr; // Arabic name (new field)
  final String? idNumber;
  final String? nationality;
  final String? dateOfBirth;
  final String? issueDate;
  final String? expiryDate;
  final String? gender; // New field for gender (M/F)
  final String? frontImagePath;
  final String? backImagePath;

  // Additional fields from back side
  final String? cardNumber;
  final String? occupation;
  final String? employer;
  final String? issuingPlace;
  final String? mrzData;

  EmiratesIdScanResult({
    this.fullName,
    this.nameEn,
    this.nameAr,
    this.idNumber,
    this.nationality,
    this.dateOfBirth,
    this.issueDate,
    this.expiryDate,
    this.gender,
    this.frontImagePath,
    this.backImagePath,
    this.cardNumber,
    this.occupation,
    this.employer,
    this.issuingPlace,
    this.mrzData,
  });

  factory EmiratesIdScanResult.fromMap(Map<String, dynamic> map) {
    return EmiratesIdScanResult(
      fullName: map['fullName'] as String?,
      nameEn: map['nameEn'] as String?,
      nameAr: map['nameAr'] as String?,
      idNumber: map['idNumber'] as String?,
      nationality: map['nationality'] as String?,
      dateOfBirth: map['dateOfBirth'] as String?,
      issueDate: map['issueDate'] as String?,
      expiryDate: map['expiryDate'] as String?,
      gender: map['gender'] as String?,
      frontImagePath: map['frontImagePath'] as String?,
      backImagePath: map['backImagePath'] as String?,
      cardNumber: map['cardNumber'] as String?,
      occupation: map['occupation'] as String?,
      employer: map['employer'] as String?,
      issuingPlace: map['issuingPlace'] as String?,
      mrzData: map['mrzData'] as String?,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'fullName': fullName,
      'nameEn': nameEn,
      'nameAr': nameAr,
      'idNumber': idNumber,
      'nationality': nationality,
      'dateOfBirth': dateOfBirth,
      'issueDate': issueDate,
      'expiryDate': expiryDate,
      'gender': gender,
      'frontImagePath': frontImagePath,
      'backImagePath': backImagePath,
      'cardNumber': cardNumber,
      'occupation': occupation,
      'employer': employer,
      'issuingPlace': issuingPlace,
      'mrzData': mrzData,
    };
  }

  @override
  String toString() {
    return 'EmiratesIdScanResult('
        'fullName: $fullName, '
        'nameEn: $nameEn, '
        'nameAr: $nameAr, '
        'idNumber: $idNumber, '
        'nationality: $nationality, '
        'dateOfBirth: $dateOfBirth, '
        'issueDate: $issueDate, '
        'expiryDate: $expiryDate, '
        'gender: $gender, '
        'frontImagePath: $frontImagePath, '
        'backImagePath: $backImagePath, '
        'cardNumber: $cardNumber, '
        'occupation: $occupation, '
        'employer: $employer, '
        'issuingPlace: $issuingPlace'
        ')';
  }

  // Backward compatibility getters
  String? get fullNameArabic => nameAr;
  String? get fullNameEnglish => nameEn ?? fullName;
}

class FlutterEmiratesIdScanner {
  /// Starts the Emirates ID scanning process
  ///
  /// Returns [EmiratesIdScanResult] with extracted data and image paths
  /// Throws [PlatformException] if scanning fails or is cancelled
  Future<EmiratesIdScanResult?> scanEmiratesId() {
    return FlutterEmiratesIdScannerPlatform.instance.scanEmiratesId();
  }
}
