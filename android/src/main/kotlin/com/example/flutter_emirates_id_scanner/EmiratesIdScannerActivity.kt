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
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    
    private var scanningStep = ScanningStep.FRONT
    private var isCapturing = false  // Add capture state management
    private var captureStartTime = 0L  // Track when capture started for timeout detection
    private var lastProcessTime = 0L  // Add timeout tracking
    private var frontImagePath: String? = null
    private var backImagePath: String? = null
    private var frontSideContent: String? = null
    private var backSideContent: String? = null
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
        
        Log.d(TAG, "updateInstruction() called - Setting instruction for ${scanningStep.name}: $instruction")
        instructionText.text = instruction
    }
    
    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
    
    private fun startCamera() {
        // Don't start camera if we've completed scanning
        if (scanningStep == ScanningStep.COMPLETED) {
            Log.d(TAG, "Skipping camera start - scanning completed")
            return
        }
        
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                
                // Unbind all use cases before rebinding to avoid conflicts
                cameraProvider.unbindAll()
                
                // Create preview use case
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }
                
                // Create image capture use case with enhanced configuration
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)  // Use minimize latency for better reliability
                    .setTargetRotation(windowManager.defaultDisplay.rotation)  // Set proper rotation
                    .setIoExecutor(cameraExecutor)  // Use dedicated executor for IO operations
                    .build()
                
                // Create image analyzer use case
                imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, ImageAnalyzer())
                    }
                
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                
                try {
                    // Try to bind all use cases to the lifecycle
                    camera = cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture, imageAnalyzer
                    )
                    Log.d(TAG, "Camera bound successfully with all use cases")
                } catch (exc: Exception) {
                    // If binding fails, try without the analyzer as a fallback
                    Log.e(TAG, "Use case binding failed, trying without analyzer", exc)
                    try {
                        camera = cameraProvider.bindToLifecycle(
                            this, cameraSelector, preview, imageCapture
                        )
                        Log.d(TAG, "Camera bound without analyzer (fallback mode)")
                    } catch (e: Exception) {
                        Log.e(TAG, "Camera binding failed completely", e)
                        finishWithError("Failed to start camera: ${e.message}")
                    }
                }
            } catch (exc: Exception) {
                Log.e(TAG, "Camera provider error", exc)
                finishWithError("Camera initialization error: ${exc.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    private inner class ImageAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {
            // Check early if we're done with scanning
            if (scanningStep == ScanningStep.COMPLETED) {
                imageProxy.close()
                return
            }
            
            // Check if we're currently capturing with timeout protection
            if (isCapturing) {
                val currentTime = System.currentTimeMillis()
                val captureElapsed = currentTime - captureStartTime
                
                // If capture has been in progress for more than 10 seconds, reset it
                if (captureElapsed > 10000) {
                    Log.w(TAG, "ImageAnalyzer: Capture timeout detected after ${captureElapsed}ms, resetting capture state")
                    isCapturing = false
                    captureStartTime = 0L
                    runOnUiThread {
                        updateInstruction()
                    }
                } else {
                    Log.v(TAG, "ImageAnalyzer: Skipping analysis - capture in progress for ${scanningStep.name} (${captureElapsed}ms elapsed)")
                    imageProxy.close()
                    return
                }
            }
            
            // Add throttling to prevent excessive processing
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastProcessTime < 1000) { // Limit to once per second
                imageProxy.close()
                return
            }
            lastProcessTime = currentTime
            
            Log.v(TAG, "ImageAnalyzer: Processing image for ${scanningStep.name} side")
            
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                
                // Process image for text recognition with timeout
                textRecognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        // Double-check if we're still scanning and not capturing before processing
                        if (scanningStep != ScanningStep.COMPLETED && !isCapturing) {
                            processTextResult(visionText.text)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Text recognition failed", e)
                    }
                    .addOnCompleteListener {
                        // Always close the image proxy when done
                        try {
                            imageProxy.close()
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to close image proxy: ${e.message}")
                        }
                    }
            } else {
                imageProxy.close()
            }
        }
    }
    
    private fun processTextResult(text: String) {
        // Skip processing if we're already completed or capturing
        if (scanningStep == ScanningStep.COMPLETED || isCapturing) {
            return
        }
        
        Log.d(TAG, "OCR Text: $text")
        
        val isValidCard = when (scanningStep) {
            ScanningStep.FRONT -> isValidFrontSide(text)
            ScanningStep.BACK -> isValidBackSide(text)
            ScanningStep.COMPLETED -> false
        }
        
        if (isValidCard) {
            // Set capturing state to prevent further analysis
            isCapturing = true
            
            Log.d(TAG, "Valid ${scanningStep.name} side detected, starting capture process")
            
            runOnUiThread {
                instructionText.text = "جيد"
            }
            
            // Delay capture to allow user to see "Good" message
            lifecycleScope.launch {
                kotlinx.coroutines.delay(1000)
                Log.d(TAG, "About to call captureImage() for ${scanningStep.name} side")
                captureImage()
            }
        }
    }
    
    private fun isValidFrontSide(text: String): Boolean {
        val cleanText = text.replace("\\s+".toRegex(), " ").uppercase()
        
        // Check for ID number with pattern 784-YYYY-XXXXXXX-X (where 784 is UAE country code)
        val idPattern = Regex("784-\\d{4}-\\d{7}-\\d{1}")
        val hasIdNumber = text.contains(idPattern)
        
        // Check for key header text
        val hasHeaderText = cleanText.contains("UNITED ARAB EMIRATES") || 
                          cleanText.contains("الإمارات العربية المتحدة") ||
                          cleanText.contains("FEDERAL AUTHORITY")
        
        // Check for nationality field
        val hasNationality = cleanText.contains("NATIONALITY") || cleanText.contains("الجنسية")
        
        // Check for date patterns (DD/MM/YYYY)
        val datePattern = Regex("\\d{2}/\\d{2}/\\d{4}")
        val hasDateFormat = text.contains(datePattern)
        
        // ID Card specific fields
        val hasCardText = cleanText.contains("IDENTITY CARD") || 
                        cleanText.contains("بطاقة هوية") || 
                        cleanText.contains("RESIDENT IDENTITY CARD")
        
        // Return true if we have at least two strong indicators
        return (hasIdNumber || (hasHeaderText && (hasDateFormat || hasNationality || hasCardText)))
    }
    
    private fun isValidBackSide(text: String): Boolean {
        val cleanText = text.replace("\\s+".toRegex(), " ").uppercase()
        
        // Check for Card Number label which is specific to the back side
        val hasCardNumber = cleanText.contains("CARD NUMBER") || cleanText.contains("رقم البطاقة")
        
        // Check for occupation field which appears on back side
        val hasOccupation = cleanText.contains("OCCUPATION") || cleanText.contains("المهنة")
        
        // Check for employer field which appears on back side
        val hasEmployer = cleanText.contains("EMPLOYER") || cleanText.contains("صاحب العمل")
        
        // Check for issuing place field which appears on back side
        val hasIssuingPlace = cleanText.contains("ISSUING PLACE") || cleanText.contains("مكان الإصدار")
        
        // Check for specific UAE emirates/places with comprehensive validation
        val hasEmirateLocation = cleanText.contains("ABU DHABI") || cleanText.contains("أبوظبي") ||
                               cleanText.contains("DUBAI") || cleanText.contains("دبي") ||
                               cleanText.contains("SHARJAH") || cleanText.contains("الشارقة") ||
                               cleanText.contains("AL AIN") || cleanText.contains("العين") ||
                               cleanText.contains("AJMAN") || cleanText.contains("عجمان") ||
                               cleanText.contains("FUJAIRAH") || cleanText.contains("الفجيرة") ||
                               cleanText.contains("RAS AL KHAIMAH") || cleanText.contains("رأس الخيمة") ||
                               cleanText.contains("UMM AL QUWAIN") || cleanText.contains("أم القيوين")
        
        // Check for the machine readable zone (MRZ) pattern with multiple '<' characters
        val hasMrzPattern = text.contains("<<<<<<") || text.count { it == '<' } > 5
        
        // Check for electronic chip references
        val hasChipInfo = cleanText.contains("CHIP") || 
                        text.contains(Regex("\\d{8,}")) // Long numerical sequences for chip ID
        
        
        // Return true if we have at least two strong indicators of back side
        return ((hasCardNumber || hasOccupation || hasEmployer || hasIssuingPlace) && 
                (hasMrzPattern || hasChipInfo || hasEmirateLocation ))
    }
    
    /**
     * Checks if the current scanned side is a duplicate of a previously scanned side
     * based on text content comparison instead of image hash
     */
    private fun isDuplicateCardSide(newText: String, isFrontSide: Boolean): Boolean {
        // Clean the text for better comparison
        val cleanedText = newText.replace("\\s+".toRegex(), " ").uppercase()
        
        if (isFrontSide) {
            // Check if this front side text is similar to already scanned back side content
            if (backSideContent != null) {
                // Content similarity check
                return hasSimilarContent(cleanedText, backSideContent!!)
            }
        } else {
            // Check if this back side text is similar to already scanned front side content
            if (frontSideContent != null) {
                return hasSimilarContent(cleanedText, frontSideContent!!)
            }
        }
        
        return false
    }
    
    /**
     * Compares two text contents to determine if they likely represent the same side of a card
     * This uses multiple criteria to make the decision more robust
     */
    private fun hasSimilarContent(text1: String, text2: String): Boolean {
        // --- SPECIFIC ID NUMBER CHECK ---
        // If both texts contain the same ID number pattern, they are likely the same
        val idPattern = Regex("784-\\d{4}-\\d{7}-\\d{1}")
        val idMatches1 = idPattern.findAll(text1).map { it.value }.toList()
        val idMatches2 = idPattern.findAll(text2).map { it.value }.toList()
        
        // If both sides have the same ID number, they are definitely the same side
        if (idMatches1.isNotEmpty() && idMatches2.isNotEmpty() && idMatches1.any { idMatches2.contains(it) }) {
            Log.d(TAG, "Same ID number found on both scans")
            return true
        }
        
        // --- FRONT SIDE SPECIFIC CHECK ---
        // Check for key phrases that should appear only on front side
        val frontSideKeywords = listOf(
            "UNITED ARAB EMIRATES", "الإمارات العربية المتحدة", "FEDERAL AUTHORITY",
            "IDENTITY CARD", "بطاقة هوية", "RESIDENT IDENTITY CARD"
        )
        
        var frontSideMatches1 = 0
        var frontSideMatches2 = 0
        
        for (keyword in frontSideKeywords) {
            if (text1.contains(keyword)) frontSideMatches1++
            if (text2.contains(keyword)) frontSideMatches2++
        }
        
        // If both texts have multiple front side indicators, they are likely both front sides
        val isBothFront = frontSideMatches1 >= 2 && frontSideMatches2 >= 2
        
        // --- BACK SIDE SPECIFIC CHECK ---
        // Check for key phrases that should appear only on back side
        val backSideKeywords = listOf(
            "CARD NUMBER", "رقم البطاقة", "OCCUPATION", "المهنة",
            "EMPLOYER", "صاحب العمل", "ISSUING PLACE", "مكان الإصدار"
        )
        
        var backSideMatches1 = 0
        var backSideMatches2 = 0
        
        for (keyword in backSideKeywords) {
            if (text1.contains(keyword)) backSideMatches1++
            if (text2.contains(keyword)) backSideMatches2++
        }
        
        // Check for MRZ pattern which is specific to back side
        val hasMrzPattern1 = text1.contains("<<<<<<") || text1.count { it == '<' } > 5
        val hasMrzPattern2 = text2.contains("<<<<<<") || text2.count { it == '<' } > 5
        
        if (hasMrzPattern1) backSideMatches1++
        if (hasMrzPattern2) backSideMatches2++
        
        // If both texts have multiple back side indicators, they are likely both back sides
        val isBothBack = backSideMatches1 >= 2 && backSideMatches2 >= 2
        
        // Log result for debugging
        if (isBothFront || isBothBack) {
            Log.d(TAG, "Duplicate side detected: isBothFront=$isBothFront, isBothBack=$isBothBack")
        }
        
        return isBothFront || isBothBack
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
        Log.d(TAG, "captureImage() called for ${scanningStep.name} side")
        
        // Safety checks to prevent freezing
        if (scanningStep == ScanningStep.COMPLETED) {
            Log.d(TAG, "Skipping capture - scanning already completed")
            return
        }
        
        // Prevent multiple concurrent capture requests
        if (isCapturing) {
            Log.d(TAG, "Capture already in progress, ignoring duplicate request")
            return
        }
        
        isCapturing = true
        captureStartTime = System.currentTimeMillis()  // Track when capture started
        Log.d(TAG, "Set isCapturing = true for ${scanningStep.name} side (started at $captureStartTime)")
        
        // Add timeout to reset isCapturing flag in case capture gets stuck
        lifecycleScope.launch {
            delay(15000) // 15 second timeout
            if (isCapturing) {
                Log.w(TAG, "Capture timeout reached, resetting isCapturing flag")
                isCapturing = false
                captureStartTime = 0L
                runOnUiThread {
                    instructionText.text = "حدث خطأ في التصوير، يرجى المحاولة مرة أخرى"
                }
                lifecycleScope.launch {
                    delay(3000)
                    runOnUiThread {
                        updateInstruction()
                    }
                }
            }
        }
        
        val imageCapture = imageCapture ?: run {
            Log.e(TAG, "ImageCapture is null, cannot capture image")
            isCapturing = false
            captureStartTime = 0L
            return
        }
        
        // Ensure camera is still bound and active
        if (camera == null) {
            Log.e(TAG, "Camera is null, attempting to restart")
            isCapturing = false
            startCamera()
            return
        }

        // Check camera state 
        val cameraInfo = camera?.cameraInfo
        Log.d(TAG, "Camera state - available: ${cameraInfo != null}")
        if (cameraInfo != null) {
            Log.d(TAG, "Camera implementation type: ${cameraInfo.implementationType}")
            Log.d(TAG, "Camera sensor rotation: ${cameraInfo.sensorRotationDegrees}")
        }
        
        Log.d(TAG, "ImageCapture and Camera are ready, proceeding with capture")
        
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
        
        Log.d(TAG, "Starting image capture for step: $scanningStep")
        Log.d(TAG, "ImageCapture object: $imageCapture")
        Log.d(TAG, "Output file: ${tempFile.absolutePath}")
        
        // Set up a fallback timeout to prevent permanent hang
        val captureTimeoutJob = lifecycleScope.launch {
            delay(10000) // 10 second timeout for capture
            if (isCapturing) {
                Log.w(TAG, "Capture callback timeout - forcing reset")
                runOnUiThread {
                    isCapturing = false
                    instructionText.text = "حدث خطأ في التصوير، يرجى المحاولة مرة أخرى"
                    lifecycleScope.launch {
                        delay(3000)
                        runOnUiThread {
                            updateInstruction()
                        }
                    }
                }
            }
        }
        
        // Use MainExecutor for callback to ensure proper UI thread handling
        imageCapture.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(this),  // Use MainExecutor for more reliable callbacks
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Image capture failed for ${scanningStep.name}: ${exception.message}", exception)
                    Log.e(TAG, "ImageCaptureException details - error code: ${exception.imageCaptureError}")
                    
                    captureTimeoutJob.cancel() // Cancel timeout since we got a callback
                    isCapturing = false  // Reset capture state on error
                    finishWithError("Failed to capture image: ${exception.message}")
                }
                
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.d(TAG, "★★★ onImageSaved callback TRIGGERED for ${scanningStep.name} side ★★★")
                    Log.d(TAG, "Image saved successfully, output URI: ${output.savedUri}")
                    Log.d(TAG, "Temp file exists: ${tempFile.exists()}, size: ${if (tempFile.exists()) tempFile.length() else "N/A"}")
                    
                    captureTimeoutJob.cancel() // Cancel timeout since we got the callback
                    
                    try {
                        // Double-check we're still in the expected scanning step
                        if (scanningStep == ScanningStep.COMPLETED) {
                            Log.d(TAG, "Image captured but scanning already completed, cleaning up")
                            tempFile.delete()
                            isCapturing = false
                            return
                        }
                        
                        Log.d(TAG, "Processing captured image for ${scanningStep.name} side")
                        processCompletedCapture(tempFile, finalFile)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in onImageSaved processing: ${e.message}", e)
                        isCapturing = false
                        finishWithError("Error processing captured image: ${e.message}")
                    }
                }
            }
        )
        
        Log.d(TAG, "takePicture() call completed, waiting for callback...")
    }
    
    private fun processCompletedCapture(tempFile: File, finalFile: File) {
        Log.d(TAG, "processCompletedCapture called for ${scanningStep.name} side")
        val tempImagePath = tempFile.absolutePath
        Log.d(TAG, "Temp image captured: $tempImagePath")
        
        // Crop the image to rectangle area
        Log.d(TAG, "Starting image cropping...")
        val cropSuccess = cropImageToRectangle(tempImagePath, finalFile.absolutePath)
        Log.d(TAG, "Image cropping result: $cropSuccess")
        
        if (!cropSuccess) {
            Log.e(TAG, "Failed to crop image, but continuing...")
            // Show guidance message instead of finishing with error
            when (scanningStep) {
                ScanningStep.FRONT -> {
                    instructionText.text = "Please try scanning the front side again"
                }
                ScanningStep.BACK -> {
                    instructionText.text = "Please try scanning the back side again"
                }
                ScanningStep.COMPLETED -> return
            }
            // Reset instruction after delay
            lifecycleScope.launch {
                delay(3000)
                updateInstruction()
            }
            // Clean up temp file and reset state
            tempFile.delete()
            isCapturing = false  // Reset capture state
            return
        }
        
        Log.d(TAG, "Processing captured image for ${scanningStep.name} side")
        
        when (scanningStep) {
            ScanningStep.FRONT -> {
                Log.d(TAG, "Processing front side image...")
                // Extract text from image to check for front side indicators
                lifecycleScope.launch {
                    try {
                        Log.d(TAG, "Extracting text from front side image...")
                        val frontText = extractTextFromImage(finalFile.absolutePath)
                        Log.d(TAG, "Front side text extraction completed, validating...")
                        val isFrontSide = isValidFrontSide(frontText)
                        Log.d(TAG, "Front side validation result: $isFrontSide")
                        
                        if (!isFrontSide) {
                            // This doesn't look like a front side
                            Log.w(TAG, "Image doesn't appear to be front side of Emirates ID")
                            // Show error message and stay on front scanning
                            runOnUiThread {
                                instructionText.text = "هذه ليست الواجهة الأمامية للبطاقة، يرجى مسح الوجه الأمامي"
                            }
                            // Delete the invalid image
                            finalFile.delete()
                            // Reset instruction after delay
                            lifecycleScope.launch {
                                delay(3000)
                                runOnUiThread {
                                    updateInstruction()
                                }
                            }
                            isCapturing = false  // Reset capture state
                            return@launch
                        }
                        
                        Log.d(TAG, "Checking for duplicate front side...")
                        // Check if the front side is a duplicate of the back (in case user scanned back first)
                        if (backSideContent != null && isDuplicateCardSide(frontText, isFrontSide = true)) {
                            Log.w(TAG, "This appears to be a duplicate of the back side")
                            runOnUiThread {
                                instructionText.text = "هذه نفس الواجهة الخلفية، يرجى مسح الوجه الأمامي للبطاقة"
                            }
                            // Delete the invalid image
                            finalFile.delete()
                            // Reset instruction after delay
                            lifecycleScope.launch {
                                delay(3000)
                                runOnUiThread {
                                    updateInstruction()
                                }
                            }
                            isCapturing = false  // Reset capture state
                            return@launch
                        }
                        
                        frontImagePath = finalFile.absolutePath
                        frontSideContent = frontText
                        
                        // Show completion message for front side BEFORE transitioning
                        runOnUiThread {
                            instructionText.text = "تم مسح الوجه الأمامي بنجاح. الآن قم بمسح الوجه الخلفي للهوية"
                        }
                        
                        Log.d(TAG, "Front side processed successfully, transitioning to back side scanning")
                        
                        // CRITICAL: Reset isCapturing IMMEDIATELY after success message to prevent infinite loop
                        isCapturing = false
                        captureStartTime = 0L
                        Log.d(TAG, "isCapturing flag reset to false after front side completion")
                        
                        // Transition to back side with improved error handling
                        transitionToBackSideScanning()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing front side: ${e.message}", e)
                        isCapturing = false
                        captureStartTime = 0L
                        runOnUiThread {
                            finishWithError("Error processing front side: ${e.message}")
                        }
                    }
                }
            }
            ScanningStep.BACK -> {
                Log.d(TAG, "Processing back side image...")
                // Extract text from image to check for back side indicators
                lifecycleScope.launch {
                    try {
                        val backText = extractTextFromImage(finalFile.absolutePath)
                        
                        // First check if this is actually a back side
                        val isBackSide = isValidBackSide(backText)
                        if (!isBackSide) {
                            // This doesn't look like a back side
                            Log.w(TAG, "Image doesn't appear to be back side of Emirates ID")
                            // Show error message and stay on back scanning
                            runOnUiThread {
                                instructionText.text = "هذه ليست الواجهة الخلفية للبطاقة، يرجى مسح الوجه الخلفي"
                            }
                            // Delete the invalid image
                            finalFile.delete()
                            // Reset instruction after delay
                            lifecycleScope.launch {
                                delay(3000)
                                runOnUiThread {
                                    updateInstruction()
                                }
                            }
                            isCapturing = false  // Reset capture state
                            return@launch
                        }
                        
                        // Check if this is a duplicate of the front side
                        if (frontSideContent != null && isDuplicateCardSide(backText, isFrontSide = false)) {
                            Log.w(TAG, "This appears to be a duplicate of the front side")
                            runOnUiThread {
                                instructionText.text = "هذه نفس الواجهة الأمامية، يرجى مسح الوجه الخلفي للبطاقة"
                            }
                            // Delete the invalid image
                            finalFile.delete()
                            // Reset instruction after delay
                            lifecycleScope.launch {
                                delay(3000)
                                runOnUiThread {
                                    updateInstruction()
                                }
                            }
                            isCapturing = false  // Reset capture state
                            return@launch
                        }
                        
                        backImagePath = finalFile.absolutePath
                        scanningStep = ScanningStep.COMPLETED
                        
                        Log.d(TAG, "Both sides captured successfully!")
                        Log.d(TAG, "Front image: $frontImagePath")
                        Log.d(TAG, "Back image: $backImagePath")
                        
                        // Extract data from both captured images and complete the process
                        Log.d(TAG, "Starting OCR data extraction from captured images...")
                        isCapturing = false // Reset capture state before data extraction
                        
                        // Process both images to extract OCR data and finish with results
                        runOnUiThread {
                            processCompleted()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing back side: ${e.message}", e)
                        isCapturing = false
                        runOnUiThread {
                            finishWithError("Error processing back side: ${e.message}")
                        }
                    }
                }
            }
            ScanningStep.COMPLETED -> {
                Log.d(TAG, "Scanning already completed, ignoring capture")
                isCapturing = false
            }
        }
        
        // Clean up temp file
        tempFile.delete()
    }

    private fun processCompleted() {
        lifecycleScope.launch {
            try {
                // Extract data from both images
                extractDataFromImages()
                
                val result = mapOf(
                    "fullName" to extractedData["fullName"],
                    "nameEn" to extractedData["nameEn"],
                    "nameAr" to extractedData["nameAr"],
                    "idNumber" to extractedData["idNumber"],
                    "nationality" to extractedData["nationality"],
                    "dateOfBirth" to extractedData["dateOfBirth"],
                    "issueDate" to extractedData["issueDate"],
                    "expiryDate" to extractedData["expiryDate"],
                    "gender" to extractedData["gender"],
                    "frontImagePath" to frontImagePath,
                    "backImagePath" to backImagePath,
                    "cardNumber" to extractedData["cardNumber"],
                    "occupation" to extractedData["occupation"],
                    "employer" to extractedData["employer"],
                    "issuingPlace" to extractedData["issuingPlace"],
                    "mrzData" to extractedData["mrzData"]
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
        val lines = text.split("\n").map { it.trim() }
        val fullText = text.replace("\n", " ")
        
        Log.d(TAG, "Extracting front side data from text: $text")
        
        // Extract ID Number (pattern: XXX-XXXX-XXXXXXX-X)
        val idPattern = Regex("(\\d{3}-\\d{4}-\\d{7}-\\d)")
        val idMatch = idPattern.find(text)
        extractedData["idNumber"] = idMatch?.value
        Log.d(TAG, "Extracted ID Number: ${extractedData["idNumber"]}")
        
        // Extract English Name (after "Name:" label)
        val nameEnPattern = Regex("Name\\s*:?\\s*([A-Z][a-zA-Z\\s]+)")
        val nameEnMatch = nameEnPattern.find(text)
        if (nameEnMatch != null && nameEnMatch.groups.size > 1) {
            val nameEn = nameEnMatch.groups[1]?.value?.trim()
            if (!nameEn.isNullOrBlank() && nameEn.length > 3) {
                extractedData["nameEn"] = nameEn
                extractedData["fullName"] = nameEn // Keep for backward compatibility
            }
        }
        Log.d(TAG, "Extracted English Name: ${extractedData["nameEn"]}")
        
        // Extract Arabic Name (enhanced patterns for better detection)
        var arabicNameFound = false
        
        // First try: Look for Arabic text after "Name" or "الاسم" labels
        val arabicNamePatterns = listOf(
            Regex("(?:Name|الاسم)\\s*[:\\/]?\\s*([\\u0600-\\u06FF\\s]+)"),
            Regex("([\\u0600-\\u06FF]+(?:\\s+[\\u0600-\\u06FF]+){1,4})") // Arabic name pattern (2-5 words)
        )
        
        for (pattern in arabicNamePatterns) {
            val match = pattern.find(text)
            if (match != null && match.groups.size > 1) {
                val arabicName = match.groups[1]?.value?.trim()
                if (!arabicName.isNullOrBlank() && arabicName.length > 4 && 
                    !arabicName.contains("الجنسية") && !arabicName.contains("الميلاد") &&
                    !arabicName.contains("الإصدار") && !arabicName.contains("الانتهاء")) {
                    extractedData["nameAr"] = arabicName
                    arabicNameFound = true
                    break
                }
            }
        }
        
        // Fallback: Look for pure Arabic lines (excluding labels and dates)
        if (!arabicNameFound) {
            for (line in lines) {
                // Check if line contains primarily Arabic characters and is not a label
                val arabicCharCount = line.count { it in '\u0600'..'\u06FF' }
                val totalChars = line.replace("\\s".toRegex(), "").length
                
                if (arabicCharCount > 3 && totalChars > 5 && 
                    arabicCharCount.toFloat() / totalChars > 0.6 && // More than 60% Arabic chars
                    !line.contains("الاسم") && !line.contains("الجنسية") && 
                    !line.contains("الميلاد") && !line.contains("الإصدار") &&
                    !line.contains("الانتهاء") && !line.contains("الجنس") &&
                    !line.contains("/") && !line.contains("-") && // Avoid dates
                    !line.matches(Regex(".*\\d.*"))) { // Avoid lines with numbers
                    extractedData["nameAr"] = line.trim()
                    break
                }
            }
        }
        Log.d(TAG, "Extracted Arabic Name: ${extractedData["nameAr"]}")
        
        // Extract Nationality (more precise patterns)
        val nationalityPatterns = listOf(
            Regex("Nationality\\s*:?\\s*([A-Za-z\\s]+)"),
            Regex("الجنسية\\s*:?\\s*([A-Za-z\\s]+)")
        )
        
        for (pattern in nationalityPatterns) {
            val match = pattern.find(text)
            if (match != null && match.groups.size > 1) {
                val nationality = match.groups[1]?.value?.trim()
                if (!nationality.isNullOrBlank()) {
                    extractedData["nationality"] = nationality
                    break
                }
            }
        }
        
        // Fallback: Look for common nationalities in lines
        if (extractedData["nationality"].isNullOrBlank()) {
            val commonNationalities = listOf("Egypt", "UAE", "Saudi Arabia", "Jordan", "Lebanon", "Syria", "Pakistan", "India", "Bangladesh")
            for (line in lines) {
                for (nationality in commonNationalities) {
                    if (line.contains(nationality, ignoreCase = true)) {
                        extractedData["nationality"] = nationality
                        break
                    }
                }
                if (!extractedData["nationality"].isNullOrBlank()) break
            }
        }
        Log.d(TAG, "Extracted Nationality: ${extractedData["nationality"]}")
        
        // Extract Date of Birth (enhanced patterns and fallbacks)
        val dobPatterns = listOf(
            Regex("(?:Date of Birth|تاريخ الميلاد|DOB|Birth)\\s*[:\\/]?\\s*(\\d{1,2}[\\/-]\\d{1,2}[\\/-]\\d{4})"),
            Regex("(?:Date of Birth|تاريخ الميلاد|DOB|Birth)\\s*[:\\/]?\\s*(\\d{4}[\\/-]\\d{1,2}[\\/-]\\d{1,2})"),
            Regex("(?:Date of Birth|تاريخ الميلاد|DOB|Birth)\\s*[:\\/]?\\s*(\\d{1,2}\\.\\d{1,2}\\.\\d{4})"),
            Regex("(?:الميلاد)\\s*[:\\/]?\\s*(\\d{1,2}[\\/-]\\d{1,2}[\\/-]\\d{4})")
        )
        
        var dobFound = false
        for (pattern in dobPatterns) {
            val dobMatch = pattern.find(text)
            if (dobMatch != null && dobMatch.groups.size > 1) {
                extractedData["dateOfBirth"] = dobMatch.groups[1]?.value
                dobFound = true
                break
            }
        }
        
        // Fallback 1: Look for date patterns near birth-related keywords in individual lines
        if (!dobFound) {
            for (line in lines) {
                if (line.contains("Birth", ignoreCase = true) || line.contains("الميلاد") || line.contains("DOB")) {
                    val datePatterns = listOf(
                        Regex("(\\d{1,2}[\\/-]\\d{1,2}[\\/-]\\d{4})"),
                        Regex("(\\d{4}[\\/-]\\d{1,2}[\\/-]\\d{1,2})"),
                        Regex("(\\d{1,2}\\.\\d{1,2}\\.\\d{4})")
                    )
                    for (datePattern in datePatterns) {
                        val dateMatch = datePattern.find(line)
                        if (dateMatch != null) {
                            extractedData["dateOfBirth"] = dateMatch.value
                            dobFound = true
                            break
                        }
                    }
                    if (dobFound) break
                }
            }
        }
        
        // Fallback 2: Look for the oldest date in the text (likely to be birth date)
        if (!dobFound) {
            val allDates = Regex("(\\d{1,2}[\\/-]\\d{1,2}[\\/-]\\d{4})").findAll(text).map { it.value }.toList()
            if (allDates.isNotEmpty()) {
                // Sort dates and pick the oldest one as potential birth date
                val sortedDates = allDates.sortedBy { 
                    try {
                        val parts = it.split(Regex("[\\/-]"))
                        if (parts.size == 3) {
                            val year = parts[2].toInt()
                            val month = parts[1].toInt()
                            val day = parts[0].toInt()
                            year * 10000 + month * 100 + day
                        } else 0
                    } catch (e: Exception) { 0 }
                }
                if (sortedDates.isNotEmpty()) {
                    extractedData["dateOfBirth"] = sortedDates.first()
                }
            }
        }
        Log.d(TAG, "Extracted Date of Birth: ${extractedData["dateOfBirth"]}")
        
        // Extract Issue Date (enhanced patterns)
        val issueDatePatterns = listOf(
            Regex("(?:Issuing Date|تاريخ الإصدار|Issue Date|Issued)\\s*[:\\/]?\\s*(\\d{1,2}[\\/-]\\d{1,2}[\\/-]\\d{4})"),
            Regex("(?:Issuing Date|تاريخ الإصدار|Issue Date|Issued)\\s*[:\\/]?\\s*(\\d{4}[\\/-]\\d{1,2}[\\/-]\\d{1,2})"),
            Regex("(?:Issuing Date|تاريخ الإصدار|Issue Date|Issued)\\s*[:\\/]?\\s*(\\d{1,2}\\.\\d{1,2}\\.\\d{4})"),
            Regex("(?:الإصدار)\\s*[:\\/]?\\s*(\\d{1,2}[\\/-]\\d{1,2}[\\/-]\\d{4})")
        )
        
        var issueDateFound = false
        for (pattern in issueDatePatterns) {
            val issueDateMatch = pattern.find(text)
            if (issueDateMatch != null && issueDateMatch.groups.size > 1) {
                extractedData["issueDate"] = issueDateMatch.groups[1]?.value
                issueDateFound = true
                break
            }
        }
        
        // Fallback: Look for issue-related keywords in lines
        if (!issueDateFound) {
            for (line in lines) {
                if (line.contains("Issue", ignoreCase = true) || line.contains("الإصدار") || line.contains("Issued")) {
                    val datePatterns = listOf(
                        Regex("(\\d{1,2}[\\/-]\\d{1,2}[\\/-]\\d{4})"),
                        Regex("(\\d{4}[\\/-]\\d{1,2}[\\/-]\\d{1,2})"),
                        Regex("(\\d{1,2}\\.\\d{1,2}\\.\\d{4})")
                    )
                    for (datePattern in datePatterns) {
                        val dateMatch = datePattern.find(line)
                        if (dateMatch != null) {
                            extractedData["issueDate"] = dateMatch.value
                            issueDateFound = true
                            break
                        }
                    }
                    if (issueDateFound) break
                }
            }
        }
        Log.d(TAG, "Extracted Issue Date: ${extractedData["issueDate"]}")
        
        // Extract Expiry Date (enhanced patterns)
        val expiryDatePatterns = listOf(
            Regex("(?:Expiry Date|تاريخ الانتهاء|Expires|Expiry|انتهاء)\\s*[:\\/]?\\s*(\\d{1,2}[\\/-]\\d{1,2}[\\/-]\\d{4})"),
            Regex("(?:Expiry Date|تاريخ الانتهاء|Expires|Expiry|انتهاء)\\s*[:\\/]?\\s*(\\d{4}[\\/-]\\d{1,2}[\\/-]\\d{1,2})"),
            Regex("(?:Expiry Date|تاريخ الانتهاء|Expires|Expiry|انتهاء)\\s*[:\\/]?\\s*(\\d{1,2}\\.\\d{1,2}\\.\\d{4})"),
            Regex("(?:الانتهاء|انتهاء)\\s*[:\\/]?\\s*(\\d{1,2}[\\/-]\\d{1,2}[\\/-]\\d{4})")
        )
        
        var expiryDateFound = false
        for (pattern in expiryDatePatterns) {
            val expiryDateMatch = pattern.find(text)
            if (expiryDateMatch != null && expiryDateMatch.groups.size > 1) {
                extractedData["expiryDate"] = expiryDateMatch.groups[1]?.value
                expiryDateFound = true
                break
            }
        }
        
        // Fallback: Look for expiry-related keywords in lines
        if (!expiryDateFound) {
            for (line in lines) {
                if (line.contains("Expir", ignoreCase = true) || line.contains("الانتهاء") || 
                    line.contains("انتهاء") || line.contains("Expires", ignoreCase = true)) {
                    val datePatterns = listOf(
                        Regex("(\\d{1,2}[\\/-]\\d{1,2}[\\/-]\\d{4})"),
                        Regex("(\\d{4}[\\/-]\\d{1,2}[\\/-]\\d{1,2})"),
                        Regex("(\\d{1,2}\\.\\d{1,2}\\.\\d{4})")
                    )
                    for (datePattern in datePatterns) {
                        val dateMatch = datePattern.find(line)
                        if (dateMatch != null) {
                            extractedData["expiryDate"] = dateMatch.value
                            expiryDateFound = true
                            break
                        }
                    }
                    if (expiryDateFound) break
                }
            }
        }
        
        // Fallback: Look for the latest date in the text (likely to be expiry date)
        if (!expiryDateFound) {
            val allDates = Regex("(\\d{1,2}[\\/-]\\d{1,2}[\\/-]\\d{4})").findAll(text).map { it.value }.toList()
            if (allDates.size >= 2) {
                // Sort dates and pick the latest one as potential expiry date
                val sortedDates = allDates.sortedByDescending { 
                    try {
                        val parts = it.split(Regex("[\\/-]"))
                        if (parts.size == 3) {
                            val year = parts[2].toInt()
                            val month = parts[1].toInt()
                            val day = parts[0].toInt()
                            year * 10000 + month * 100 + day
                        } else 0
                    } catch (e: Exception) { 0 }
                }
                if (sortedDates.isNotEmpty()) {
                    extractedData["expiryDate"] = sortedDates.first()
                }
            }
        }
        Log.d(TAG, "Extracted Expiry Date: ${extractedData["expiryDate"]}")
        
        // Extract Gender (enhanced patterns and fallbacks)
        val genderPatterns = listOf(
            Regex("(?:Sex|Gender|الجنس)\\s*[:\\/]?\\s*([MFmf]|Male|Female|MALE|FEMALE|ذكر|أنثى)"),
            Regex("(?:Sex|Gender|الجنس)\\s*[:\\/]?\\s*([MF])"),
            Regex("\\b(Male|Female|MALE|FEMALE)\\b"),
            Regex("\\b(ذكر|أنثى)\\b"),
            Regex("\\b([MF])\\b(?!\\d)") // M or F not followed by digits
        )
        
        var genderFound = false
        for (pattern in genderPatterns) {
            val genderMatch = pattern.find(text)
            if (genderMatch != null && genderMatch.groups.size > 1) {
                val genderValue = genderMatch.groups[1]?.value?.uppercase()
                when (genderValue) {
                    "M", "MALE" -> {
                        extractedData["gender"] = "M"
                        genderFound = true
                        break
                    }
                    "F", "FEMALE" -> {
                        extractedData["gender"] = "F"
                        genderFound = true
                        break
                    }
                    "ذكر" -> {
                        extractedData["gender"] = "M"
                        genderFound = true
                        break
                    }
                    "أنثى" -> {
                        extractedData["gender"] = "F"
                        genderFound = true
                        break
                    }
                }
            }
        }
        
        // Fallback: Look for standalone M or F near gender indicators in lines
        if (!genderFound) {
            for (line in lines) {
                if (line.contains("Sex", ignoreCase = true) || line.contains("Gender", ignoreCase = true) || 
                    line.contains("الجنس")) {
                    val sexMatch = Regex("\\b([MFmf])\\b").find(line)
                    if (sexMatch != null) {
                        val gender = sexMatch.value.uppercase()
                        if (gender == "M" || gender == "F") {
                            extractedData["gender"] = gender
                            genderFound = true
                            break
                        }
                    }
                }
            }
        }
        
        // Final fallback: Look for standalone M/F anywhere in text (with context validation)
        if (!genderFound) {
            val mfMatches = Regex("\\b([MF])\\b").findAll(text).toList()
            if (mfMatches.size == 1) { // Only if there's exactly one M or F
                extractedData["gender"] = mfMatches.first().value
            }
        }
        Log.d(TAG, "Extracted Gender: ${extractedData["gender"]}")
    }
    
    private fun extractBackSideData(text: String) {
        val lines = text.split("\n").map { it.trim() }
        
        Log.d(TAG, "Extracting back side data from text: $text")
        
        // Extract Card Number (clear pattern from your image)
        val cardNumberPatterns = listOf(
            Regex("Card Number\\s*/\\s*رقم البطاقة\\s*([0-9]+)"),
            Regex("(?:Card Number|رقم البطاقة)\\s*:?\\s*([0-9]+)"),
            Regex("^([0-9]{8,10})$") // Standalone number pattern
        )
        
        for (pattern in cardNumberPatterns) {
            val match = pattern.find(text)
            if (match != null && match.groups.size > 1) {
                val cardNumber = match.groups[1]?.value?.trim()
                if (!cardNumber.isNullOrBlank() && cardNumber.length >= 8) {
                    extractedData["cardNumber"] = cardNumber
                    break
                }
            }
        }
        
        // Fallback: Look for 8-10 digit numbers in lines
        if (extractedData["cardNumber"].isNullOrBlank()) {
            for (line in lines) {
                val numberMatch = Regex("\\b([0-9]{8,10})\\b").find(line)
                if (numberMatch != null) {
                    extractedData["cardNumber"] = numberMatch.value
                    break
                }
            }
        }
        Log.d(TAG, "Extracted Card Number: ${extractedData["cardNumber"]}")
        
        // Extract Occupation (precise pattern from your image)
        val occupationPatterns = listOf(
            Regex("Occupation\\s*:?\\s*([A-Za-z\\s]+)(?:\\n|$)"),
            Regex("المهنة\\s*:?\\s*([A-Za-z\\s]+)"),
            Regex("Occupation\\s*:?\\s*(.+?)(?=\\n(?:Employer|صاحب العمل)|$)")
        )
        
        for (pattern in occupationPatterns) {
            val match = pattern.find(text)
            if (match != null && match.groups.size > 1) {
                val occupation = match.groups[1]?.value?.trim()
                if (!occupation.isNullOrBlank() && occupation.length > 2) {
                    extractedData["occupation"] = occupation
                    break
                }
            }
        }
        Log.d(TAG, "Extracted Occupation: ${extractedData["occupation"]}")
        
        // Extract Employer (handle multi-line employer names)
        val employerPatterns = listOf(
            Regex("Employer\\s*:?\\s*(.+?)(?=\\n(?:Issuing Place|مكان الإصدار)|$)", RegexOption.DOT_MATCHES_ALL),
            Regex("صاحب العمل\\s*:?\\s*(.+?)(?=\\n|$)"),
            Regex("Employer\\s*:?\\s*([^\n]+(?:\\n[^\n:]+)*)")
        )
        
        for (pattern in employerPatterns) {
            val match = pattern.find(text)
            if (match != null && match.groups.size > 1) {
                val employer = match.groups[1]?.value?.trim()?.replace("\\s+".toRegex(), " ")
                if (!employer.isNullOrBlank() && employer.length > 3) {
                    extractedData["employer"] = employer
                    break
                }
            }
        }
        
        // Fallback: Look for employer in lines after "Employer"
        if (extractedData["employer"].isNullOrBlank()) {
            for (i in lines.indices) {
                if (lines[i].contains("Employer", ignoreCase = true)) {
                    val employerParts = mutableListOf<String>()
                    
                    // Extract from same line after colon
                    val colonIndex = lines[i].indexOf(":")
                    if (colonIndex != -1 && colonIndex < lines[i].length - 1) {
                        val sameLine = lines[i].substring(colonIndex + 1).trim()
                        if (sameLine.isNotEmpty()) employerParts.add(sameLine)
                    }
                    
                    // Check next lines for continuation
                    for (j in i + 1 until minOf(i + 3, lines.size)) {
                        if (lines[j].isNotEmpty() && 
                            !lines[j].contains(":", ignoreCase = true) &&
                            !lines[j].contains("Issuing", ignoreCase = true) &&
                            lines[j].matches(Regex("^[A-Za-z\\s]+$"))) {
                            employerParts.add(lines[j])
                        } else {
                            break
                        }
                    }
                    
                    if (employerParts.isNotEmpty()) {
                        extractedData["employer"] = employerParts.joinToString(" ").trim()
                        break
                    }
                }
            }
        }
        Log.d(TAG, "Extracted Employer: ${extractedData["employer"]}")
        
        // Extract Issuing Place with enhanced UAE emirate validation
        val issuingPlacePatterns = listOf(
            Regex("Issuing Place\\s*:?\\s*([A-Za-z\\s]+)"),
            Regex("مكان الإصدار\\s*:?\\s*([A-Za-z\\s]+)"),
            Regex("(?i)issuing\\s*place[^a-z]*([^\\n]{3,30})"),
            Regex("(?i)مكان\\s*الإصدار[^أ-ي]*([^\\n]{3,30})")
        )
        
        for (pattern in issuingPlacePatterns) {
            val match = pattern.find(text)
            if (match != null && match.groups.size > 1) {
                var issuingPlace = match.groups[1]?.value?.trim()
                if (!issuingPlace.isNullOrBlank()) {
                    // Clean up the issuing place string
                    issuingPlace = issuingPlace.replace(":", "").trim()
                    issuingPlace = validateAndNormalizeEmiratePlace(issuingPlace)
                    if (issuingPlace.isNotEmpty()) {
                        extractedData["issuingPlace"] = issuingPlace
                        break
                    }
                }
            }
        }
        
        // Fallback: Look for emirate names which could be issuing places
        if (extractedData["issuingPlace"].isNullOrBlank()) {
            val validatedPlace = findValidEmirateInText(text)
            if (validatedPlace.isNotEmpty()) {
                extractedData["issuingPlace"] = validatedPlace
            }
        }
        
        Log.d(TAG, "Extracted Issuing Place: ${extractedData["issuingPlace"]}")
        
        // Process MRZ data for additional validation and fallback data
        val mrzLines = lines.filter { it.count { c -> c == '<' } > 5 }
        if (mrzLines.isNotEmpty()) {
            extractedData["mrzData"] = mrzLines.joinToString("\\n")
            
            // Extract ID number from MRZ if not found earlier
            if (extractedData["idNumber"].isNullOrBlank()) {
                val mrzIdPattern = Regex("784\\d{4}7\\d{7}")
                val mrzIdMatch = mrzIdPattern.find(mrzLines.joinToString(""))
                if (mrzIdMatch != null) {
                    val idNumber = mrzIdMatch.value
                    if (idNumber.length >= 15) {
                        val formatted = "${idNumber.substring(0, 3)}-${idNumber.substring(3, 7)}-${idNumber.substring(7, 14)}-${idNumber.substring(14)}"
                        extractedData["idNumber"] = formatted
                    }
                }
            }
            
            // Extract name from MRZ if not found earlier  
            if (extractedData["nameEn"].isNullOrBlank()) {
                for (mrzLine in mrzLines) {
                    if (mrzLine.length > 30) {
                        val nameMatch = Regex("([A-Z]+)<+([A-Z<]+)").find(mrzLine)
                        if (nameMatch != null && nameMatch.groups.size > 2) {
                            val lastName = nameMatch.groups[1]?.value?.replace("<", " ")?.trim()
                            val firstName = nameMatch.groups[2]?.value?.replace("<", " ")?.trim()
                            if (!lastName.isNullOrBlank() && !firstName.isNullOrBlank()) {
                                extractedData["nameEn"] = "$firstName $lastName"
                                extractedData["fullName"] = "$firstName $lastName" // Backward compatibility
                            }
                        }
                    }
                }
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
        // Full cleanup of resources
        try {
            // Clear ImageAnalysis first to stop any ongoing processing
            imageAnalyzer?.clearAnalyzer()
            imageAnalyzer = null
            
            // Unbind camera
            val cameraProvider = ProcessCameraProvider.getInstance(this).get()
            cameraProvider.unbindAll()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing camera in onDestroy: ${e.message}")
        }
        
        // Shutdown executors and close resources
        cameraExecutor.shutdown()
        textRecognizer.close()
        
        // Reset all state
        camera = null
        imageCapture = null
        isCapturing = false
        captureStartTime = 0L
    }
    
    override fun onResume() {
        super.onResume()
        // Restart camera when activity resumes, but only if we haven't completed scanning
        if (scanningStep != ScanningStep.COMPLETED && ::viewFinder.isInitialized) {
            // Small delay to ensure the surface is ready
            lifecycleScope.launch {
                delay(200)
                runOnUiThread {
                    if (scanningStep != ScanningStep.COMPLETED) {
                        startCamera()
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Release camera resources when activity is paused
        try {
            // Unbind camera when paused to release resources
            val cameraProvider = ProcessCameraProvider.getInstance(this).get()
            cameraProvider.unbindAll()
            
            // Clear ImageAnalysis to stop processing immediately
            imageAnalyzer?.clearAnalyzer()
            
            // Set camera references to null to avoid any potential memory leaks
            camera = null
            imageCapture = null
            imageAnalyzer = null
            isCapturing = false  // Reset capture state
        } catch (exc: Exception) {
            Log.e(TAG, "Error unbinding camera: ${exc.message}")
        }
    }
    
    private fun restartCameraForNextStep() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "=== Starting camera restart for ${scanningStep.name} side ===")
                Log.d(TAG, "Current isCapturing state before restart: $isCapturing")
                
                // First, completely stop the camera and clear ImageAnalysis
                val cameraProvider = ProcessCameraProvider.getInstance(this@EmiratesIdScannerActivity).get()
                cameraProvider.unbindAll()
                Log.d(TAG, "Camera provider unbound all use cases")
                
                // Clear camera references and reset capture state
                camera = null
                imageCapture = null
                imageAnalyzer?.clearAnalyzer()  // Clear the analyzer to stop processing
                imageAnalyzer = null
                isCapturing = false  // Reset capture state for next step
                captureStartTime = 0L
                Log.d(TAG, "Camera resources cleared and isCapturing reset to false")
                
                // Wait a bit to ensure resources are fully released
                delay(1000) // Increased delay for better resource cleanup
                
                // Update UI first
                runOnUiThread {
                    updateInstruction()
                    Log.d(TAG, "Instruction updated for ${scanningStep.name} side")
                }
                
                // Small additional delay for UI update
                delay(500)
                
                // Restart camera
                runOnUiThread {
                    if (scanningStep != ScanningStep.COMPLETED) {
                        Log.d(TAG, "About to restart camera for ${scanningStep.name} side")
                        startCamera()
                        Log.d(TAG, "=== Camera restarted successfully for ${scanningStep.name} side ===")
                    } else {
                        Log.d(TAG, "Skipping camera restart - scanning completed")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart camera: ${e.message}", e)
                
                // Enhanced fallback recovery
                try {
                    Log.w(TAG, "Attempting fallback camera restart...")
                    delay(2000) // Longer delay for fallback
                    
                    runOnUiThread {
                        if (scanningStep != ScanningStep.COMPLETED) {
                            isCapturing = false  // Ensure state is reset
                            
                            try {
                                startCamera()
                                Log.d(TAG, "Camera restarted with fallback method")
                                
                                // Show success message to user
                                when (scanningStep) {
                                    ScanningStep.FRONT -> instructionText.text = "قُم بمسح الوجه الأمامي للهوية"
                                    ScanningStep.BACK -> instructionText.text = "قُم بمسح الوجه الخلفي للهوية"
                                    ScanningStep.COMPLETED -> return@runOnUiThread
                                }
                                
                            } catch (e2: Exception) {
                                Log.e(TAG, "Fallback camera restart also failed: ${e2.message}")
                                instructionText.text = "خطأ في إعادة تشغيل الكاميرا. يرجى إعادة المحاولة"
                                isCapturing = false
                                
                                // Final attempt after user sees error message
                                lifecycleScope.launch {
                                    delay(3000)
                                    try {
                                        startCamera()
                                        updateInstruction()
                                        Log.d(TAG, "Final camera restart attempt succeeded")
                                    } catch (e3: Exception) {
                                        Log.e(TAG, "All camera restart attempts failed")
                                        finishWithError("Failed to restart camera for ${scanningStep.name} side: ${e3.message}")
                                    }
                                }
                            }
                        }
                    }
                } catch (e2: Exception) {
                    Log.e(TAG, "Fallback camera restart also failed: ${e2.message}")
                    // If we still can't restart, show an error to user
                    runOnUiThread {
                        instructionText.text = "خطأ في إعادة تشغيل الكاميرا. يرجى إعادة المحاولة"
                        isCapturing = false  // Reset state even on error
                    }
                }
            }
        }
    }
    
    private fun transitionToBackSideScanning() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Starting transition from FRONT to BACK side scanning")
                
                // Step 1: Wait for UI message to be visible
                delay(2000) // Give user time to read the success message
                
                // Step 2: Set the scanning step to BACK
                scanningStep = ScanningStep.BACK
                Log.d(TAG, "Scanning step changed to: ${scanningStep.name}")
                
                // Step 3: Update instruction text to back side
                runOnUiThread {
                    updateInstruction()
                    Log.d(TAG, "UI updated for back side scanning")
                }
                
                // Step 4: Wait a bit more for UI update
                delay(500)
                
                // Step 5: Restart camera for back side with proper error handling
                restartCameraForNextStep()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during transition to back side: ${e.message}", e)
                runOnUiThread {
                    instructionText.text = "خطأ في الانتقال إلى المسح الخلفي. يرجى إعادة المحاولة"
                    isCapturing = false
                    // Try to restart anyway after a delay
                    lifecycleScope.launch {
                        delay(3000)
                        try {
                            scanningStep = ScanningStep.BACK
                            restartCameraForNextStep()
                        } catch (e2: Exception) {
                            Log.e(TAG, "Failed to recover from transition error: ${e2.message}")
                            finishWithError("Failed to transition to back side scanning: ${e2.message}")
                        }
                    }
                }
            }
        }
    }

    /**
     * Validates and normalizes UAE emirate place names
     * Returns normalized English name or empty string if not valid
     */
    private fun validateAndNormalizeEmiratePlace(place: String): String {
        val cleanPlace = place.uppercase().trim()
        
        // Map of possible variations to standard English names
        val emirateMap = mapOf(
            // Abu Dhabi variations
            "ABU DHABI" to "Abu Dhabi",
            "ABUDHABI" to "Abu Dhabi", 
            "أبوظبي" to "Abu Dhabi",
            "أبو ظبي" to "Abu Dhabi",
            
            // Dubai variations
            "DUBAI" to "Dubai",
            "دبي" to "Dubai",
            
            // Sharjah variations
            "SHARJAH" to "Sharjah",
            "الشارقة" to "Sharjah",
            
            // Al Ain variations
            "AL AIN" to "Al Ain",
            "ALAIN" to "Al Ain",
            "AL-AIN" to "Al Ain",
            "العين" to "Al Ain",
            
            // Ajman variations
            "AJMAN" to "Ajman",
            "عجمان" to "Ajman",
            
            // Fujairah variations
            "FUJAIRAH" to "Fujairah",
            "الفجيرة" to "Fujairah",
            
            // Ras Al Khaimah variations
            "RAS AL KHAIMAH" to "Ras Al Khaimah",
            "RAS AL-KHAIMAH" to "Ras Al Khaimah",
            "RASALKHAIMAH" to "Ras Al Khaimah",
            "رأس الخيمة" to "Ras Al Khaimah",
            
            // Umm Al Quwain variations
            "UMM AL QUWAIN" to "Umm Al Quwain",
            "UMM AL-QUWAIN" to "Umm Al Quwain",
            "UMMALQUWAIN" to "Umm Al Quwain",
            "أم القيوين" to "Umm Al Quwain"
        )
        
        // Try exact match first
        emirateMap[cleanPlace]?.let { return it }
        
        // Try partial matches for cases where OCR adds/removes characters
        for ((key, value) in emirateMap) {
            if (cleanPlace.contains(key) || key.contains(cleanPlace)) {
                // Additional validation to prevent false positives
                val similarity = calculateStringSimilarity(cleanPlace, key)
                if (similarity > 0.7) { // 70% similarity threshold
                    return value
                }
            }
        }
        
        return ""
    }
    
    /**
     * Finds valid UAE emirate names in the given text
     */
    private fun findValidEmirateInText(text: String): String {
        val cleanText = text.uppercase()
        
        // List of all valid UAE emirate patterns
        val emiratePatterns = listOf(
            "ABU DHABI", "أبوظبي", "أبو ظبي",
            "DUBAI", "دبي",
            "SHARJAH", "الشارقة", 
            "AL AIN", "العين", "ALAIN",
            "AJMAN", "عجمان",
            "FUJAIRAH", "الفجيرة",
            "RAS AL KHAIMAH", "رأس الخيمة", "RASALKHAIMAH",
            "UMM AL QUWAIN", "أم القيوين", "UMMALQUWAIN"
        )
        
        for (pattern in emiratePatterns) {
            if (cleanText.contains(pattern)) {
                return validateAndNormalizeEmiratePlace(pattern)
            }
        }
        
        return ""
    }
    
    /**
     * Calculates string similarity using Levenshtein distance
     */
    private fun calculateStringSimilarity(s1: String, s2: String): Double {
        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1
        
        if (longer.isEmpty()) return 1.0
        
        val distance = levenshteinDistance(longer, shorter)
        return (longer.length - distance) / longer.length.toDouble()
    }
    
    /**
     * Calculates Levenshtein distance between two strings
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                dp[i][j] = if (s1[i - 1] == s2[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        
        return dp[s1.length][s2.length]
    }
}
