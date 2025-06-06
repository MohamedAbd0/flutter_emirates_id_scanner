import UIKit
import Foundation
import Vision
import AVFoundation

class EmiratesIdScannerViewController: UIViewController {
    
    // MARK: - Properties
    private var captureSession: AVCaptureSession!
    private var videoPreviewLayer: AVCaptureVideoPreviewLayer!
    private var capturePhotoOutput: AVCapturePhotoOutput!
    
    private var overlayView: UIView!
    private var instructionLabel: UILabel!
    private var closeButton: UIButton!
    
    private var scanningStep: ScanningStep = .front
    private var frontImagePath: String?
    private var backImagePath: String?
    private var frontSideContent: String?
    private var backSideContent: String?
    private var extractedData: [String: String?] = [:]
    
    // Rectangle bounds for cropping
    private var rectangleBounds: CGRect = .zero
    
    var onScanComplete: ((Result<[String: Any?], Error>) -> Void)?
    
    enum ScanningStep {
        case front, back, completed
    }
    
    // MARK: - Lifecycle
    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
        requestCameraPermission()
    }
    
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        // Start or restart the capture session when view appears
        if captureSession?.isRunning == false {
            DispatchQueue.global(qos: .userInitiated).async {
                self.captureSession.startRunning()
            }
        }
    }
    
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        // Stop the capture session when view is about to disappear
        if captureSession?.isRunning == true {
            captureSession.stopRunning()
        }
    }
    
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        // Clean up additional resources if needed
        if captureSession?.isRunning == true {
            captureSession.stopRunning()
        }
    }
    
    // MARK: - Camera Session Management
    
    private func restartCameraSession() {
        // Stop the current capture session
        if captureSession?.isRunning == true {
            captureSession.stopRunning()
        }
        
        // Clean up resources
        if let inputs = captureSession?.inputs {
            for input in inputs {
                captureSession.removeInput(input)
            }
        }
        
        if let outputs = captureSession?.outputs {
            for output in outputs {
                captureSession.removeOutput(output)
            }
        }
        
        // Re-setup the camera inputs and outputs
        setupCameraInputsAndOutputs()
        
        // Restart the session
        DispatchQueue.global(qos: .userInitiated).async {
            self.captureSession.startRunning()
        }
    }
    
    private func setupCameraInputsAndOutputs() {
        guard let backCamera = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) else {
            handleError("Unable to access back camera")
            return
        }
        
        do {
            let input = try AVCaptureDeviceInput(device: backCamera)
            capturePhotoOutput = AVCapturePhotoOutput()
            
            if captureSession.canAddInput(input) && captureSession.canAddOutput(capturePhotoOutput) {
                captureSession.addInput(input)
                captureSession.addOutput(capturePhotoOutput)
            }
        } catch {
            handleError("Error setting up camera: \(error.localizedDescription)")
        }
    }
    
    // MARK: - UI Setup
    private func setupUI() {
        view.backgroundColor = .black
        
        setupOverlay()
        setupInstructionLabel()
        setupCloseButton()
        updateInstruction()
    }
    
    private func setupOverlay() {
        overlayView = CardOverlayView(frame: view.bounds)
        overlayView.backgroundColor = .clear
        view.addSubview(overlayView)
        
        overlayView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            overlayView.topAnchor.constraint(equalTo: view.topAnchor),
            overlayView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            overlayView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            overlayView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        
        // Update rectangle bounds after layout
        DispatchQueue.main.async {
            if let cardOverlay = self.overlayView as? CardOverlayView {
                self.rectangleBounds = cardOverlay.rectangleBounds
            }
        }
    }
    
    private func setupInstructionLabel() {
        instructionLabel = UILabel()
        instructionLabel.textColor = .white
        instructionLabel.font = UIFont.systemFont(ofSize: 18, weight: .medium)
        instructionLabel.textAlignment = .center
        instructionLabel.numberOfLines = 0
        instructionLabel.backgroundColor = UIColor.black.withAlphaComponent(0.7)
        instructionLabel.layer.cornerRadius = 8
        instructionLabel.clipsToBounds = true
        
        view.addSubview(instructionLabel)
        instructionLabel.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            instructionLabel.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 50),
            instructionLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            instructionLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            instructionLabel.heightAnchor.constraint(greaterThanOrEqualToConstant: 50)
        ])
    }
    
    private func setupCloseButton() {
        closeButton = UIButton(type: .system)
        closeButton.setTitle("✕", for: .normal)
        closeButton.titleLabel?.font = UIFont.systemFont(ofSize: 24, weight: .bold)
        closeButton.setTitleColor(.white, for: .normal)
        closeButton.backgroundColor = UIColor.black.withAlphaComponent(0.7)
        closeButton.layer.cornerRadius = 25
        closeButton.addTarget(self, action: #selector(closeButtonTapped), for: .touchUpInside)
        
        view.addSubview(closeButton)
        closeButton.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            closeButton.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 20),
            closeButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            closeButton.widthAnchor.constraint(equalToConstant: 50),
            closeButton.heightAnchor.constraint(equalToConstant: 50)
        ])
    }
    
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        if let videoPreviewLayer = videoPreviewLayer {
            videoPreviewLayer.frame = view.bounds
        }
        overlayView.setNeedsDisplay()
    }
    
    override func draw(_ rect: CGRect) {
        super.draw(rect)
    }
    
    private func drawCardFrame() {
        // This method is no longer used as drawing is handled by CardOverlayView
    }
    
    // MARK: - Camera Setup
    private func requestCameraPermission() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            setupCamera()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                DispatchQueue.main.async {
                    if granted {
                        self.setupCamera()
                    } else {
                        self.handleError("Camera permission denied")
                    }
                }
            }
        case .denied, .restricted:
            handleError("Camera permission denied")
        @unknown default:
            handleError("Unknown camera permission status")
        }
    }
    
    private func setupCamera() {
        captureSession = AVCaptureSession()
        captureSession.sessionPreset = .photo
        
        setupCameraInputsAndOutputs()
        
        videoPreviewLayer = AVCaptureVideoPreviewLayer(session: captureSession)
        videoPreviewLayer.videoGravity = .resizeAspectFill
        videoPreviewLayer.connection?.videoOrientation = .portrait
        
        view.layer.insertSublayer(videoPreviewLayer, at: 0)
        
        DispatchQueue.global(qos: .userInitiated).async {
            self.captureSession.startRunning()
            
            DispatchQueue.main.async {
                self.videoPreviewLayer.frame = self.view.bounds
            }
        }
        
        setupVideoOutput()
    }
    
    private func setupVideoOutput() {
        let videoOutput = AVCaptureVideoDataOutput()
        videoOutput.setSampleBufferDelegate(self, queue: DispatchQueue.global(qos: .userInitiated))
        
        if captureSession.canAddOutput(videoOutput) {
            captureSession.addOutput(videoOutput)
        }
    }
    
    // MARK: - Actions
    @objc private func closeButtonTapped() {
        let error = NSError(domain: "EmiratesIdScanner", code: 1, userInfo: [NSLocalizedDescriptionKey: "User cancelled"])
        onScanComplete?(.failure(error))
        dismiss(animated: true)
    }
    
    private func updateInstruction() {
        let instruction: String
        switch scanningStep {
        case .front:
            instruction = "قُم بمسح الوجه الأمامي للهوية"
        case .back:
            instruction = "قُم بمسح الوجه الخلفي للهوية"
        case .completed:
            instruction = "جيد"
        }
        
        DispatchQueue.main.async {
            self.instructionLabel.text = instruction
        }
    }
    
    // MARK: - Image Processing Helpers
    private func cropImageToRectangle(originalPath: String, outputPath: String) -> Bool {
        guard let originalImage = UIImage(contentsOfFile: originalPath) else {
            print("Failed to load image from: \(originalPath)")
            return false
        }
        
        print("Original image size: \(originalImage.size)")
        
        // Get the preview layer dimensions
        let previewSize = videoPreviewLayer.bounds.size
        print("Preview layer size: \(previewSize)")
        
        // Calculate Emirates ID card dimensions (must match overlay calculations exactly)
        let cardWidth = previewSize.width * 0.85  // Same as overlay
        let cardHeight = cardWidth * 0.63 // Emirates ID aspect ratio
        
        let centerX = previewSize.width / 2
        let centerY = previewSize.height / 2
        
        let rectLeft = centerX - cardWidth / 2
        let rectTop = centerY - cardHeight / 2
        
        let rectangleFrame = CGRect(x: rectLeft, y: rectTop, width: cardWidth, height: cardHeight)
        print("Card rectangle: \(rectangleFrame)")
        
        // Calculate how the camera preview maps to the captured image
        let imageSize = originalImage.size
        let imageAspectRatio = imageSize.width / imageSize.height
        let previewAspectRatio = previewSize.width / previewSize.height
        
        let scaleX: CGFloat
        let scaleY: CGFloat
        let offsetX: CGFloat
        let offsetY: CGFloat
        
        if imageAspectRatio > previewAspectRatio {
            // Image is wider than preview - image is cropped horizontally
            scaleY = imageSize.height / previewSize.height
            scaleX = scaleY
            offsetY = 0
            offsetX = (imageSize.width - previewSize.width * scaleX) / 2
        } else {
            // Image is taller than preview - image is cropped vertically
            scaleX = imageSize.width / previewSize.width
            scaleY = scaleX
            offsetX = 0
            offsetY = (imageSize.height - previewSize.height * scaleY) / 2
        }
        
        print("Scale: x=\(scaleX), y=\(scaleY), Offset: x=\(offsetX), y=\(offsetY)")
        
        // Map preview coordinates to image coordinates
        let cropRect = CGRect(
            x: rectLeft * scaleX + offsetX,
            y: rectTop * scaleY + offsetY,
            width: cardWidth * scaleX,
            height: cardHeight * scaleY
        )
        
        // Ensure crop rect is within image bounds
        let clampedRect = CGRect(
            x: max(0, cropRect.origin.x),
            y: max(0, cropRect.origin.y),
            width: min(imageSize.width - max(0, cropRect.origin.x), cropRect.width),
            height: min(imageSize.height - max(0, cropRect.origin.y), cropRect.height)
        )
        
        print("Crop rect: \(clampedRect)")
        
        guard clampedRect.width > 0 && clampedRect.height > 0,
              let cgImage = originalImage.cgImage?.cropping(to: clampedRect) else {
            print("Invalid crop dimensions or failed to crop")
            return false
        }
        
        let croppedImage = UIImage(cgImage: cgImage, scale: originalImage.scale, orientation: originalImage.imageOrientation)
        
        guard let croppedImageData = croppedImage.jpegData(compressionQuality: 0.95) else {
            print("Failed to convert cropped image to JPEG")
            return false
        }
        
        do {
            try croppedImageData.write(to: URL(fileURLWithPath: outputPath))
            print("Cropped image saved: \(outputPath) (\(croppedImage.size))")
            return true
        } catch {
            print("Failed to save cropped image: \(error)")
            return false
        }
    }
    
    private func captureImage() {
        let settings = AVCapturePhotoSettings()
        settings.flashMode = .off
        
        capturePhotoOutput.capturePhoto(with: settings, delegate: self)
    }
    
    private func processCompleted() {
        Task {
            do {
                try await extractDataFromImages()
                
                let result: [String: Any?] = [
                    "fullName": extractedData["fullName"] ?? nil,
                    "idNumber": extractedData["idNumber"] ?? nil,
                    "nationality": extractedData["nationality"] ?? nil,
                    "dateOfBirth": extractedData["dateOfBirth"] ?? nil,
                    "issueDate": extractedData["issueDate"] ?? nil,
                    "expiryDate": extractedData["expiryDate"] ?? nil,
                    "frontImagePath": frontImagePath,
                    "backImagePath": backImagePath,
                    "cardNumber": extractedData["cardNumber"] ?? nil,
                    "occupation": extractedData["occupation"] ?? nil,
                    "employer": extractedData["employer"] ?? nil,
                    "issuingPlace": extractedData["issuingPlace"] ?? nil,
                    "mrzData": extractedData["mrzData"] ?? nil
                ]
                
                DispatchQueue.main.async {
                    self.onScanComplete?(.success(result))
                    self.dismiss(animated: true)
                }
            } catch {
                DispatchQueue.main.async {
                    self.handleError("Failed to process images: \(error.localizedDescription)")
                }
            }
        }
    }
    
    private func handleError(_ message: String) {
        let error = NSError(domain: "EmiratesIdScanner", code: 2, userInfo: [NSLocalizedDescriptionKey: message])
        onScanComplete?(.failure(error))
        dismiss(animated: true)
    }
    
    /**
     * Checks if the current scanned side is a duplicate of a previously scanned side
     * based on text content comparison instead of image hash
     */
    private func isDuplicateCardSide(_ newText: String, isFrontSide: Bool) -> Bool {
        // Clean the text for better comparison
        let cleanedText = newText.replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression).uppercased()
        
        if isFrontSide {
            // Check if this front side text is similar to already scanned back side content
            if let backText = backSideContent {
                // Content similarity check
                return hasSimilarContent(cleanedText, backText)
            }
        } else {
            // Check if this back side text is similar to already scanned front side content
            if let frontText = frontSideContent {
                return hasSimilarContent(cleanedText, frontText)
            }
        }
        
        return false
    }
    
    /**
     * Compares two text contents to determine if they likely represent the same side of a card
     * This uses multiple criteria to make the decision more robust
     */
    private func hasSimilarContent(_ text1: String, _ text2: String) -> Bool {
        // --- SPECIFIC ID NUMBER CHECK ---
        // If both texts contain the same ID number pattern, they are likely the same
        let idPattern = try? NSRegularExpression(pattern: "784-\\d{4}-\\d{7}-\\d{1}")
        let range1 = NSRange(location: 0, length: text1.utf16.count)
        let range2 = NSRange(location: 0, length: text2.utf16.count)
        
        let idMatches1 = idPattern?.matches(in: text1, options: [], range: range1).map {
            String(text1[Range($0.range, in: text1)!])
        } ?? []
        
        let idMatches2 = idPattern?.matches(in: text2, options: [], range: range2).map {
            String(text2[Range($0.range, in: text2)!])
        } ?? []
        
        // If both sides have the same ID number, they are definitely the same side
        if !idMatches1.isEmpty && !idMatches2.isEmpty && idMatches1.contains(where: { idMatches2.contains($0) }) {
            print("Same ID number found on both scans")
            return true
        }
        
        // --- FRONT SIDE SPECIFIC CHECK ---
        // Check for key phrases that should appear only on front side
        let frontSideKeywords = [
            "UNITED ARAB EMIRATES", "الإمارات العربية المتحدة", "FEDERAL AUTHORITY",
            "IDENTITY CARD", "بطاقة هوية", "RESIDENT IDENTITY CARD"
        ]
        
        var frontSideMatches1 = 0
        var frontSideMatches2 = 0
        
        for keyword in frontSideKeywords {
            if text1.contains(keyword) { frontSideMatches1 += 1 }
            if text2.contains(keyword) { frontSideMatches2 += 1 }
        }
        
        // If both texts have multiple front side indicators, they are likely both front sides
        let isBothFront = frontSideMatches1 >= 2 && frontSideMatches2 >= 2
        
        // --- BACK SIDE SPECIFIC CHECK ---
        // Check for key phrases that should appear only on back side
        let backSideKeywords = [
            "CARD NUMBER", "رقم البطاقة", "OCCUPATION", "المهنة",
            "EMPLOYER", "صاحب العمل", "ISSUING PLACE", "مكان الإصدار"
        ]
        
        var backSideMatches1 = 0
        var backSideMatches2 = 0
        
        for keyword in backSideKeywords {
            if text1.contains(keyword) { backSideMatches1 += 1 }
            if text2.contains(keyword) { backSideMatches2 += 1 }
        }
        
        // Check for MRZ pattern which is specific to back side
        let hasMrzPattern1 = text1.contains("<<<<<<") || text1.filter { $0 == "<" }.count > 5
        let hasMrzPattern2 = text2.contains("<<<<<<") || text2.filter { $0 == "<" }.count > 5
        
        if hasMrzPattern1 { backSideMatches1 += 1 }
        if hasMrzPattern2 { backSideMatches2 += 1 }
        
        // If both texts have multiple back side indicators, they are likely both back sides
        let isBothBack = backSideMatches1 >= 2 && backSideMatches2 >= 2
        
        // Log result for debugging
        if isBothFront || isBothBack {
            print("Duplicate side detected: isBothFront=\(isBothFront), isBothBack=\(isBothBack)")
        }
        
        return isBothFront || isBothBack
    }
}

// MARK: - AVCaptureVideoDataOutputSampleBufferDelegate
extension EmiratesIdScannerViewController: AVCaptureVideoDataOutputSampleBufferDelegate {
    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        
        let request = VNRecognizeTextRequest { [weak self] request, error in
            guard let self = self, let observations = request.results as? [VNRecognizedTextObservation] else { return }
            
            let recognizedText = observations.compactMap { observation in
                observation.topCandidates(1).first?.string
            }.joined(separator: "\n")
            
            self.processTextResult(recognizedText)
        }
        
        request.recognitionLevel = .accurate
        
        let handler = VNImageRequestHandler(cvPixelBuffer: pixelBuffer, options: [:])
        try? handler.perform([request])
    }
    
    private func processTextResult(_ text: String) {
        let isValidCard: Bool
        
        switch scanningStep {
        case .front:
            isValidCard = isValidFrontSide(text)
        case .back:
            isValidCard = isValidBackSide(text)
        case .completed:
            return
        }
        
        if isValidCard {
            DispatchQueue.main.async {
                self.instructionLabel.text = "جيد"
            }
            
            // Delay capture to show "Good" message
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                self.captureImage()
            }
        }
    }
    
    private func isValidFrontSide(_ text: String) -> Bool {
        let cleanText = text.replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression).uppercased()
        
        // Check for ID number with pattern 784-YYYY-XXXXXXX-X (where 784 is UAE country code)
        let hasIdNumber = text.range(of: "784-\\d{4}-\\d{7}-\\d{1}", options: .regularExpression) != nil
        
        // Check for key header text
        let hasHeaderText = cleanText.contains("UNITED ARAB EMIRATES") || 
                          cleanText.contains("الإمارات العربية المتحدة") ||
                          cleanText.contains("FEDERAL AUTHORITY")
        
        // Check for nationality field
        let hasNationality = cleanText.contains("NATIONALITY") || cleanText.contains("الجنسية")
        
        // Check for date patterns (DD/MM/YYYY)
        let hasDateFormat = text.range(of: "\\d{2}/\\d{2}/\\d{4}", options: .regularExpression) != nil
        
        // ID Card specific fields
        let hasCardText = cleanText.contains("IDENTITY CARD") || 
                        cleanText.contains("بطاقة هوية") || 
                        cleanText.contains("RESIDENT IDENTITY CARD")
        
        // Return true if we have at least two strong indicators
        return (hasIdNumber || (hasHeaderText && (hasDateFormat || hasNationality || hasCardText)))
    }
    
    private func isValidBackSide(_ text: String) -> Bool {
        let cleanText = text.replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression).uppercased()
        
        // Check for Card Number label which is specific to the back side
        let hasCardNumber = cleanText.contains("CARD NUMBER") || cleanText.contains("رقم البطاقة")
        
        // Check for occupation field which appears on back side
        let hasOccupation = cleanText.contains("OCCUPATION") || cleanText.contains("المهنة")
        
        // Check for employer field which appears on back side
        let hasEmployer = cleanText.contains("EMPLOYER") || cleanText.contains("صاحب العمل")
        
        // Check for issuing place field which appears on back side
        let hasIssuingPlace = cleanText.contains("ISSUING PLACE") || cleanText.contains("مكان الإصدار")
        
        // Check for specific places like Abu Dhabi, Dubai, etc.
        let hasEmirateLocation = cleanText.contains("ABU DHABI") || 
                               cleanText.contains("DUBAI") || 
                               cleanText.contains("SHARJAH") || 
                               cleanText.contains("أبوظبي") || 
                               cleanText.contains("دبي") || 
                               cleanText.contains("الشارقة")
        
        // Check for the machine readable zone (MRZ) pattern with multiple '<' characters 
        let hasMrzPattern = cleanText.contains("<<<<<<") || text.filter { $0 == "<" }.count > 5
        
        // Check for electronic chip references
        let hasChipInfo = cleanText.contains("CHIP") || 
                        text.range(of: "\\d{8,}", options: .regularExpression) != nil // Long numerical sequences for chip ID
        
        // Check for characteristic notice text on the back
        let hasNotice = cleanText.contains("PLEASE RETURN") || 
                      cleanText.contains("POLICE STATION") ||
                      cleanText.contains("الرجاء إعادة") ||
                      cleanText.contains("مركز شرطة")
        
        // Return true if we have at least two strong indicators of back side
        return ((hasCardNumber || hasOccupation || hasEmployer || hasIssuingPlace) && 
                (hasMrzPattern || hasChipInfo || hasEmirateLocation || hasNotice))
    }
}

// MARK: - AVCapturePhotoCaptureDelegate
extension EmiratesIdScannerViewController: AVCapturePhotoCaptureDelegate {
    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        guard error == nil, let imageData = photo.fileDataRepresentation() else {
            handleError("Failed to capture image")
            return
        }
        
        let timestamp = DateFormatter().string(from: Date()).replacingOccurrences(of: " ", with: "_")
        let tempFileName: String
        let finalFileName: String
        
        switch scanningStep {
        case .front:
            tempFileName = "emirates_id_front_temp_\(timestamp).jpg"
            finalFileName = "emirates_id_front_\(timestamp).jpg"
        case .back:
            tempFileName = "emirates_id_back_temp_\(timestamp).jpg"
            finalFileName = "emirates_id_back_\(timestamp).jpg"
        case .completed:
            return
        }
        
        let documentsPath = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        let tempImagePath = documentsPath.appendingPathComponent(tempFileName)
        let finalImagePath = documentsPath.appendingPathComponent(finalFileName)
        
        do {
            // Save temp image first
            try imageData.write(to: tempImagePath)
            
            // Crop the image to rectangle area
            let cropSuccess = cropImageToRectangle(originalPath: tempImagePath.path, outputPath: finalImagePath.path)
            
            if !cropSuccess {
                print("Failed to crop image, but continuing...")
                // Show guidance message instead of error
                DispatchQueue.main.async {
                    switch self.scanningStep {
                    case .front:
                        self.instructionLabel.text = "يرجى إعادة مسح الوجه الأمامي للهوية"
                    case .back:
                        self.instructionLabel.text = "يرجى إعادة مسح الوجه الخلفي للهوية"
                    case .completed:
                        return
                    }
                }
                // Reset instruction after delay
                DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
                    self.updateInstruction()
                }
                // Clean up temp file and continue
                try? FileManager.default.removeItem(at: tempImagePath)
                return
            }
            
            switch scanningStep {
            case .front:
                // Extract text from image to check for front side indicators
                Task {
                    do {
                        let frontText = try await extractTextFromImage(at: finalImagePath.path)
                        let isFrontSide = isValidFrontSide(frontText)
                        
                        if !isFrontSide {
                            // This doesn't look like a front side
                            print("Image doesn't appear to be front side of Emirates ID")
                            // Show error message and stay on front scanning
                            DispatchQueue.main.async {
                                self.instructionLabel.text = "هذه ليست الواجهة الأمامية للبطاقة، يرجى مسح الوجه الأمامي"
                                
                                // Reset instruction after delay
                                DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
                                    self.updateInstruction()
                                }
                            }
                            // Delete the invalid image
                            try? FileManager.default.removeItem(at: finalImagePath)
                            return
                        }
                        
                        // Check if the front side is a duplicate of the back (in case user scanned back first)
                        if backSideContent != nil && isDuplicateCardSide(frontText, isFrontSide: true) {
                            print("This appears to be a duplicate of the back side")
                            DispatchQueue.main.async {
                                self.instructionLabel.text = "هذه نفس الواجهة الخلفية، يرجى مسح الوجه الأمامي للبطاقة"
                                
                                // Reset instruction after delay
                                DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
                                    self.updateInstruction()
                                }
                            }
                            // Delete the invalid image
                            try? FileManager.default.removeItem(at: finalImagePath)
                            return
                        }
                        
                        // Valid front side - continue
                        self.frontImagePath = finalImagePath.path
                        self.frontSideContent = frontText
                        self.scanningStep = .back
                        
                        // Show completion message for front side
                        DispatchQueue.main.async {
                            self.instructionLabel.text = "تم مسح الوجه الأمامي بنجاح. الآن قم بمسح الوجه الخلفي للهوية"
                            
                            // Restart the camera session to prevent resource issues
                            self.restartCameraSession()
                            
                            // Delay before showing back instruction
                            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                                self.updateInstruction()
                            }
                        }
                    } catch {
                        print("Error processing front image: \(error)")
                        DispatchQueue.main.async {
                            self.handleError("Failed to process image")
                        }
                    }
                }
            case .back:
                // Extract text from image to check for back side indicators
                Task {
                    do {
                        let backText = try await extractTextFromImage(at: finalImagePath.path)
                        
                        // First check if this is actually a back side
                        let isBackSide = self.isValidBackSide(backText)
                        if !isBackSide {
                            // This doesn't look like a back side
                            print("Image doesn't appear to be back side of Emirates ID")
                            // Show error message and stay on back scanning
                            DispatchQueue.main.async {
                                self.instructionLabel.text = "هذه ليست الواجهة الخلفية للبطاقة، يرجى مسح الوجه الخلفي"
                                
                                // Reset instruction after delay
                                DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
                                    self.updateInstruction()
                                }
                            }
                            // Delete the invalid image
                            try? FileManager.default.removeItem(at: finalImagePath)
                            return
                        }
                        
                        // Check if this is actually the same side as the front (duplicate scan)
                        if isDuplicateCardSide(backText, isFrontSide: false) {
                            print("This appears to be a duplicate of the front side")
                            DispatchQueue.main.async {
                                self.instructionLabel.text = "هذه نفس الواجهة الأمامية، يرجى مسح الوجه الخلفي للبطاقة"
                                
                                // Reset instruction after delay
                                DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
                                    self.updateInstruction()
                                }
                            }
                            // Delete the invalid image
                            try? FileManager.default.removeItem(at: finalImagePath)
                            return
                        }
                
                        // Valid back side - continue
                        backImagePath = finalImagePath.path
                        backSideContent = backText
                        scanningStep = .completed
                
                        // Show completion message for back side
                        DispatchQueue.main.async {
                            self.instructionLabel.text = "تم مسح الوجه الخلفي بنجاح. جاري معالجة البيانات..."
                            
                            // Release camera resources as they are no longer needed
                            if self.captureSession?.isRunning == true {
                                self.captureSession.stopRunning()
                            }
                        }
                
                        // Delay before processing
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                            self.processCompleted()
                        }
                    } catch {
                        print("Error processing back image: \(error)")
                        DispatchQueue.main.async {
                            self.handleError("Failed to process image")
                        }
                    }
                }
            case .completed:
                break
            }
            
            // Clean up temp file
            try? FileManager.default.removeItem(at: tempImagePath)
            
        } catch {
            handleError("Failed to save image: \(error.localizedDescription)")
        }
    }
}

// MARK: - Data Extraction
extension EmiratesIdScannerViewController {
    private func extractDataFromImages() async throws {
        if let frontPath = frontImagePath {
            let frontText = try await extractTextFromImage(at: frontPath)
            extractFrontSideData(from: frontText)
        }
        
        if let backPath = backImagePath {
            let backText = try await extractTextFromImage(at: backPath)
            extractBackSideData(from: backText)
        }
    }
    
    private func extractTextFromImage(at path: String) async throws -> String {
        return try await withCheckedThrowingContinuation { continuation in
            guard let image = UIImage(contentsOfFile: path),
                  let cgImage = image.cgImage else {
                continuation.resume(throwing: NSError(domain: "ImageError", code: 1, userInfo: nil))
                return
            }
            
            let request = VNRecognizeTextRequest { request, error in
                if let error = error {
                    continuation.resume(throwing: error)
                    return
                }
                
                guard let observations = request.results as? [VNRecognizedTextObservation] else {
                    continuation.resume(returning: "")
                    return
                }
                
                let recognizedText = observations.compactMap { observation in
                    observation.topCandidates(1).first?.string
                }.joined(separator: "\n")
                
                continuation.resume(returning: recognizedText)
            }
            
            request.recognitionLevel = .accurate
            let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
            
            do {
                try handler.perform([request])
            } catch {
                continuation.resume(throwing: error)
            }
        }
    }
    
    private func extractFrontSideData(from text: String) {
        let lines = text.components(separatedBy: .newlines)
        
        // Extract ID Number
        if let idMatch = text.range(of: "\\d{3}-\\d{4}-\\d{7}-\\d{1}", options: .regularExpression) {
            extractedData["idNumber"] = String(text[idMatch])
        }
        
        // Extract Name
        for line in lines {
            let trimmedLine = line.trimmingCharacters(in: .whitespaces)
            if trimmedLine.range(of: "[A-Za-z]{3,}", options: .regularExpression) != nil &&
               !trimmedLine.contains(where: { $0.isNumber }) &&
               trimmedLine.count > 3 {
                extractedData["fullName"] = trimmedLine
                break
            }
        }
        
        // Extract Nationality
        let nationalityKeywords = ["NATIONALITY", "الجنسية", "UNITED ARAB EMIRATES", "UAE"]
        for line in lines {
            for keyword in nationalityKeywords {
                if line.uppercased().contains(keyword) {
                    extractedData["nationality"] = line.trimmingCharacters(in: .whitespaces)
                    break
                }
            }
        }
    }
    
    private func extractBackSideData(from text: String) {
        let lines = text.components(separatedBy: .newlines)
        
        // Extract Card Number from back (often clearer than front)
        if let cardNumberRange = text.range(of: "(?:Card Number|رقم البطاقة)[^\\d]*(\\d+)", options: .regularExpression),
           let match = String(text[cardNumberRange]).range(of: "(\\d+)$", options: .regularExpression) {
            let cardNumber = String(String(text[cardNumberRange])[match]).trimmingCharacters(in: .whitespaces)
            if !cardNumber.isEmpty {
                extractedData["cardNumber"] = cardNumber
            }
        }
        
        // Extract Occupation
        if let occupationRange = text.range(of: "(?:Occupation|المهنة)[^:]*:[^\\n]*([^\\n]+)", options: .regularExpression) {
            let occupationText = String(text[occupationRange])
            if let colonIndex = occupationText.firstIndex(of: ":"),
               colonIndex != occupationText.endIndex {
                let occupationValue = String(occupationText[occupationText.index(after: colonIndex)...]).trimmingCharacters(in: .whitespaces)
                if !occupationValue.isEmpty {
                    extractedData["occupation"] = occupationValue
                }
            }
        }
        
        // Extract Employer
        if let employerRange = text.range(of: "(?:Employer|صاحب العمل)[^:]*:[^\\n]*([^\\n]+)", options: .regularExpression) {
            let employerText = String(text[employerRange])
            if let colonIndex = employerText.firstIndex(of: ":"),
               colonIndex != employerText.endIndex {
                let employerValue = String(employerText[employerText.index(after: colonIndex)...]).trimmingCharacters(in: .whitespaces)
                if !employerValue.isEmpty {
                    extractedData["employer"] = employerValue
                }
            }
        }
        
        // Extract Issuing Place
        if let issuingPlaceRange = text.range(of: "(?:Issuing Place|مكان الإصدار)[^:]*:[^\\n]*([^\\n]+)", options: .regularExpression) {
            let issuingPlaceText = String(text[issuingPlaceRange])
            if let colonIndex = issuingPlaceText.firstIndex(of: ":"),
               colonIndex != issuingPlaceText.endIndex {
                let issuingPlaceValue = String(issuingPlaceText[issuingPlaceText.index(after: colonIndex)...]).trimmingCharacters(in: .whitespaces)
                if !issuingPlaceValue.isEmpty {
                    extractedData["issuingPlace"] = issuingPlaceValue
                }
            }
        }
        
        // Extract MRZ data which may have more reliable info
        let mrzLines = lines.filter { $0.filter { $0 == "<" }.count > 5 }
        if !mrzLines.isEmpty {
            extractedData["mrzData"] = mrzLines.joined(separator: "\n")
            
            // Try to extract embedded ID number from MRZ (format should contain 784YYYY7XXXXXXX)
            let mrzText = mrzLines.joined()
            if let mrzIdRange = mrzText.range(of: "784\\d{4}7\\d{7}", options: .regularExpression),
               extractedData["idNumber"] == nil {
                let idNumber = String(mrzText[mrzIdRange])
                if idNumber.count >= 15 {
                    // Format the ID with dashes for consistency
                    let formatted = "\(idNumber.prefix(3))-\(idNumber.dropFirst(3).prefix(4))-\(idNumber.dropFirst(7).prefix(7))-\(idNumber.dropFirst(14).prefix(1))"
                    extractedData["idNumber"] = formatted
                }
            }
        }
        
        // Extract dates
        let dateMatches = text.ranges(of: "\\d{2}/\\d{2}/\\d{4}", options: .regularExpression)
        let dates = dateMatches.map { String(text[$0]) }
        
        if dates.count >= 2 {
            extractedData["issueDate"] = dates[0]
            extractedData["expiryDate"] = dates[1]
        }
        
        // Extract Date of Birth
        for line in lines {
            if line.contains("BIRTH") || line.contains("الميلاد") || line.contains("DOB") {
                if let dobRange = line.range(of: "\\d{2}/\\d{2}/\\d{4}", options: .regularExpression) {
                    extractedData["dateOfBirth"] = String(line[dobRange])
                    break
                }
            }
        }
    }
}

// Extension to find all ranges of a regex pattern
extension String {
    func ranges(of pattern: String, options: String.CompareOptions = []) -> [Range<String.Index>] {
        var ranges: [Range<String.Index>] = []
        var searchRange = startIndex..<endIndex
        
        while let range = range(of: pattern, options: options, range: searchRange) {
            ranges.append(range)
            searchRange = range.upperBound..<endIndex
        }
        
        return ranges
    }
}

// MARK: - CardOverlayView
class CardOverlayView: UIView {
    
    private(set) var rectangleBounds: CGRect = .zero
    
    override func draw(_ rect: CGRect) {
        super.draw(rect)
        
        guard let context = UIGraphicsGetCurrentContext() else { return }
        
        let centerX = bounds.width / 2
        let centerY = bounds.height / 2
        let cardWidth = bounds.width * 0.85  // Slightly larger for better visibility
        let cardHeight = cardWidth * 0.63 // Emirates ID aspect ratio (85.6mm × 53.98mm)
        
        let cardRect = CGRect(
            x: centerX - cardWidth / 2,
            y: centerY - cardHeight / 2,
            width: cardWidth,
            height: cardHeight
        )
        
        // Store rectangle bounds for cropping
        rectangleBounds = cardRect
        
        // Draw semi-black overlay outside scanning rectangle (darker overlay)
        context.setFillColor(UIColor.black.withAlphaComponent(0.7).cgColor) // More opaque black (70% opacity)
        
        // Top overlay
        context.fill(CGRect(x: 0, y: 0, width: bounds.width, height: cardRect.minY))
        
        // Bottom overlay
        context.fill(CGRect(x: 0, y: cardRect.maxY, width: bounds.width, height: bounds.height - cardRect.maxY))
        
        // Left overlay
        context.fill(CGRect(x: 0, y: cardRect.minY, width: cardRect.minX, height: cardRect.height))
        
        // Right overlay
        context.fill(CGRect(x: cardRect.maxX, y: cardRect.minY, width: bounds.width - cardRect.maxX, height: cardRect.height))
        
        // Draw ID card frame
        context.setStrokeColor(UIColor.white.cgColor)
        context.setLineWidth(3.0)
        context.stroke(cardRect)
        
        // Draw corner guides for better alignment
        let cornerLength: CGFloat = 40
        context.setStrokeColor(UIColor.systemGreen.cgColor) // Green color for corners
        context.setLineWidth(6.0)
        context.setLineCap(.round)
        
        // Top-left corner
        context.move(to: CGPoint(x: cardRect.minX, y: cardRect.minY))
        context.addLine(to: CGPoint(x: cardRect.minX + cornerLength, y: cardRect.minY))
        context.move(to: CGPoint(x: cardRect.minX, y: cardRect.minY))
        context.addLine(to: CGPoint(x: cardRect.minX, y: cardRect.minY + cornerLength))
        
        // Top-right corner
        context.move(to: CGPoint(x: cardRect.maxX - cornerLength, y: cardRect.minY))
        context.addLine(to: CGPoint(x: cardRect.maxX, y: cardRect.minY))
        context.move(to: CGPoint(x: cardRect.maxX, y: cardRect.minY))
        context.addLine(to: CGPoint(x: cardRect.maxX, y: cardRect.minY + cornerLength))
        
        // Bottom-left corner
        context.move(to: CGPoint(x: cardRect.minX, y: cardRect.maxY - cornerLength))
        context.addLine(to: CGPoint(x: cardRect.minX, y: cardRect.maxY))
        context.move(to: CGPoint(x: cardRect.minX, y: cardRect.maxY))
        context.addLine(to: CGPoint(x: cardRect.minX + cornerLength, y: cardRect.maxY))
        
        // Bottom-right corner
        context.move(to: CGPoint(x: cardRect.maxX - cornerLength, y: cardRect.maxY))
        context.addLine(to: CGPoint(x: cardRect.maxX, y: cardRect.maxY))
        context.move(to: CGPoint(x: cardRect.maxX, y: cardRect.maxY - cornerLength))
        context.addLine(to: CGPoint(x: cardRect.maxX, y: cardRect.maxY))
        
        context.strokePath()
        
        // Draw center alignment guides
        context.setStrokeColor(UIColor.white.withAlphaComponent(0.3).cgColor) // Semi-transparent white
        context.setLineWidth(1.0)
        
        // Set dashed line pattern
        let dashPattern: [CGFloat] = [10.0, 10.0]
        context.setLineDash(phase: 0, lengths: dashPattern)
        
        // Horizontal center line
        context.move(to: CGPoint(x: cardRect.minX + 20, y: centerY))
        context.addLine(to: CGPoint(x: cardRect.maxX - 20, y: centerY))
        
        // Vertical center line
        context.move(to: CGPoint(x: centerX, y: cardRect.minY + 20))
        context.addLine(to: CGPoint(x: centerX, y: cardRect.maxY - 20))
        
        context.strokePath()
        
        // Reset line dash
        context.setLineDash(phase: 0, lengths: [])
    }
}
