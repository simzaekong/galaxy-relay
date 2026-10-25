import Foundation
struct DisconnectLocationPolicy {
 private var armed=false;private var lastAttempt:TimeInterval?
 mutating func connected(){armed=true}
 mutating func stopped(){armed=false}
 mutating func lost(now:TimeInterval,allowed:Bool)->Bool{let was=armed;armed=false;guard was,allowed else{return false};if let lastAttempt,now-lastAttempt<300{return false};lastAttempt=now;return true}
}
struct DisconnectFix:Codable {
 let pairId:String;let latitude:Double;let longitude:Double;let accuracy:Double;let triggeredAt:Int64;let measuredAt:Int64
 var valid:Bool{latitude.isFinite&&longitude.isFinite&&accuracy.isFinite&&(-90...90).contains(latitude)&&(-180...180).contains(longitude)&&(0...10000).contains(accuracy)&&measuredAt>=triggeredAt-30000&&measuredAt<=triggeredAt+20000}
 func acceptable(now:Int64)->Bool{valid&&measuredAt<=now+5000&&now>=triggeredAt&&now-triggeredAt<=20000}
}
