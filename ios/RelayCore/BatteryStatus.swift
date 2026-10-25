import Foundation
struct BatteryReading:Codable,Equatable {
 let level:Int?;let state:String
 var valid:Bool{(level==nil || (0...100).contains(level!)) && ["charging","full","plugged","unplugged","unknown"].contains(state)}
 var label:String{let percent=level.map{"\($0)%"} ?? "—";return percent+(state=="charging" ? " · 충전 중":state=="full" ? " · 충전 완료":state=="plugged" ? " · 전원 연결":"")}
}
struct PeerBattery:Codable {let pairId:String;let battery:BatteryReading;let reportedAt:Int64}
struct BatterySyncPolicy {
 private var previous:BatteryReading?;private var sentAt:TimeInterval=0
 func delay(_ next:BatteryReading,now:TimeInterval)->TimeInterval?{
  guard let previous else{return 0};guard previous != next else{return nil}
  let interval:TimeInterval=previous.state==next.state ? 300:60
  return max(0,interval-max(0,now-sentAt))
 }
 mutating func sent(_ next:BatteryReading,now:TimeInterval){previous=next;sentAt=now}
}
