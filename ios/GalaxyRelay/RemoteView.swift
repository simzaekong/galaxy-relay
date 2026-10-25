import SwiftUI

@MainActor struct RemoteView:View {
 @ObservedObject var ble:BLELink
 @ObservedObject var remote:RemoteModel
 @Environment(\.dynamicTypeSize) private var typeSize
 let openCamera:()->Void
 init(ble:BLELink,openCamera:@escaping()->Void){self.ble=ble;remote=ble.remote;self.openCamera=openCamera}
 private var controlsDisabled:Bool{!ble.ready || !ble.remoteSupported || !remote.loaded || remote.busy}
 var body:some View {
  ScrollView {
   VStack(alignment:.leading,spacing:28){
    if !ble.remoteSupported{Text("Galaxy Relay를 업데이트하면 리모트를 사용할 수 있습니다.").font(.footnote).foregroundStyle(.secondary)}
    if typeSize.isAccessibilitySize{
     musicCard
     RemoteVolumeSlider(title:"미디어 음량",symbol:"speaker.wave.2.fill",value:remote.state.mediaVolume,maximum:remote.state.mediaVolumeMax,vertical:false,disabled:controlsDisabled){remote.perform("mediaVolume",value:$0,ble:ble)}
    }else{
     HStack(spacing:12){
      musicCard.frame(maxWidth:.infinity)
      RemoteVolumeSlider(title:"미디어 음량",symbol:"speaker.wave.2.fill",value:remote.state.mediaVolume,maximum:remote.state.mediaVolumeMax,vertical:true,disabled:controlsDisabled){remote.perform("mediaVolume",value:$0,ble:ble)}.frame(width:70)
     }.frame(height:190)
    }
    VStack(alignment:.leading,spacing:24){
     HStack(alignment:.top,spacing:16){ForEach(0..<3){mode in modeButton(mode)}}
     RemoteVolumeSlider(title:"벨소리 음량",symbol:"bell.fill",value:remote.state.ringVolume,maximum:remote.state.ringVolumeMax,vertical:false,disabled:controlsDisabled){remote.perform("ringVolume",value:$0,ble:ble)}
    }
    Button(action:openCamera){Label("Galaxy 카메라 실행",systemImage:"video.fill").frame(maxWidth:.infinity).padding(.vertical,10)}.buttonStyle(.bordered)
    HStack(alignment:.top,spacing:10){
     ProgressView().opacity(remote.busy ? 1:0).accessibilityHidden(!remote.busy)
     Text(!remote.notice.isEmpty ? remote.notice:remote.busy ? "요청한 조작을 확인하고 있어요…":remote.loaded ? "Galaxy와 연결됨":"Galaxy 상태 확인 중…")
      .font(.footnote).foregroundStyle(.secondary).frame(maxWidth:.infinity,alignment:.leading)
    }.frame(minHeight:30)
    Text("Galaxy의 방해금지 정책에 따라 벨소리 모드 변경이 제한될 수 있습니다.").font(.caption).foregroundStyle(.secondary)
   }.padding(20)
  }.background(Color(uiColor:.systemBackground)).navigationTitle("리모트")
   .toolbar{ToolbarItem(placement:.navigationBarTrailing){Button{remote.perform("state",ble:ble)}label:{Image(systemName:"arrow.clockwise").font(.title3).frame(width:40,height:40)}.disabled(remote.busy || !ble.remoteSupported).accessibilityLabel("Galaxy 상태 새로 고침")}}
   .modifier(RemoteMonitoring(ble:ble,includeSignal:false))
 }
 private var musicCard:some View{
  VStack(alignment:.leading,spacing:20){
   HStack(spacing:12){
    ZStack{
     RoundedRectangle(cornerRadius:18).fill(LinearGradient(colors:[Color.cyan.opacity(0.8),Color.blue.opacity(0.7)],startPoint:.topLeading,endPoint:.bottomTrailing))
     Image(systemName:"music.note").font(.system(size:32,weight:.medium)).foregroundStyle(.white)
    }.frame(width:64,height:72).accessibilityHidden(true)
    VStack(alignment:.leading,spacing:5){
     Text(remote.state.title.isEmpty ? "재생 중인 곡 없음":remote.state.title).font(.title3.bold()).lineLimit(2)
     Text(remote.state.artist.isEmpty ? (remote.state.mediaApp.isEmpty ? "Galaxy에서 음악을 재생하세요":remote.state.mediaApp):remote.state.artist).font(.subheadline).foregroundStyle(.secondary).lineLimit(2)
    }.frame(maxWidth:.infinity,alignment:.leading)
   }
   HStack(spacing:12){
    Spacer(minLength:0)
    mediaButton("previous",symbol:"backward.end.fill",enabled:remote.state.canPrevious)
    mediaButton(remote.state.playing ? "pause":"play",symbol:remote.state.playing ? "pause.fill":"play.fill",enabled:remote.state.playing ? remote.state.canPause:remote.state.canPlay)
    mediaButton("next",symbol:"forward.end.fill",enabled:remote.state.canNext)
    Spacer(minLength:0)
   }
  }.padding(18).frame(maxWidth:.infinity,maxHeight:.infinity,alignment:.center).remoteSurface(radius:32)
 }
 private func mediaButton(_ action:String,symbol:String,enabled:Bool)->some View{
  Button{remote.perform(action,ble:ble)}label:{Image(systemName:symbol).font(.system(size:action=="play" || action=="pause" ? 30:22,weight:.semibold)).frame(minWidth:44,minHeight:44)}
   .buttonStyle(.plain).foregroundStyle(Color.primary).opacity(enabled ? 1:0.35).disabled(controlsDisabled || !enabled)
   .accessibilityLabel(["play":"재생","pause":"일시 정지","next":"다음 곡","previous":"이전 곡"][action] ?? action)
 }
 private func modeButton(_ mode:Int)->some View{
  let selected=remote.loaded && remote.state.ringerMode==mode
  return Button{remote.perform("ringerMode",value:mode,ble:ble)}label:{
   VStack(spacing:10){
    Image(systemName:["bell.slash.fill","iphone.radiowaves.left.and.right","bell.fill"][mode])
     .font(.system(size:27,weight:.medium)).foregroundStyle(selected && mode==0 ? Color.red:selected ? Color(uiColor:.label):Color.primary)
     .frame(width:76,height:76).background{if selected{Circle().fill(LinearGradient(colors:[Color(uiColor:.systemGray3),Color(uiColor:.systemGray4)],startPoint:.top,endPoint:.bottom))}else{Circle().fill(Color(uiColor:.secondarySystemBackground))}}
     .overlay(Circle().strokeBorder(LinearGradient(colors:[Color.primary.opacity(0.3),Color.primary.opacity(0.03)],startPoint:.topLeading,endPoint:.bottomTrailing),lineWidth:1))
    Text(["무음","진동","소리"][mode]).font(.caption).foregroundStyle(.secondary)
   }
  }.buttonStyle(.plain).disabled(controlsDisabled).accessibilityLabel(["무음","진동","소리"][mode]).accessibilityAddTraits(selected ? .isSelected:[])
 }
}

/// A drag stays local until release. Polling cannot move the thumb under the finger.
private struct RemoteVolumeSlider:View {
 let title:String,symbol:String
 let value:Int,maximum:Int
 let vertical:Bool,disabled:Bool
 let commit:(Int)->Void
 @State private var draft:Double?
 private var displayed:Double{draft ?? Double(value)}
 var body:some View{
  Group{
   if vertical{
    GeometryReader{geometry in
     ZStack(alignment:.bottom){
      Color.clear
      Rectangle().fill(LinearGradient(colors:[Color(uiColor:.systemGray3),Color(uiColor:.systemGray4)],startPoint:.top,endPoint:.bottom)).frame(height:geometry.size.height*displayed/100)
      VStack(spacing:8){Text("\(Int(displayed.rounded()))%").font(.caption2.weight(.semibold)).monospacedDigit();Image(systemName:symbol).font(.system(size:25,weight:.medium)).foregroundStyle(.cyan)}.padding(.bottom,20)
     }.frame(width:geometry.size.width,height:geometry.size.height).remoteSurface(radius:35).contentShape(Capsule())
      .gesture(DragGesture(minimumDistance:0).onChanged{gesture in guard !disabled else{return};draft=Double(min(1,max(0,1-gesture.location.y/geometry.size.height)))*100}.onEnded{_ in finish()})
    }.accessibilityElement(children:.ignore).accessibilityLabel(title).accessibilityValue("\(Int(displayed.rounded()))퍼센트").accessibilityAdjustableAction{direction in adjust(direction)}
   }else{
    VStack(spacing:10){
     HStack{Label(title,systemImage:symbol);Spacer();Text("\(Int(displayed.rounded()))%").monospacedDigit()}.font(.caption).foregroundStyle(.secondary)
     Slider(value:Binding(get:{displayed},set:{draft=$0}),in:0...100,onEditingChanged:{editing in if editing{if draft==nil{draft=Double(value)}}else{finish()}})
      .tint(.blue).accessibilityLabel(title).accessibilityValue("\(Int(displayed.rounded()))퍼센트")
    }
   }
  }.disabled(disabled).onChange(of:disabled){if $0{draft=nil}}.onDisappear{draft=nil}
 }
 private func finish(){guard let draft else{return};self.draft=nil;guard !disabled else{return};let percent=Int(draft.rounded());if percent != value{commit(percent)}}
 private func adjust(_ direction:AccessibilityAdjustmentDirection){
  guard !disabled else{return};let delta:Int
  switch direction{case .increment:delta=1;case .decrement:delta = -1;@unknown default:return}
  let step=Int((Double(value)*Double(maximum)/100).rounded())+delta
  commit(Int((Double(min(maximum,max(0,step)))*100/Double(maximum)).rounded()))
 }
}

private extension View {
 func remoteSurface(radius:CGFloat)->some View{
  background(LinearGradient(colors:[Color(uiColor:.secondarySystemBackground),Color(uiColor:.tertiarySystemBackground)],startPoint:.topLeading,endPoint:.bottomTrailing))
   .clipShape(RoundedRectangle(cornerRadius:radius,style:.continuous))
   .overlay(RoundedRectangle(cornerRadius:radius,style:.continuous).strokeBorder(LinearGradient(colors:[Color.primary.opacity(0.28),Color.primary.opacity(0.02),Color.primary.opacity(0.12)],startPoint:.topLeading,endPoint:.bottomTrailing),lineWidth:1))
 }
}

/// Active only on the visible home/finder or remote screen; no background polling.
@MainActor private struct RemoteMonitoring:ViewModifier {
 @ObservedObject var ble:BLELink
 let includeSignal:Bool
 @Environment(\.scenePhase) private var scenePhase
 func body(content:Content)->some View{
  content.task(id:scenePhase){
   #if targetEnvironment(simulator)
   if ProcessInfo.processInfo.arguments.contains("--design-preview"){return}
   #endif
   var tick=0
   while !Task.isCancelled{
    if scenePhase == .active,ble.ready{
     if includeSignal{ble.readSignal()}
     if tick%2==0{ble.remote.perform("state",ble:ble)}
    }
    tick+=1;do{try await Task.sleep(nanoseconds:2_000_000_000)}catch{return}
   }
  }
 }
}

@MainActor struct DeviceFinderCard:View {
 @ObservedObject var finder:DeviceFinder
 @ObservedObject var ble:BLELink
 @ObservedObject var remote:RemoteModel
 let settings:PairSettings?
 init(finder:DeviceFinder,ble:BLELink,settings:PairSettings?){self.finder=finder;self.ble=ble;remote=ble.remote;self.settings=settings}
 var body:some View{
  VStack(alignment:.leading,spacing:20){
   Label("기기 찾기",systemImage:"location.magnifyingglass").font(.headline)
   if ble.ready{nearby;Divider()}
   VStack(alignment:.leading,spacing:12){
    Text("현재 위치 찾기").font(.subheadline.weight(.semibold))
    Text(finder.status).font(.footnote).foregroundStyle(.secondary)
    if let p=finder.found,let accuracy=p.accuracy,let measuredAt=p.measuredAt{
     Text("측정 시각 \(Date(timeIntervalSince1970:Double(measuredAt)/1000).formatted(date:.abbreviated,time:.shortened)) · 정확도 약 \(Int(accuracy.rounded()))m").font(.caption).foregroundStyle(.secondary)
     Button("지도에서 보기"){finder.openMap()}.buttonStyle(.bordered)
    }
    Button{if let settings{Task{await finder.locate(settings)}}}label:{HStack{if finder.busy{ProgressView()};Label(finder.busy ? "위치 요청 중…":"서버로 현재 위치 요청",systemImage:"location.fill")}}.buttonStyle(.borderedProminent).disabled(settings==nil || finder.busy)
    Text("Galaxy의 인터넷·위치 서비스와 기기 찾기 허용이 필요합니다. 전원이 꺼지거나 앱이 강제 중지되면 응답할 수 없습니다.").font(.caption).foregroundStyle(.secondary)
   }
   if !ble.ready{Divider();DisconnectLocationContent(recorder:ble.disconnectLocation)}
  }.frame(maxWidth:.infinity,alignment:.leading).padding(22).background(Color(uiColor:.secondarySystemGroupedBackground),in:RoundedRectangle(cornerRadius:28))
   .modifier(RemoteMonitoring(ble:ble,includeSignal:true))
 }
 private var nearby:some View{
  VStack(alignment:.leading,spacing:14){
   Text("가까운 곳에서 찾기").font(.subheadline.weight(.semibold))
   Button{remote.perform(remote.state.finding ? "findStop":"findStart",ble:ble)}label:{Label(remote.state.finding ? "소리 재생 중지":"Galaxy 소리 울리기",systemImage:remote.state.finding ? "stop.circle.fill":"speaker.wave.3.fill")}.buttonStyle(.borderedProminent).tint(remote.state.finding ? .red:.blue).disabled(!ble.remoteSupported || !remote.loaded || remote.busy)
   TimelineView(.periodic(from:.now,by:2)){context in
    let fresh=ble.signalAt.map{context.date.timeIntervalSince($0)<8} ?? false
    HStack(spacing:12){
     Image(systemName:"antenna.radiowaves.left.and.right").font(.title2).foregroundStyle(.blue)
     VStack(alignment:.leading,spacing:4){Text(fresh ? ble.signal.text:"신호 측정 대기 중").font(.subheadline);if fresh,let rssi=ble.signal.rssi{Text("\(rssi) dBm").font(.caption).monospacedDigit().foregroundStyle(.secondary)}}
    }
   }
   if !remote.notice.isEmpty{Text(remote.notice).font(.footnote).foregroundStyle(.secondary)}
   Text("소리는 최대 15초 재생합니다. 신호는 벽·몸·기기 방향에 영향을 받으며 정확한 거리를 나타내지 않습니다.").font(.caption).foregroundStyle(.secondary)
  }
 }
}
