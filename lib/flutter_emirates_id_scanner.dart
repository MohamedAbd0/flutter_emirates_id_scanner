import 'flutter_emirates_id_scanner_platform_interface.dart';

/// Result of Emirates ID scanning containing extracted data and image paths
class EmiratesIdScanResult {
  final String? fullName;
  final String? idNumber;
  final String? nationality;
  final String? dateOfBirth;
  final String? issueDate;
  final String? expiryDate;
  final String? frontImagePath;
  final String? backImagePath;

  EmiratesIdScanResult({
    this.fullName,
    this.idNumber,
    this.nationality,
    this.dateOfBirth,
    this.issueDate,
    this.expiryDate,
    this.frontImagePath,
    this.backImagePath,
  });

  factory EmiratesIdScanResult.fromMap(Map<String, dynamic> map) {
    return EmiratesIdScanResult(
      fullName: map['fullName'] as String?,
      idNumber: map['idNumber'] as String?,
      nationality: map['nationality'] as String?,
      dateOfBirth: map['dateOfBirth'] as String?,
      issueDate: map['issueDate'] as String?,
      expiryDate: map['expiryDate'] as String?,
      frontImagePath: map['frontImagePath'] as String?,
      backImagePath: map['backImagePath'] as String?,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'fullName': fullName,
      'idNumber': idNumber,
      'nationality': nationality,
      'dateOfBirth': dateOfBirth,
      'issueDate': issueDate,
      'expiryDate': expiryDate,
      'frontImagePath': frontImagePath,
      'backImagePath': backImagePath,
    };
  }

  @override
  String toString() {
    return 'EmiratesIdScanResult(fullName: $fullName, idNumber: $idNumber, nationality: $nationality, dateOfBirth: $dateOfBirth, issueDate: $issueDate, expiryDate: $expiryDate, frontImagePath: $frontImagePath, backImagePath: $backImagePath)';
  }
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
