import Flutter
import UIKit

public class FlutterEmiratesIdScannerPlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "flutter_emirates_id_scanner", binaryMessenger: registrar.messenger())
    let instance = FlutterEmiratesIdScannerPlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
    case "scanEmiratesId":
      scanEmiratesId(result: result)
    default:
      result(FlutterMethodNotImplemented)
    }
  }
  
  private func scanEmiratesId(result: @escaping FlutterResult) {
    guard let rootViewController = UIApplication.shared.delegate?.window??.rootViewController else {
      result(FlutterError(code: "NO_ROOT_VIEW_CONTROLLER", message: "Root view controller not found", details: nil))
      return
    }
    
    let scannerViewController = EmiratesIdScannerViewController()
    scannerViewController.modalPresentationStyle = .fullScreen
    
    scannerViewController.onScanComplete = { scanResult in
      switch scanResult {
      case .success(let data):
        result(data)
      case .failure(let error):
        result(FlutterError(code: "SCAN_ERROR", message: error.localizedDescription, details: nil))
      }
    }
    
    rootViewController.present(scannerViewController, animated: true)
  }
}
