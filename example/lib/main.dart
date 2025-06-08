import 'dart:developer';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_emirates_id_scanner/flutter_emirates_id_scanner.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Emirates ID Scanner',
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF00838F), // Teal color
          brightness: Brightness.light,
        ),
        appBarTheme: const AppBarTheme(
          centerTitle: true,
          elevation: 0,
        ),
        cardTheme: CardTheme(
          elevation: 3,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
        ),
        elevatedButtonTheme: ElevatedButtonThemeData(
          style: ElevatedButton.styleFrom(
            padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 16),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(12),
            ),
            elevation: 2,
          ),
        ),
      ),
      home: EnhancedEmiratesId(),
      debugShowCheckedModeBanner: false,
    );
  }
}

class EnhancedEmiratesId extends StatefulWidget {
  const EnhancedEmiratesId({Key? key}) : super(key: key);

  @override
  State<EnhancedEmiratesId> createState() => _EnhancedEmiratesIdState();
}

class _EnhancedEmiratesIdState extends State<EnhancedEmiratesId> {
  EmiratesIdScanResult? _scanResult;
  bool _isScanning = false;

  Future<void> _startScan() async {
    setState(() => _isScanning = true);

    try {
      final scanner = FlutterEmiratesIdScanner();
      final result = await scanner.scanEmiratesId();

      print('Scan result: ${result?.toMap()}');

      setState(() {
        _scanResult = result;
        _isScanning = false;
      });
    } catch (e) {
      setState(() => _isScanning = false);

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Scan failed: $e')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Enhanced Emirates ID Scanner'),
        backgroundColor: Colors.blue[700],
        foregroundColor: Colors.white,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Scan Button
            ElevatedButton.icon(
              onPressed: _isScanning ? null : _startScan,
              icon: _isScanning
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.camera_alt),
              label: Text(_isScanning ? 'Scanning...' : 'Scan Emirates ID'),
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.all(16),
                textStyle: const TextStyle(fontSize: 18),
              ),
            ),

            const SizedBox(height: 24),

            // Results Section
            if (_scanResult != null) ...[
              _buildResultCard(
                title: 'Front Side Data',
                icon: Icons.credit_card,
                color: Colors.blue,
                children: [
                  _buildDataRow('ID Number', _scanResult!.idNumber),
                  _buildDataRow('Name (English)', _scanResult!.fullName),
                  _buildDataRow('Name (Arabic)', _scanResult!.fullNameArabic),
                  _buildDataRow('Nationality', _scanResult!.nationality),
                  _buildDataRow('Date of Birth', _scanResult!.dateOfBirth),
                  _buildDataRow('Issue Date', _scanResult!.issueDate),
                  _buildDataRow('Expiry Date', _scanResult!.expiryDate),
                  _buildDataRow('Gender', _scanResult!.gender),
                ],
              ),
              const SizedBox(height: 16),
              _buildResultCard(
                title: 'Back Side Data',
                icon: Icons.badge,
                color: Colors.green,
                children: [
                  _buildDataRow('Card Number', _scanResult!.cardNumber),
                  _buildDataRow('Occupation', _scanResult!.occupation),
                  _buildDataRow('Employer', _scanResult!.employer),
                  _buildDataRow('Issuing Place', _scanResult!.issuingPlace),
                ],
              ),
              const SizedBox(height: 16),
              _buildResultCard(
                title: 'Captured Images',
                icon: Icons.image,
                color: Colors.purple,
                children: [
                  _buildDataRow('Front Image', _scanResult!.frontImagePath),
                  _buildDataRow('Back Image', _scanResult!.backImagePath),
                ],
              ),
              if (_scanResult?.frontImagePath != null)
                Image.file(File(_scanResult!.frontImagePath!)),

              SizedBox(height: 16),
              // Display back image if available
              if (_scanResult?.backImagePath != null)
                Image.file(File(_scanResult!.backImagePath!)),
            ],

            // Instructions
            if (_scanResult == null && !_isScanning) ...[
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Icon(Icons.info, color: Colors.blue[700]),
                          const SizedBox(width: 8),
                          const Text(
                            'How to Use',
                            style: TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      const Text(
                        '1. Tap "Scan Emirates ID" button\n'
                        '2. Point camera at Emirates ID front side\n'
                        '3. Wait for automatic capture\n'
                        '4. Flip to back side when prompted\n'
                        '5. View extracted data below',
                        style: TextStyle(fontSize: 16),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildResultCard({
    required String title,
    required IconData icon,
    required Color color,
    required List<Widget> children,
  }) {
    return Card(
      elevation: 4,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(icon, color: color),
                const SizedBox(width: 8),
                Text(
                  title,
                  style: TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                    color: color,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            ...children,
          ],
        ),
      ),
    );
  }

  Widget _buildDataRow(String label, String? value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 120,
            child: Text(
              '$label:',
              style: const TextStyle(
                fontWeight: FontWeight.w500,
                color: Colors.grey,
              ),
            ),
          ),
          Expanded(
            child: Text(
              value?.isNotEmpty == true ? value! : 'Not detected',
              style: TextStyle(
                color: value?.isNotEmpty == true ? Colors.black : Colors.red,
                fontWeight: value?.isNotEmpty == true
                    ? FontWeight.normal
                    : FontWeight.w300,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
