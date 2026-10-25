import Foundation
struct MessageReplayWindow {
 enum Decision {case accepted,replay,capacity}
 private var ids=[String:Int64]()
 mutating func record(_ id:String,timestamp:Int64,now:Int64)->Decision{
  ids=ids.filter{$0.value>=now}
  guard timestamp>=now-120000,timestamp<=now+120000,ids[id]==nil else{return .replay}
  guard ids.count<4096 else{return .capacity}
  ids[id]=timestamp+120000;return .accepted
 }
}
