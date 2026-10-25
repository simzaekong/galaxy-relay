import Foundation

struct RemoteState:Codable,Equatable {
 var mediaAvailable=false,mediaId="",mediaApp="",title="",artist="",playing=false
 var canPlay=false,canPause=false,canNext=false,canPrevious=false
 var mediaVolume=0,ringVolume=0,ringerMode=2,finding=false
 var mediaVolumeMax=15,ringVolumeMax=15
 var valid:Bool{(0...100).contains(mediaVolume)&&(0...100).contains(ringVolume)&&(0...2).contains(ringerMode)&&(1...1000).contains(mediaVolumeMax)&&(1...1000).contains(ringVolumeMax)&&title.utf8.count<=1024&&artist.utf8.count<=1024&&mediaId.utf8.count<=256}
 func optimistic(_ action:String,value:Int?)->RemoteState {
  var next=self
  switch action{case "play":next.playing=true;case "pause":next.playing=false;case "mediaVolume":next.mediaVolume=value ?? mediaVolume;case "ringVolume":next.ringVolume=value ?? ringVolume;case "ringerMode":next.ringerMode=value ?? ringerMode;case "findStart":next.finding=true;case "findStop":next.finding=false;default:break};return next
 }
 func confirms(_ action:String,value:Int?,previous:RemoteState)->Bool {
  switch action {
  case "play":return mediaId==previous.mediaId&&playing
  case "pause":return mediaId==previous.mediaId && !playing
  case "findStart":return finding
  case "findStop":return !finding
  case "ringerMode":return ringerMode==value
  case "mediaVolume","ringVolume":
   guard let value else{return false};let max=action=="mediaVolume" ? mediaVolumeMax:ringVolumeMax
   let expected=Int((Double(Int((Double(max)*Double(value)/100).rounded()))*100/Double(max)).rounded())
   return (action=="mediaVolume" ? mediaVolume:ringVolume)==expected
  default:return true
  }
 }
}

/// One in-flight command prevents a late receipt from undoing a newer tap.
struct RemoteTransaction {
 struct Pending {let id:String,action:String,value:Int?,previous:RemoteState}
 private(set) var confirmed=RemoteState(),displayed=RemoteState(),pending:Pending?
 mutating func refresh(_ state:RemoteState)->Bool{guard pending==nil,state.valid else{return false};guard state != confirmed || state != displayed else{return false};confirmed=state;displayed=state;return true}
 mutating func begin(id:String,action:String,value:Int?=nil)->Bool{guard pending==nil else{return false};pending=Pending(id:id,action:action,value:value,previous:confirmed);displayed=confirmed.optimistic(action,value:value);return true}
 mutating func resolve(id:String,state:RemoteState,result:String)->Bool?{
  guard let p=pending,p.id==id,state.valid else{return nil};pending=nil
  let success=result=="OK"&&state.confirms(p.action,value:p.value,previous:p.previous)
  confirmed=state;displayed=success ? state:p.previous;return success
 }
 mutating func cancel(){displayed=confirmed;pending=nil}
 mutating func reset(){self=RemoteTransaction()}
}

struct SignalTrend {
 private var samples:[Double]=[]
 private(set) var rssi:Int?
 private(set) var text="신호 측정 중"
 mutating func record(_ value:Int){
  guard (-127 ... -1).contains(value) else{return};samples.append(Double(value));if samples.count>8{samples.removeFirst()}
  let recent=Array(samples.suffix(4));rssi=Int((recent.reduce(0,+)/Double(recent.count)).rounded())
  guard samples.count>=8 else{text="신호 비교 중";return}
  let old=samples.prefix(4).reduce(0,+)/4,new=samples.suffix(4).reduce(0,+)/4
  text=new-old>=4 ? "신호가 강해지고 있어요":old-new>=4 ? "신호가 약해지고 있어요":"신호 세기가 비슷해요"
 }
}
