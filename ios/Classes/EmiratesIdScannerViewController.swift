import UIKit
import AVFoundation
import Vision

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
    private var extractedData: [String: String?] = [:]
    
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
        if captureSession?.isRunning == false {
            DispatchQueue.global(qos: .userInitiated).async {
                self.captureSession.startRunning()
            }
        }
    }
    
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        if captureSession?.isRunning == true {
            captureSession.stopRunning()
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
                
                setupLivePreview()
                setupVideoOutput()
            }
        } catch {
            handleError("Error setting up camera: \(error.localizedDescription)")
        }
    }
    
    private func setupLivePreview() {
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
                    "backImagePath": backImagePath
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
        return cleanText.contains("EMIRATES") ||
               cleanText.contains("الإمارات") ||
               cleanText.contains("IDENTITY") ||
               cleanText.contains("CARD") ||
               text.range(of: "\\d{3}-\\d{4}-\\d{7}-\\d{1}", options: .regularExpression) != nil
    }
    
    private func isValidBackSide(_ text: String) -> Bool {
        let cleanText = text.replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression).uppercased()
        return cleanText.contains("MINISTRY") ||
               cleanText.contains("INTERIOR") ||
               cleanText.contains("وزارة") ||
               cleanText.contains("الداخلية") ||
               text.range(of: "\\d{2}/\\d{2}/\\d{4}", options: .regularExpression) != nil
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
        let fileName: String
        
        switch scanningStep {
        case .front:
            fileName = "emirates_id_front_\(timestamp).jpg"
        case .back:
            fileName = "emirates_id_back_\(timestamp).jpg"
        case .completed:
            return
        }
        
        let documentsPath = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        let imagePath = documentsPath.appendingPathComponent(fileName)
        
        do {
            try imageData.write(to: imagePath)
            
            switch scanningStep {
            case .front:
                frontImagePath = imagePath.path
                scanningStep = .back
                updateInstruction()
            case .back:
                backImagePath = imagePath.path
                scanningStep = .completed
                processCompleted()
            case .completed:
                break
            }
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
    
    override func draw(_ rect: CGRect) {
        super.draw(rect)
        
        guard let context = UIGraphicsGetCurrentContext() else { return }
        
        context.setStrokeColor(UIColor.white.cgColor)
        context.setLineWidth(3.0)
        
        let centerX = bounds.width / 2
        let centerY = bounds.height / 2
        let cardWidth = bounds.width * 0.8
        let cardHeight = cardWidth * 0.63 // Emirates ID aspect ratio
        
        let cardRect = CGRect(
            x: centerX - cardWidth / 2,
            y: centerY - cardHeight / 2,
            width: cardWidth,
            height: cardHeight
        )
        
        context.stroke(cardRect)
        
        // Draw corner guides
        let cornerLength: CGFloat = 30
        context.setLineWidth(6.0)
        
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
    }
}
