import Foundation

enum RelayConnectionStatus:Equatable {
 case bluetooth,server,connecting,disconnected
 static func resolve(bluetoothConnected:Bool,serverConnected:Bool,attempting:Bool)->Self{
  if bluetoothConnected{return .bluetooth}
  if serverConnected{return .server}
  return attempting ? .connecting:.disconnected
 }
 var title:String{switch self{case .bluetooth:"블루투스로 연결됨";case .server:"서버를 통해 연결됨";case .connecting:"연결 시도 중…";case .disconnected:"연결이 끊어졌습니다"}}
 var symbol:String{switch self{case .bluetooth:"checkmark.circle.fill";case .server:"network";case .connecting:"arrow.triangle.2.circlepath";case .disconnected:"xmark.circle.fill"}}
}

/// A successful server request alone is not proof that Galaxy is reachable.
/// Confirm only after an authenticated Galaxy packet has been decrypted and acknowledged.
struct ServerConnectionState {
 static let maximumAge:TimeInterval=120
 private(set) var verifiedAt:Date?
 private(set) var attempting=false
 mutating func begin(){attempting=true}
 mutating func confirm(at date:Date){verifiedAt=date;attempting=false}
 mutating func fail(){verifiedAt=nil;attempting=false}
 mutating func finish(){attempting=false}
 func isVerified(at date:Date)->Bool{guard let verifiedAt else{return false};let age=date.timeIntervalSince(verifiedAt);return age>=0 && age<Self.maximumAge}
}
