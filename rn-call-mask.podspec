require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "rn-call-mask"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = "https://github.com/nguyentienminh07102004/rn-call-mask"
  s.license      = package["license"]
  s.authors      = { "rn-call-mask" => "maintainers" }
  s.platforms    = { :ios => "15.0" }
  s.source       = { :git => "https://github.com/nguyentienminh07102004/rn-call-mask.git", :tag => "#{s.version}" }
  s.source_files = "ios/*.{swift,h,m,mm}"
  s.exclude_files = "ios/Tests/**/*"
  s.swift_version = "5.9"
  s.module_name = "RNCallMask"
  s.frameworks = "CallKit", "PushKit", "AVFAudio"
  s.dependency "React-Core"
end
