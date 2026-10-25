import Foundation
enum PhoneNetwork:String,Codable {
 case online,offline,unknown
 static func classify(available:Bool,unavailable:Bool)->PhoneNetwork{available ? .online:unavailable ? .offline:.unknown}
 var label:String{switch self{case .online:"온라인";case .offline:"오프라인";case .unknown:"확인 중"}}
}
