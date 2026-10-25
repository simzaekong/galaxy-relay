import Foundation
/// A single, live BLE alert. Never a transport for call audio or telephony actions.
enum CallAlertEnd:String,Equatable {case ended,answeredElsewhere,acknowledged,connectionLost,disabled,timeout,failed,reset}
enum CallAlertEffect:Equatable {case none,start(String),update(String),connected(String),end(String,CallAlertEnd)}
struct CallAlertPolicy {
 private(set) var active:String?;private var started:Int64=0,latest:Int64=0
 private var closed:[String]=[];private(set) var connected=false
 static let maximumAge:Int64=15000,maximumRing:Int64=45000
 private mutating func remember(_ id:String){if !closed.contains(id){closed.append(id)};if closed.count>256{closed.removeFirst(closed.count-256)}}
 static func valid(_ id:String)->Bool{id.hasPrefix("evt_")&&UUID(uuidString:String(id.dropFirst(4))) != nil}
 mutating func receive(id:String,state:String,timestamp:Int64,now:Int64,live:Bool,enabled:Bool)->CallAlertEffect {
  guard Self.valid(id),timestamp<=now+5000,["ringing","offhook","ended"].contains(state) else{return .none}
  if state == "ended"{remember(id);guard active==id else{return .none};active=nil;connected=false;return .end(id,.ended)}
  if state == "offhook"{guard active==id else{remember(id);return .none};guard live,enabled,timestamp>=now-Self.maximumAge,timestamp>=latest,!connected else{return .none};connected=true;latest=timestamp;return .connected(id)}
  guard enabled,live,!closed.contains(id),timestamp>=now-Self.maximumAge else{return .none}
  if let active{guard active==id else{remember(id);return .none};guard !connected,timestamp>latest,now<started+Self.maximumRing else{return .none};latest=timestamp;return .update(id)}
  active=id;started=min(timestamp,now);latest=timestamp;return .start(id)
 }
 mutating func stop(_ reason:CallAlertEnd)->CallAlertEffect{guard let id=active else{return .none};active=nil;connected=false;remember(id);return .end(id,reason)}
 mutating func expire(now:Int64)->CallAlertEffect{guard !connected,active != nil,now>=started+Self.maximumRing else{return .none};return stop(.timeout)}
 var expiresAt:Int64?{active==nil||connected ? nil:started+Self.maximumRing}
}
