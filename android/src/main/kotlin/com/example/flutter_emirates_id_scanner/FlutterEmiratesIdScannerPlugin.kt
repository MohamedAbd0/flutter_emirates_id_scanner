package com.example.flutter_emirates_id_scanner

import android.app.Activity
import android.content.Intent
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry

/** FlutterEmiratesIdScannerPlugin */
class FlutterEmiratesIdScannerPlugin: FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.ActivityResultListener {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel
  private var activity: Activity? = null
  private var pendingResult: Result? = null
  
  companion object {
    private const val REQUEST_CODE_SCAN = 1001
  }

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_emirates_id_scanner")
    channel.setMethodCallHandler(this)
  }

  override fun onMethodCall(call: MethodCall, result: Result) {
    when (call.method) {
      "scanEmiratesId" -> {
        if (activity == null) {
          result.error("ACTIVITY_NOT_AVAILABLE", "Activity not available", null)
          return
        }
        
        if (pendingResult != null) {
          result.error("SCAN_IN_PROGRESS", "Another scan is already in progress", null)
          return
        }
        
        pendingResult = result
        
        val intent = Intent(activity, EmiratesIdScannerActivity::class.java)
        activity?.startActivityForResult(intent, REQUEST_CODE_SCAN)
      }
      else -> {
        result.notImplemented()
      }
    }
  }
  
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
    if (requestCode == REQUEST_CODE_SCAN) {
      val result = pendingResult
      pendingResult = null
      
      if (result == null) return true
      
      if (resultCode == Activity.RESULT_OK && data != null) {
        val status = data.getStringExtra("status")
        
        when (status) {
          EmiratesIdScannerActivity.RESULT_SUCCESS -> {
            val scanResult = mapOf(
              "fullName" to data.getStringExtra("fullName"),
              "idNumber" to data.getStringExtra("idNumber"),
              "nationality" to data.getStringExtra("nationality"),
              "dateOfBirth" to data.getStringExtra("dateOfBirth"),
              "issueDate" to data.getStringExtra("issueDate"),
              "expiryDate" to data.getStringExtra("expiryDate"),
              "frontImagePath" to data.getStringExtra("frontImagePath"),
              "backImagePath" to data.getStringExtra("backImagePath"),
              "cardNumber" to data.getStringExtra("cardNumber"),
              "occupation" to data.getStringExtra("occupation"),
              "employer" to data.getStringExtra("employer"),
              "issuingPlace" to data.getStringExtra("issuingPlace"),
              "mrzData" to data.getStringExtra("mrzData")
            )
            result.success(scanResult)
          }
          EmiratesIdScannerActivity.RESULT_CANCELLED -> {
            result.error("SCAN_CANCELLED", "User cancelled the scan", null)
          }
          EmiratesIdScannerActivity.RESULT_ERROR -> {
            val error = data.getStringExtra("error") ?: "Unknown error"
            result.error("SCAN_ERROR", error, null)
          }
          else -> {
            result.error("UNKNOWN_ERROR", "Unknown result status", null)
          }
        }
      } else {
        result.error("SCAN_FAILED", "Scan failed or was cancelled", null)
      }
      
      return true
    }
    return false
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }
  
  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
    binding.addActivityResultListener(this)
  }

  override fun onDetachedFromActivityForConfigChanges() {
    activity = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activity = binding.activity
    binding.addActivityResultListener(this)
  }

  override fun onDetachedFromActivity() {
    activity = null
  }
}
