import Foundation
struct LinkHealth {
 static let interval:TimeInterval=300,timeout:TimeInterval=30
 private(set) var pending:String?
 private var next:TimeInterval=0,deadline:TimeInterval=0
 mutating func reset(now:TimeInterval){pending=nil;next=now+Self.interval;deadline=0}
 func due(now:TimeInterval)->Bool{pending==nil && now>=next}
 mutating func begin(_ id:String,now:TimeInterval)->Bool{guard pending==nil else{return false};pending=id;deadline=now+Self.timeout;return true}
 mutating func acknowledge(_ id:String,now:TimeInterval)->Bool{guard pending==id else{return false};reset(now:now);return true}
 func expired(now:TimeInterval)->Bool{pending != nil && now>=deadline}
}
