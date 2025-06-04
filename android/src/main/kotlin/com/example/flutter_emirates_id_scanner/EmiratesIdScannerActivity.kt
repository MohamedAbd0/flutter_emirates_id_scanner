package com.example.flutter_emirates_id_scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.delay
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
    private var frontImageHash: String? = null
    private var backImageHash: String? = null
    private var extractedData = mutableMapOf<String, String?>()
    
    // Rectangle bounds for cropping
    private var rectangleBounds: RectF? = null
    
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
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                canvas.let { c ->
                    // Calculate rectangle bounds for Emirates ID scanning
                    val centerX = width / 2f
                    val centerY = height / 2f
                    val cardWidth = width * 0.85f  // Slightly larger for better visibility
                    val cardHeight = cardWidth * 0.63f // Emirates ID aspect ratio (85.6mm × 53.98mm)
                    
                    val left = centerX - cardWidth / 2
                    val top = centerY - cardHeight / 2
                    val right = centerX + cardWidth / 2
                    val bottom = centerY + cardHeight / 2
                    
                    // Store rectangle bounds for reference
                    rectangleBounds = RectF(left, top, right, bottom)
                    
                    // Draw semi-black overlay outside scanning rectangle (darker overlay)
                    val overlayPaint = Paint().apply {
                        color = Color.parseColor("#00000000") // More opaque black 
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    }
                    
                    // Draw overlay on all four sides to create scanning window
                    c.drawRect(0f, 0f, width.toFloat(), top, overlayPaint) // Top area
                    c.drawRect(0f, bottom, width.toFloat(), height.toFloat(), overlayPaint) // Bottom area
                    c.drawRect(0f, top, left, bottom, overlayPaint) // Left area
                    c.drawRect(right, top, width.toFloat(), bottom, overlayPaint) // Right area
                    
                    // Draw Emirates ID scanning frame with enhanced visibility
                    val framePaint = Paint().apply {
                        color = Color.WHITE
                        style = Paint.Style.STROKE
                        strokeWidth = 3f
                        isAntiAlias = true
                    }
                    
                    // Draw main scanning rectangle
                    c.drawRect(left, top, right, bottom, framePaint)
                    
                    // Draw corner guides for better alignment
                    val cornerLength = 40f
                    val cornerPaint = Paint().apply {
                        color = Color.parseColor("#00E676") // Green color for corners
                        style = Paint.Style.STROKE
                        strokeWidth = 6f
                        isAntiAlias = true
                        strokeCap = Paint.Cap.ROUND
                    }
                    
                    // Top-left corner
                    c.drawLine(left, top, left + cornerLength, top, cornerPaint)
                    c.drawLine(left, top, left, top + cornerLength, cornerPaint)
                    
                    // Top-right corner
                    c.drawLine(right - cornerLength, top, right, top, cornerPaint)
                    c.drawLine(right, top, right, top + cornerLength, cornerPaint)
                    
                    // Bottom-left corner
                    c.drawLine(left, bottom - cornerLength, left, bottom, cornerPaint)
                    c.drawLine(left, bottom, left + cornerLength, bottom, cornerPaint)
                    
                    // Bottom-right corner
                    c.drawLine(right - cornerLength, bottom, right, bottom, cornerPaint)
                    c.drawLine(right, bottom - cornerLength, right, bottom, cornerPaint)
                    
                    // Draw center alignment guide
                    val centerLinePaint = Paint().apply {
                        color = Color.parseColor("#4DFFFFFF") // Semi-transparent white
                        style = Paint.Style.STROKE
                        strokeWidth = 1f
                        pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
                    }
                    
                    // Horizontal center line
                    c.drawLine(left + 20f, centerY, right - 20f, centerY, centerLinePaint)
                    // Vertical center line  
                    c.drawLine(centerX, top + 20f, centerX, bottom - 20f, centerLinePaint)
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
    
    private fun calculateImageHash(imagePath: String): String {
        return try {
            val file = File(imagePath)
            val bytes = file.readBytes()
            val digest = java.security.MessageDigest.getInstance("MD5")
            val hashBytes = digest.digest(bytes)
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate hash for $imagePath", e)
            ""
        }
    }
    
    private fun areImagesSimilar(hash1: String?, hash2: String?): Boolean {
        return hash1 != null && hash2 != null && hash1 == hash2
    }
    
    private fun getExifOrientation(imagePath: String): Int {
        return try {
            val exif = ExifInterface(imagePath)
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read EXIF data: ${e.message}")
            ExifInterface.ORIENTATION_NORMAL
        }
    }
    
    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(-90f)
                matrix.preScale(-1f, 1f)
            }
            else -> return bitmap // No rotation needed
        }
        
        return try {
            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotatedBitmap != bitmap) {
                bitmap.recycle() // Recycle original bitmap to free memory
            }
            rotatedBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rotate bitmap: ${e.message}")
            bitmap // Return original bitmap if rotation fails
        }
    }
    
    private fun cropImageToRectangle(originalPath: String, outputPath: String): Boolean {
        return try {
            // Get EXIF orientation before decoding
            val exifOrientation = getExifOrientation(originalPath)
            Log.d(TAG, "EXIF orientation: $exifOrientation")
            
            var originalBitmap = BitmapFactory.decodeFile(originalPath)
            if (originalBitmap == null) {
                Log.e(TAG, "Failed to decode image: $originalPath")
                return false
            }
            
            Log.d(TAG, "Original image size before rotation: ${originalBitmap.width} x ${originalBitmap.height}")
            
            // Apply EXIF rotation to fix image orientation
            originalBitmap = rotateBitmap(originalBitmap, exifOrientation)
            
            Log.d(TAG, "Original image size after rotation: ${originalBitmap.width} x ${originalBitmap.height}")
            
            // Get the viewfinder dimensions (which should match overlay dimensions)
            val overlayWidth = viewFinder.width.toFloat()
            val overlayHeight = viewFinder.height.toFloat()
            
            Log.d(TAG, "Preview size: $overlayWidth x $overlayHeight")
            
            // Calculate Emirates ID card dimensions (must match overlay calculations exactly)
            val cardWidth = overlayWidth * 0.85f  // Same as overlay
            val cardHeight = cardWidth * 0.63f // Emirates ID aspect ratio
            
            val centerX = overlayWidth / 2f
            val centerY = overlayHeight / 2f
            
            val rectLeft = centerX - cardWidth / 2f
            val rectTop = centerY - cardHeight / 2f
            val rectWidth = cardWidth
            val rectHeight = cardHeight
            
            Log.d(TAG, "Card rectangle: left=$rectLeft, top=$rectTop, width=$rectWidth, height=$rectHeight")
            
            // Calculate how the camera preview maps to the captured image
            // The preview might be scaled/cropped to fit the view
            val imageAspectRatio = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
            val previewAspectRatio = overlayWidth / overlayHeight
            
            val scaleX: Float
            val scaleY: Float
            val offsetX: Float
            val offsetY: Float
            
            if (imageAspectRatio > previewAspectRatio) {
                // Image is wider than preview - image is cropped horizontally
                scaleY = originalBitmap.height.toFloat() / overlayHeight
                scaleX = scaleY
                offsetY = 0f
                offsetX = (originalBitmap.width - overlayWidth * scaleX) / 2f
            } else {
                // Image is taller than preview - image is cropped vertically  
                scaleX = originalBitmap.width.toFloat() / overlayWidth
                scaleY = scaleX
                offsetX = 0f
                offsetY = (originalBitmap.height - overlayHeight * scaleY) / 2f
            }
            
            Log.d(TAG, "Scale: x=$scaleX, y=$scaleY, Offset: x=$offsetX, y=$offsetY")
            
            // Map overlay coordinates to image coordinates
            val cropLeft = (rectLeft * scaleX + offsetX).toInt().coerceAtLeast(0)
            val cropTop = (rectTop * scaleY + offsetY).toInt().coerceAtLeast(0)
            val cropWidth = (rectWidth * scaleX).toInt()
                .coerceAtMost(originalBitmap.width - cropLeft)
            val cropHeight = (rectHeight * scaleY).toInt()
                .coerceAtMost(originalBitmap.height - cropTop)
            
            Log.d(TAG, "Crop bounds: left=$cropLeft, top=$cropTop, width=$cropWidth, height=$cropHeight")
            
            if (cropWidth <= 0 || cropHeight <= 0) {
                Log.e(TAG, "Invalid crop dimensions")
                return false
            }
            
            val croppedBitmap = Bitmap.createBitmap(
                originalBitmap, 
                cropLeft, 
                cropTop, 
                cropWidth, 
                cropHeight
            )
            
            // Save cropped bitmap with corrected orientation
            FileOutputStream(outputPath).use { out ->
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            
            Log.d(TAG, "Cropped image saved: $outputPath (${croppedBitmap.width} x ${croppedBitmap.height})")
            
            originalBitmap.recycle()
            croppedBitmap.recycle()
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to crop image: ${e.message}", e)
            false
        }
    }
    
    private fun captureImage() {
        val imageCapture = imageCapture ?: return
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val tempFileName = when (scanningStep) {
            ScanningStep.FRONT -> "emirates_id_front_temp_$timestamp.jpg"
            ScanningStep.BACK -> "emirates_id_back_temp_$timestamp.jpg"
            ScanningStep.COMPLETED -> return
        }
        
        val finalFileName = when (scanningStep) {
            ScanningStep.FRONT -> "emirates_id_front_$timestamp.jpg"
            ScanningStep.BACK -> "emirates_id_back_$timestamp.jpg"
            ScanningStep.COMPLETED -> return
        }
        
        val tempFile = File(cacheDir, tempFileName)
        val finalFile = File(cacheDir, finalFileName)
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()
        
        imageCapture.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Image capture failed: ${exception.message}", exception)
                    finishWithError("Failed to capture image: ${exception.message}")
                }
                
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val tempImagePath = tempFile.absolutePath
                    Log.d(TAG, "Temp image captured: $tempImagePath")
                    
                    // Crop the image to rectangle area
                    val cropSuccess = cropImageToRectangle(tempImagePath, finalFile.absolutePath)
                    
                    if (!cropSuccess) {
                        Log.e(TAG, "Failed to crop image, but continuing...")
                        // Show guidance message instead of finishing with error
                        runOnUiThread {
                            when (scanningStep) {
                                ScanningStep.FRONT -> {
                                    instructionText.text = "Please try scanning the front side again"
                                }
                                ScanningStep.BACK -> {
                                    instructionText.text = "Please try scanning the back side again"
                                }
                                ScanningStep.COMPLETED -> return@runOnUiThread
                            }
                        }
                        // Reset instruction after delay
                        lifecycleScope.launch {
                            delay(3000)
                            runOnUiThread {
                                updateInstruction()
                            }
                        }
                        // Clean up temp file and continue
                        tempFile.delete()
                        return
                    }
                    
                    // Calculate hash for duplicate detection
                    val imageHash = calculateImageHash(finalFile.absolutePath)
                    
                    when (scanningStep) {
                        ScanningStep.FRONT -> {
                            frontImagePath = finalFile.absolutePath
                            frontImageHash = imageHash
                            scanningStep = ScanningStep.BACK
                            
                            // Show completion message for front side
                            runOnUiThread {
                                instructionText.text = "تم مسح الوجه الأمامي بنجاح. الآن قم بمسح الوجه الخلفي للهوية"
                            }
                            
                            // Delay before showing back instruction
                            lifecycleScope.launch {
                                delay(2000)
                                runOnUiThread {
                                    updateInstruction()
                                }
                            }
                        }
                        ScanningStep.BACK -> {
                            // Check if back image is different from front
                            if (areImagesSimilar(frontImageHash, imageHash)) {
                                Log.w(TAG, "Back image is similar to front image")
                                // Show error message and stay on back scanning
                                runOnUiThread {
                                    instructionText.text = "الصورة مشابهة للوجه الأمامي، قم بمسح الوجه الخلفي"
                                }
                                // Delete the duplicate image
                                finalFile.delete()
                                // Reset instruction after delay
                                lifecycleScope.launch {
                                    delay(3000)
                                    runOnUiThread {
                                        updateInstruction()
                                    }
                                }
                                return
                            }
                            
                            backImagePath = finalFile.absolutePath
                            backImageHash = imageHash
                            scanningStep = ScanningStep.COMPLETED
                            
                            // Show completion message for back side
                            runOnUiThread {
                                instructionText.text = "تم مسح الوجه الخلفي بنجاح. جاري معالجة البيانات..."
                            }
                            
                            // Delay before processing
                            lifecycleScope.launch {
                                delay(1500)
                                processCompleted()
                            }
                        }
                        ScanningStep.COMPLETED -> return
                    }
                    
                    // Clean up temp file
                    tempFile.delete()
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
