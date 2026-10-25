import Foundation
/// Only fixed error categories and a timestamp; no caller, call ID or raw NSError text.
enum CallAlertFailure:String,Error,Codable {
 case unentitled="CK01",focus="CK02",blocked="CK03",duplicate="CK04",busy="CK05",execution="CK06",unknown="CK99"
 var message:String{switch self{
 case .unentitled:return "iOS가 CallKit 앱 설정 또는 서명 권한을 거부했습니다. 최신 IPA로 업데이트해주세요."
 case .focus:return "집중 모드 때문에 통화형 알림이 차단되었습니다."
 case .blocked:return "iOS 차단 설정 때문에 통화형 알림이 차단되었습니다."
 case .duplicate:return "iOS에 같은 수신 화면이 이미 등록되어 있습니다."
 case .busy:return "iPhone에 다른 통화가 있어 일반 알림으로 표시했습니다."
 case .execution:return "iOS가 수신 화면의 백그라운드 실행 시간을 허용하지 않았습니다."
 case .unknown:return "iOS에서 통화형 알림을 표시하지 못했습니다."
 }}
 static func classify(domain:String,code:Int)->Self{guard domain=="com.apple.CallKit.error.incomingcall"||domain=="com.apple.CallKit.error.requesttransaction" else{return .unknown};if code==1{return .unentitled};guard domain=="com.apple.CallKit.error.incomingcall" else{return .unknown};switch code{case 2:return .duplicate;case 3:return .focus;case 4:return .blocked;default:return .unknown}}
}
struct CallAlertDiagnostic:Codable,Equatable {
 let code:String;let timestamp:Int64
 var valid:Bool{(code=="CK00"||CallAlertFailure(rawValue:code) != nil)&&timestamp>=0}
 var message:String{code=="CK00" ? "iOS가 통화형 수신 화면 요청을 수락했습니다.":CallAlertFailure(rawValue:code)?.message ?? "진단 기록을 확인할 수 없습니다."}
}
