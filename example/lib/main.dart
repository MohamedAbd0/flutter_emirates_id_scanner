import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
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
      home: const EmiratesIdScannerScreen(),
      debugShowCheckedModeBanner: false,
    );
  }
}

class EmiratesIdScannerScreen extends StatefulWidget {
  const EmiratesIdScannerScreen({super.key});

  @override
  State<EmiratesIdScannerScreen> createState() =>
      _EmiratesIdScannerScreenState();
}

class _EmiratesIdScannerScreenState extends State<EmiratesIdScannerScreen> {
  final _flutterEmiratesIdScannerPlugin = FlutterEmiratesIdScanner();
  EmiratesIdScanResult? _scanResult;
  bool _isScanning = false;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Emirates ID Scanner Demo'),
          backgroundColor: Colors.teal,
          foregroundColor: Colors.white,
        ),
        body: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            children: [
              // Header Card
              Card(
                elevation: 4,
                child: Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Column(
                    children: [
                      const Icon(
                        Icons.credit_card,
                        size: 48,
                        color: Colors.teal,
                      ),
                      const SizedBox(height: 8),
                      Text(
                        'Emirates ID Scanner',
                        style: Theme.of(
                          context,
                        ).textTheme.headlineSmall?.copyWith(
                              fontWeight: FontWeight.bold,
                              color: Colors.teal,
                            ),
                      ),
                      const SizedBox(height: 8),
                      const Text(
                        'Scan both sides of your Emirates ID card to extract information automatically',
                        textAlign: TextAlign.center,
                        style: TextStyle(color: Colors.grey),
                      ),
                    ],
                  ),
                ),
              ),

              const SizedBox(height: 24),

              // Scan Button
              SizedBox(
                width: double.infinity,
                height: 56,
                child: ElevatedButton.icon(
                  onPressed: _isScanning ? null : _scanEmiratesId,
                  icon: _isScanning
                      ? const SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.camera_alt),
                  label: Text(_isScanning ? 'Scanning...' : 'Start Scanning'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.teal,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                ),
              ),

              const SizedBox(height: 24),

              // Results Section
              if (_scanResult != null) ...[
                Text(
                  'Scan Results',
                  style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
                ),
                const SizedBox(height: 16),
                Expanded(
                  child: SingleChildScrollView(
                    child: Card(
                      elevation: 2,
                      child: Padding(
                        padding: const EdgeInsets.all(16.0),
                        child: Column(
                          children: [
                            _buildInfoRow('Full Name', _scanResult!.fullName),
                            _buildInfoRow('ID Number', _scanResult!.idNumber),
                            _buildInfoRow(
                              'Nationality',
                              _scanResult!.nationality,
                            ),
                            _buildInfoRow(
                              'Date of Birth',
                              _scanResult!.dateOfBirth,
                            ),
                            _buildInfoRow('Issue Date', _scanResult!.issueDate),
                            _buildInfoRow(
                              'Expiry Date',
                              _scanResult!.expiryDate,
                            ),
                            const Divider(),
                            _buildInfoRow(
                              'Front Image',
                              _scanResult!.frontImagePath,
                              isPath: true,
                            ),
                            if (_scanResult?.frontImagePath != null)
                              Padding(
                                padding: const EdgeInsets.only(top: 8.0),
                                child: Image.file(
                                  File(_scanResult!.frontImagePath!),
                                ),
                              ),
                            const SizedBox(height: 8),
                            _buildInfoRow(
                              'Back Image',
                              _scanResult!.backImagePath,
                              isPath: true,
                            ),
                            if (_scanResult?.backImagePath != null)
                              Padding(
                                padding: const EdgeInsets.only(top: 8.0),
                                child: Image.file(
                                  File(_scanResult!.backImagePath!),
                                ),
                              ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              ] else
                Expanded(
                  child: Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(
                          Icons.document_scanner_outlined,
                          size: 64,
                          color: Colors.grey[400],
                        ),
                        const SizedBox(height: 16),
                        Text(
                          'No scan results yet',
                          style: TextStyle(
                            fontSize: 18,
                            color: Colors.grey[600],
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          'Tap the button above to start scanning',
                          style: TextStyle(color: Colors.grey[500]),
                        ),
                      ],
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildInfoRow(String label, String? value, {bool isPath = false}) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8.0),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 120,
            child: Text(
              label,
              style: const TextStyle(
                fontWeight: FontWeight.bold,
                color: Colors.teal,
              ),
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Text(
              value ?? 'Not found',
              style: TextStyle(
                color: value != null ? Colors.black87 : Colors.grey,
                fontStyle: value != null ? FontStyle.normal : FontStyle.italic,
                fontSize: isPath ? 12 : 14,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _scanEmiratesId() async {
    setState(() {
      _isScanning = true;
      _scanResult = null;
    });

    try {
      final result = await _flutterEmiratesIdScannerPlugin.scanEmiratesId();
      setState(() {
        _scanResult = result;
      });

      if (result != null) {
        _showSuccessSnackBar();
      }
    } on PlatformException catch (e) {
      // Don't show error message, just show guideline for next step
      _showGuidanceMessage('Please scan the back side of your Emirates ID');
      debugPrint(
          'Platform exception: ${e.message ?? 'Unknown error occurred'}');
    } catch (e) {
      // Show gentle guidance instead of error
      _showGuidanceMessage(
          'Please try scanning again or scan the back side of your Emirates ID');
      debugPrint('General exception: Failed to scan Emirates ID: $e');
    } finally {
      setState(() {
        _isScanning = false;
      });
    }
  }

  void _showGuidanceMessage(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Row(
          children: [
            const Icon(Icons.info_outline, color: Colors.white),
            const SizedBox(width: 8),
            Expanded(child: Text(message)),
          ],
        ),
        backgroundColor: Colors.blue,
        duration: const Duration(seconds: 4),
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  void _showSuccessSnackBar() {
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Row(
          children: [
            Icon(Icons.check_circle, color: Colors.white),
            SizedBox(width: 8),
            Text('Emirates ID scanned successfully!'),
          ],
        ),
        backgroundColor: Colors.green,
        duration: Duration(seconds: 3),
      ),
    );
  }
}
