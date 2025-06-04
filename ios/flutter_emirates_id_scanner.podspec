#
# To learn more about a Podspec see http://guides.cocoapods.org/syntax/podspec.html.
# Run `pod lib lint flutter_emirates_id_scanner.podspec` to validate before publishing.
#
Pod::Spec.new do |s|
  s.name             = 'flutter_emirates_id_scanner'
  s.version          = '0.0.1'
  s.summary          = 'A Flutter plugin for scanning Emirates ID cards with native camera and OCR capabilities.'
  s.description      = <<-DESC
A Flutter plugin for scanning Emirates ID cards with native camera and OCR capabilities on Android and iOS.
                       DESC
  s.homepage         = 'http://example.com'
  s.license          = { :file => '../LICENSE' }
  s.author           = { 'Your Company' => 'email@example.com' }
  s.source           = { :path => '.' }
  s.source_files = 'Classes/**/*'
  s.dependency 'Flutter'
  s.platform = :ios, '12.0'

  # Flutter.framework does not contain a i386 slice.
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES', 'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386' }
  s.swift_version = '5.0'
  
  # Add framework dependencies for camera and vision
  s.frameworks = 'AVFoundation', 'Vision'

  # Privacy manifest for camera usage
  s.resource_bundles = {'flutter_emirates_id_scanner_privacy' => ['Resources/PrivacyInfo.xcprivacy']}
end
