package com.example.flutter_emirates_id_scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class EmiratesIdScannerActivity : AppCompatActivity() {
    
    private lateinit var viewFinder: PreviewView
    private lateinit var overlayView: View
    private lateinit var instructionText: TextView
    private lateinit var closeButton: Button
    private lateinit var cameraExecutor: ExecutorService
    
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    
    private var scanningStep = ScanningStep.FRONT
    private var frontImagePath: String? = null
    private var backImagePath: String? = null
    private var extractedData = mutableMapOf<String, String?>()
    
    enum class ScanningStep {
        FRONT, BACK, COMPLETED
    }
    
    companion object {
        private const val TAG = "EmiratesIdScanner"
        const val RESULT_SUCCESS = "success"
        const val RESULT_CANCELLED = "cancelled"
        const val RESULT_ERROR = "error"
    }
    
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            finishWithError("Camera permission denied")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createLayout())
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        
        updateInstruction()
    }
    
    private fun createLayout(): View {
        val rootLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }
        
        // Camera preview
        viewFinder = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootLayout.addView(viewFinder)
        
        // Overlay view for ID card frame
        overlayView = createOverlayView()
        rootLayout.addView(overlayView)
        
        // Instruction text
        instructionText = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 100
                leftMargin = 40
                rightMargin = 40
            }
            textSize = 18f
            setTextColor(Color.WHITE)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setBackgroundColor(Color.parseColor("#80000000"))
            setPadding(20, 20, 20, 20)
        }
        rootLayout.addView(instructionText)
        
        // Close button
        closeButton = Button(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 50
                rightMargin = 20
                gravity = android.view.Gravity.TOP or android.view.Gravity.END
            }
            text = "✕"
            textSize = 20f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#80000000"))
            setPadding(20, 10, 20, 10)
            setOnClickListener {
                finishWithResult(RESULT_CANCELLED, null)
            }
        }
        rootLayout.addView(closeButton)
        
        return rootLayout
    }
    
    private fun createOverlayView(): View {
        return object : View(this) {
            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                canvas.let { c ->
                    val paint = android.graphics.Paint().apply {
                        color = Color.WHITE
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 4f
                    }
                    
                    // Draw ID card frame
                    val centerX = width / 2f
                    val centerY = height / 2f
                    val cardWidth = width * 0.8f
                    val cardHeight = cardWidth * 0.63f // Emirates ID aspect ratio
                    
                    val left = centerX - cardWidth / 2
                    val top = centerY - cardHeight / 2
                    val right = centerX + cardWidth / 2
                    val bottom = centerY + cardHeight / 2
                    
                    c.drawRect(left, top, right, bottom, paint)
                    
                    // Draw corner guides
                    val cornerLength = 50f
                    paint.strokeWidth = 8f
                    
                    // Top-left corner
                    c.drawLine(left, top, left + cornerLength, top, paint)
                    c.drawLine(left, top, left, top + cornerLength, paint)
                    
                    // Top-right corner
                    c.drawLine(right - cornerLength, top, right, top, paint)
                    c.drawLine(right, top, right, top + cornerLength, paint)
                    
                    // Bottom-left corner
                    c.drawLine(left, bottom - cornerLength, left, bottom, paint)
                    c.drawLine(left, bottom, left + cornerLength, bottom, paint)
                    
                    // Bottom-right corner
                    c.drawLine(right - cornerLength, bottom, right, bottom, paint)
                    c.drawLine(right, bottom - cornerLength, right, bottom, paint)
                }
            }
        }.apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
    }
    
    private fun updateInstruction() {
        val instruction = when (scanningStep) {
            ScanningStep.FRONT -> "قُم بمسح الوجه الأمامي للهوية"
            ScanningStep.BACK -> "قُم بمسح الوجه الخلفي للهوية"
            ScanningStep.COMPLETED -> "جيد"
        }
        instructionText.text = instruction
    }
    
    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }
            
            imageCapture = ImageCapture.Builder().build()
            
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, ImageAnalyzer())
                }
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                finishWithError("Failed to start camera: ${exc.message}")
            }
            
        }, ContextCompat.getMainExecutor(this))
    }
    
    private inner class ImageAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                
                textRecognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        processTextResult(visionText.text)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Text recognition failed", e)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }
    
    private fun processTextResult(text: String) {
        Log.d(TAG, "OCR Text: $text")
        
        val isValidCard = when (scanningStep) {
            ScanningStep.FRONT -> isValidFrontSide(text)
            ScanningStep.BACK -> isValidBackSide(text)
            ScanningStep.COMPLETED -> false
        }
        
        if (isValidCard) {
            runOnUiThread {
                instructionText.text = "جيد"
            }
            
            // Delay capture to allow user to see "Good" message
            lifecycleScope.launch {
                kotlinx.coroutines.delay(1000)
                captureImage()
            }
        }
    }
    
    private fun isValidFrontSide(text: String): Boolean {
        val cleanText = text.replace("\\s+".toRegex(), " ").uppercase()
        return cleanText.contains("EMIRATES") || 
               cleanText.contains("الإمارات") ||
               cleanText.contains("IDENTITY") ||
               cleanText.contains("CARD") ||
               text.contains(Regex("\\d{3}-\\d{4}-\\d{7}-\\d{1}")) // Emirates ID pattern
    }
    
    private fun isValidBackSide(text: String): Boolean {
        val cleanText = text.replace("\\s+".toRegex(), " ").uppercase()
        return cleanText.contains("MINISTRY") ||
               cleanText.contains("INTERIOR") ||
               cleanText.contains("وزارة") ||
               cleanText.contains("الداخلية") ||
               text.contains(Regex("\\d{2}/\\d{2}/\\d{4}")) // Date pattern
    }
    
    private fun captureImage() {
        val imageCapture = imageCapture ?: return
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = when (scanningStep) {
            ScanningStep.FRONT -> "emirates_id_front_$timestamp.jpg"
            ScanningStep.BACK -> "emirates_id_back_$timestamp.jpg"
            ScanningStep.COMPLETED -> return
        }
        
        val outputFile = File(cacheDir, fileName)
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        
        imageCapture.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Image capture failed: ${exception.message}", exception)
                    finishWithError("Failed to capture image: ${exception.message}")
                }
                
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val imagePath = outputFile.absolutePath
                    Log.d(TAG, "Image captured: $imagePath")
                    
                    when (scanningStep) {
                        ScanningStep.FRONT -> {
                            frontImagePath = imagePath
                            scanningStep = ScanningStep.BACK
                            updateInstruction()
                        }
                        ScanningStep.BACK -> {
                            backImagePath = imagePath
                            scanningStep = ScanningStep.COMPLETED
                            processCompleted()
                        }
                        ScanningStep.COMPLETED -> return
                    }
                }
            }
        )
    }
    
    private fun processCompleted() {
        lifecycleScope.launch {
            try {
                // Extract data from both images
                extractDataFromImages()
                
                val result = mapOf(
                    "fullName" to extractedData["fullName"],
                    "idNumber" to extractedData["idNumber"],
                    "nationality" to extractedData["nationality"],
                    "dateOfBirth" to extractedData["dateOfBirth"],
                    "issueDate" to extractedData["issueDate"],
                    "expiryDate" to extractedData["expiryDate"],
                    "frontImagePath" to frontImagePath,
                    "backImagePath" to backImagePath
                )
                
                finishWithResult(RESULT_SUCCESS, result)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process images", e)
                finishWithError("Failed to process captured images: ${e.message}")
            }
        }
    }
    
    private suspend fun extractDataFromImages() {
        frontImagePath?.let { path ->
            val frontText = extractTextFromImage(path)
            extractFrontSideData(frontText)
        }
        
        backImagePath?.let { path ->
            val backText = extractTextFromImage(path)
            extractBackSideData(backText)
        }
    }
    
    private suspend fun extractTextFromImage(imagePath: String): String {
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val file = File(imagePath)
            val image = InputImage.fromFilePath(this, android.net.Uri.fromFile(file))
            
            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText.text) {}
                }
                .addOnFailureListener { e ->
                    continuation.resume("") {}
                }
        }
    }
    
    private fun extractFrontSideData(text: String) {
        val lines = text.split("\n")
        
        // Extract ID Number (pattern: XXX-XXXX-XXXXXXX-X)
        val idPattern = Regex("(\\d{3}-\\d{4}-\\d{7}-\\d{1})")
        val idMatch = idPattern.find(text)
        extractedData["idNumber"] = idMatch?.value
        
        // Extract Name (usually appears after "Name" or before ID number)
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.matches(Regex(".*[A-Za-z]{3,}.*")) && 
                !line.contains(Regex("\\d{3}-\\d{4}")) &&
                line.length > 3) {
                extractedData["fullName"] = line
                break
            }
        }
        
        // Extract Nationality
        val nationalityKeywords = listOf("NATIONALITY", "الجنسية", "UNITED ARAB EMIRATES", "UAE")
        for (line in lines) {
            for (keyword in nationalityKeywords) {
                if (line.uppercase().contains(keyword)) {
                    extractedData["nationality"] = line.trim()
                    break
                }
            }
        }
    }
    
    private fun extractBackSideData(text: String) {
        val lines = text.split("\n")
        
        // Extract dates (pattern: DD/MM/YYYY)
        val datePattern = Regex("(\\d{2}/\\d{2}/\\d{4})")
        val dates = datePattern.findAll(text).map { it.value }.toList()
        
        if (dates.size >= 2) {
            extractedData["issueDate"] = dates[0]
            extractedData["expiryDate"] = dates[1]
        }
        
        // Extract Date of Birth (look for specific patterns)
        for (line in lines) {
            if (line.contains("BIRTH") || line.contains("الميلاد") || line.contains("DOB")) {
                val dobMatch = datePattern.find(line)
                extractedData["dateOfBirth"] = dobMatch?.value
                break
            }
        }
    }
    
    private fun finishWithResult(status: String, data: Map<String, Any?>?) {
        val intent = Intent().apply {
            putExtra("status", status)
            if (data != null) {
                for ((key, value) in data) {
                    putExtra(key, value?.toString())
                }
            }
        }
        setResult(RESULT_OK, intent)
        finish()
    }
    
    private fun finishWithError(error: String) {
        val intent = Intent().apply {
            putExtra("status", RESULT_ERROR)
            putExtra("error", error)
        }
        setResult(RESULT_OK, intent)
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        textRecognizer.close()
    }
}
