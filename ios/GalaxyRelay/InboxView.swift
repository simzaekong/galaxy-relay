import SwiftUI

struct NotificationGroup:Identifiable {let id:String;let events:[RelayEvent]}
enum RelayTab:Hashable {case home,inbox,remote,settings}

@MainActor struct InboxView:View {
 @ObservedObject var model:RelayModel
 @ObservedObject var history:History
 @ObservedObject var ble:BLELink
 @ObservedObject var calls:CallAlerts
 @State private var tab:RelayTab = .home
 @State private var selected:RelayEvent?
 @State private var scanning=false
 @State private var cameraOpen=false
 init(model:RelayModel){self.model=model;history=model.history;ble=model.ble;calls=model.callAlerts
  #if targetEnvironment(simulator)
  if ProcessInfo.processInfo.arguments.contains("--design-inbox"){_tab=State(initialValue:.inbox)}
  if ProcessInfo.processInfo.arguments.contains("--design-settings"){_tab=State(initialValue:.settings)}
  #endif
 }
 var body:some View {
  TabView(selection:$tab){
   NavigationStack{HomeView(model:model,ble:ble,scan:{scanning=true},openCamera:{cameraOpen=true})}
    .tabItem{Label("홈",systemImage:"house.fill")}.tag(RelayTab.home)
   NavigationStack{NotificationInbox(model:model,history:history,show:{selected=$0})}
    .tabItem{Label("알림",systemImage:"bell.badge.fill")}.tag(RelayTab.inbox)
   if ble.ready{
    NavigationStack{RemoteView(ble:ble,openCamera:{cameraOpen=true})}.tabItem{Label("리모트",systemImage:"slider.horizontal.3")}.tag(RelayTab.remote)
   }
   SettingsView(model:model,ble:ble,scan:{scanning=true})
    .tabItem{Label("설정",systemImage:"gearshape.fill")}.tag(RelayTab.settings)
  }
  .onChange(of:ble.ready){ready in
   if !ready,tab == .remote{tab = .home}
   #if targetEnvironment(simulator)
   if ready,ProcessInfo.processInfo.arguments.contains("--design-remote"){tab = .remote}
   #endif
  }
  .tint(.blue)
  .fullScreenCover(isPresented:Binding(get:{calls.connectedAt != nil},set:{_ in})){GalaxyCallView(calls:calls)}
  .onAppear{showNotification()}.onChange(of:model.openEventId){_ in showNotification()}
  .sheet(item:$selected){event in DetailView(event:event,model:model,history:history).presentationDetents([.large]).presentationDragIndicator(.visible)}
  .fullScreenCover(isPresented:$cameraOpen){if let settings=model.settings{CameraView(settings:settings)}else{Text("Galaxy 연결이 필요합니다.")}}
  .onChange(of:model.settings?.deviceId){_ in cameraOpen=false}
  .onChange(of:ble.phoneNetwork){model.networkChanged($0)}
  .sheet(isPresented:$scanning){
   QRScanner{value in
    do{try model.pair(PairQR.parse(value));scanning=false;tab = .home}
    catch{model.notice="Galaxy Relay의 새 연결 QR인지 확인해주세요.";scanning=false}
   }.overlay(alignment:.topTrailing){Button("닫기"){scanning=false}.buttonStyle(.borderedProminent).padding()}.ignoresSafeArea()
  }
 }
 private func showNotification(){guard let id=model.openEventId,let event=history.state.events.first(where:{$0.id==id}) else{return};tab = .inbox;selected=event;model.openEventId=nil}
}

@MainActor private struct HomeView:View {
 @ObservedObject var model:RelayModel
 @ObservedObject var ble:BLELink
 let scan:()->Void;let openCamera:()->Void
 var body:some View {
  ScrollView{
   VStack(alignment:.leading,spacing:24){
    connectionCard
    if model.settings != nil{
     Button(action:openCamera){Label("Galaxy 카메라 실행",systemImage:"video.fill").font(.headline).frame(maxWidth:.infinity).padding(22).relayCard()}.buttonStyle(.plain).foregroundStyle(.blue)
    }
    if model.settings != nil{DeviceFinderCard(finder:model.finder,ble:ble,settings:model.settings)}
    if !model.notice.isEmpty{Label(model.notice,systemImage:"info.circle").font(.footnote).foregroundStyle(.secondary).fixedSize(horizontal:false,vertical:true)}
   }.padding(20)
  }.background(Color(uiColor:.systemGroupedBackground)).navigationTitle("홈")
   .toolbar{ToolbarItem(placement:.navigationBarTrailing){Button{Task{await model.sync()}}label:{Image(systemName:"arrow.clockwise")}.accessibilityLabel("서버 동기화").disabled(model.busy)}}
 }
 private var connectionCard:some View {
  VStack(alignment:.leading,spacing:22){
   HStack(alignment:.top){
    VStack(alignment:.leading,spacing:7){
     ConnectionStatusLabel(model:model,ble:ble).font(.subheadline.weight(.semibold))
     Text(model.settings?.galaxyName ?? "내 Galaxy와 연결하세요").font(.title2.bold()).fixedSize(horizontal:false,vertical:true)
    };Spacer()
    if model.connectionStatus() == .connecting{ProgressView().padding(.top,4)}
   }
   HStack(spacing:18){
    device("Galaxy",symbol:"smartphone",battery:ble.remoteBattery?.battery,updatedAt:ble.remoteBattery.map{Date(timeIntervalSince1970:Double($0.reportedAt)/1000)})
    VStack(spacing:8){Image(systemName:ble.ready ? "arrow.left.arrow.right":"ellipsis").font(.title3).foregroundStyle(ble.ready ? Color.blue:Color.secondary);if let contact=ble.lastContactAt{Text(contact,style:.relative).font(.caption2).foregroundStyle(.secondary).accessibilityLabel(Text("마지막 통신 확인 ")+Text(contact,style:.relative))}}.frame(maxWidth:.infinity)
    device("iPhone",symbol:"iphone",battery:ble.localBattery)
   }.padding(.vertical,3)
   if !ble.ready{Text(model.connectionDetail).font(.footnote).foregroundStyle(.secondary).fixedSize(horizontal:false,vertical:true)}
   if !ble.ready{Button{model.permission();scan()}label:{Label(ble.isConnecting ? "새 QR로 연결":"QR로 Galaxy 연결",systemImage:"qrcode.viewfinder").frame(maxWidth:.infinity).padding(.vertical,5)}.relayPrimaryButton()}
  }.padding(22).relayCard()
 }
 private func device(_ title:String,symbol:String,battery:BatteryReading?,updatedAt:Date?=nil)->some View{VStack(spacing:10){Image(systemName:symbol).font(.system(size:38,weight:.light));Text(title).font(.caption.weight(.medium));Text(battery?.label ?? "—").font(.caption2).foregroundStyle(.secondary).multilineTextAlignment(.center).lineLimit(2);if let updatedAt{Text(updatedAt,style:.relative).font(.caption2).foregroundStyle(.tertiary).accessibilityLabel(Text("배터리 마지막 갱신 ")+Text(updatedAt,style:.relative))}else{Text(" ").font(.caption2).accessibilityHidden(true)}}.frame(width:86,height:154).background(Color(uiColor:.tertiarySystemFill),in:RoundedRectangle(cornerRadius:24))}

}

@MainActor private struct NotificationInbox:View {
 @ObservedObject var model:RelayModel;@ObservedObject var history:History
 let show:(RelayEvent)->Void
 @State private var search=""
 @State private var app="all"
 @State private var expanded=Set<String>()
 private var apps:[String]{Array(Set(history.state.events.map(\.label))).sorted()}
 private var groups:[NotificationGroup]{
  let rows=history.state.events.filter{(app=="all"||$0.label==app)&&(search.isEmpty||[$0.heading,$0.message,$0.label].joined(separator:" ").localizedCaseInsensitiveContains(search))}
  return Dictionary(grouping:rows,by:\.groupKey).map{NotificationGroup(id:$0.key,events:$0.value.sorted{$0.timestamp>$1.timestamp})}.sorted{$0.events[0].timestamp>$1.events[0].timestamp}
 }
 var body:some View {
  List{
   Section{
    NotificationMetrics(history:history).listRowInsets(EdgeInsets()).listRowBackground(Color.clear)
   }
   Section{
    HStack{Text("앱으로 거르기");Spacer();Menu{Button("전체"){app="all"};ForEach(apps,id:\.self){name in Button(name){app=name}}}label:{HStack(spacing:6){Text(app=="all" ? "전체":app);Image(systemName:"chevron.up.chevron.down").font(.caption2)}}}
   }
   if groups.isEmpty{
    Section{VStack(spacing:10){Image(systemName:search.isEmpty ? "bell.slash":"magnifyingglass").font(.largeTitle).foregroundStyle(.secondary);Text(search.isEmpty ? "알림이 없어요":"검색 결과가 없어요").font(.headline);Text("새로운 알림을 기다리고 있어요.").font(.footnote).foregroundStyle(.secondary)}.frame(maxWidth:.infinity).padding(.vertical,42)}
   }else{
    ForEach(groups){group in
     Section{
      if expanded.contains(group.id){
       ForEach(group.events){event in notification(event,count:1,action:{show(event)}).swipeActions(edge:.trailing,allowsFullSwipe:true){Button("삭제",role:.destructive){Task{await model.dismissEvents([event])}}}}
      }else{
       notification(group.events[0],count:group.events.count,action:{if group.events.count>1{withAnimation{_ = expanded.insert(group.id)}}else{show(group.events[0])}})
        .swipeActions(edge:.trailing,allowsFullSwipe:true){Button("삭제",role:.destructive){Task{await model.dismissEvents(group.events)}}}
      }
     }header:{if expanded.contains(group.id){HStack{Text(group.events[0].label);Spacer();Button("간략히 보기"){withAnimation{_ = expanded.remove(group.id)}}.textCase(nil)}}}
    }
   }
  }.listStyle(.insetGrouped).navigationTitle("알림").searchable(text:$search,prompt:"이름, 메시지, 앱 검색")
   .toolbar{ToolbarItem(placement:.navigationBarTrailing){Text("\(history.state.events.count)개").font(.subheadline).foregroundStyle(.secondary)}}
 }
 private func notification(_ event:RelayEvent,count:Int,action:@escaping()->Void)->some View{
  Button(action:action){NotificationRow(event:event,count:count).padding(.vertical,7)}.buttonStyle(.plain)
 }
}

@MainActor private struct NotificationMetrics:View {
 @ObservedObject var history:History
 var body:some View{
  HStack(spacing:12){
   metric("안 읽은 알림",value:history.state.events.filter{$0.read != true}.count,symbol:"circle.fill",color:.blue)
   metric("오늘 받은 알림",value:history.state.events.filter{Calendar.current.isDateInToday(Date(timeIntervalSince1970:Double($0.timestamp)/1000))}.count,symbol:"tray.fill",color:.secondary)
  }.accessibilityElement(children:.contain)
 }
 private func metric(_ title:String,value:Int,symbol:String,color:Color)->some View{
  VStack(alignment:.leading,spacing:12){Label(title,systemImage:symbol).font(.caption).foregroundStyle(color);Text("\(value)").font(.system(size:30,weight:.semibold,design:.rounded)).monospacedDigit()}.frame(maxWidth:.infinity,alignment:.leading).padding(20).relayCard()
 }
}

struct NotificationRow:View {
 let event:RelayEvent;let count:Int
 var body:some View {
  HStack(alignment:.top,spacing:14){
   ZStack(alignment:.bottomTrailing){AppBadge(event:event);if count>1{Text("\(count)").font(.system(size:10,weight:.bold)).foregroundStyle(.white).padding(.horizontal,5).padding(.vertical,3).background(.blue,in:Capsule()).overlay(Capsule().stroke(Color(uiColor:.secondarySystemGroupedBackground),lineWidth:2)).offset(x:5,y:4)}}
   VStack(alignment:.leading,spacing:5){
    HStack(alignment:.firstTextBaseline){Text(event.label).font(.caption).lineLimit(1);Spacer(minLength:8);Text(Date(timeIntervalSince1970:Double(event.timestamp)/1000),style:.time).font(.caption2).monospacedDigit()}.foregroundStyle(.secondary)
    Text(event.heading).font(.subheadline.weight(event.read == true ? .medium:.semibold)).lineLimit(1).foregroundStyle(.primary)
    Text(event.message).font(.subheadline).foregroundStyle(.secondary).lineLimit(2)
   }
   if event.read != true{Circle().fill(.blue).frame(width:6,height:6).padding(.top,24)}
  }.contentShape(Rectangle()).accessibilityElement(children:.combine)
 }
}

@MainActor struct DetailView:View {
 let event:RelayEvent;@ObservedObject var model:RelayModel;@ObservedObject var history:History
 @Environment(\.dismiss) private var dismiss;@State private var reply=""
 private var current:RelayEvent{history.state.events.first{$0.id==event.id} ?? event}
 private var active:Bool{history.state.events.contains{$0.id==event.id&&$0.androidState=="active"}}
 var body:some View {
  NavigationStack{
   ScrollView{
    VStack(alignment:.leading,spacing:24){
     HStack(alignment:.center,spacing:16){AppBadge(event:current,size:56);VStack(alignment:.leading,spacing:5){Text(current.label).font(.subheadline).foregroundStyle(.blue);Text(current.heading).font(.title3.weight(.semibold));Text(Date(timeIntervalSince1970:Double(current.timestamp)/1000),format:.dateTime.year().month().day().hour().minute()).font(.caption).foregroundStyle(.secondary)};Spacer(minLength:0)}.frame(maxWidth:.infinity,alignment:.leading).padding(22).relayCard()
     VStack(alignment:.leading,spacing:18){Text(current.message).font(.body).frame(maxWidth:.infinity,alignment:.leading);Label(active ? "Galaxy 알림 활성":"Galaxy에서 더 이상 열 수 없는 알림",systemImage:active ? "checkmark.circle":"minus.circle").font(.caption).foregroundStyle(.secondary)}.padding(22).relayCard().contentShape(Rectangle()).onTapGesture{if active{Task{await model.command("OPEN",current)}}}.accessibilityAction(named:Text("Galaxy에서 열기")){if active{Task{await model.command("OPEN",current)}}}
     Text("본문을 다시 누르면 Galaxy에서 원본 알림을 엽니다.").font(.footnote).foregroundStyle(.secondary).padding(.horizontal,4)
     if current.reply != nil{replyCard}
     if !model.notice.isEmpty{Text(model.notice).font(.footnote).foregroundStyle(.secondary).padding(.horizontal,4)}
    }.padding(20)
   }.background(Color(uiColor:.systemGroupedBackground)).navigationTitle("알림").navigationBarTitleDisplayMode(.inline)
    .toolbar{
     ToolbarItem(placement:.cancellationAction){Button{dismiss()}label:{Image(systemName:"xmark")}.accessibilityLabel("닫기")}
     ToolbarItem(placement:.navigationBarTrailing){Button(role:.destructive){Task{await model.dismissEvents([current]);if !history.state.events.contains(where:{$0.id==event.id}){dismiss()}}}label:{Image(systemName:"trash")}.accessibilityLabel("알림 삭제").disabled(model.busy)}
    }
  }.onAppear{history.read(event.id)}
 }
 private var replyCard:some View{
  VStack(alignment:.leading,spacing:14){
   Text("답장").font(.headline)
   TextField("메시지를 입력하세요",text:$reply,axis:.vertical).lineLimit(3...8).disabled(!active||model.busy||history.pending(event.id))
   HStack{Text("\(reply.utf16.count) / 500").font(.caption).foregroundStyle(.secondary);Spacer();Button{Task{await model.command("REPLY",current,replyText:reply)}}label:{Image(systemName:"arrow.up").font(.headline).frame(width:24,height:24)}.relayPrimaryButton().accessibilityLabel("답장 보내기").disabled(!active||model.busy||history.pending(event.id)||reply.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty||reply.utf16.count>500)}
  }.padding(22).relayCard()
 }
}

@MainActor struct SettingsView:View {
 @ObservedObject var model:RelayModel;@ObservedObject var ble:BLELink
 let scan:()->Void
 @State private var erase=false
 @State private var revoke=false
 var body:some View {
  NavigationStack{
   Form{
    Section{
     HStack(spacing:14){Image(systemName:"iphone").font(.largeTitle).foregroundStyle(.blue);VStack(alignment:.leading,spacing:4){Text("Galaxy Relay").font(.headline);Text("내 Galaxy와 이어지는 알림").font(.footnote).foregroundStyle(.secondary)}}.padding(.vertical,10)
    }
    Section("연결"){
     LabeledContent("Galaxy",value:model.settings?.galaxyName ?? "연결 전")
     LabeledContent("이 iPhone의 네트워크",value:ble.phoneNetwork.label)
     ConnectionStatusLabel(model:model,ble:ble)
     Text(model.connectionDetail).font(.caption).foregroundStyle(.secondary)
     if !ble.ready{Button{model.permission();scan()}label:{Label("QR로 연결",systemImage:"qrcode.viewfinder")}}
     Button("이 iPhone의 알림 허용"){model.permission()}
     Button("Galaxy 연결 테스트"){Task{await model.command("PING",nil)}}.disabled(model.settings==nil||model.busy)
    }
    CallAlertSettings(calls:model.callAlerts)
    DisconnectLocationSettings(recorder:ble.disconnectLocation)
    Section("개인정보"){Toggle("알림 미리보기 숨기기",isOn:$model.hidePreview).onChange(of:model.hidePreview){UserDefaults.standard.set($0,forKey:"hidePreview");model.callAlerts.privacyChanged($0)}}
    Section("데이터"){
     Button("알림 기록 모두 삭제",role:.destructive){erase=true}
     Button("Galaxy 연결 해제",role:.destructive){revoke=true}.disabled(model.settings==nil && !ble.isConnecting)
    }
    Section{Text("블루투스가 끊긴 동안에는 앱을 열면 서버를 통해 동기화합니다.");Text("AltStore에서 7일 만료 전에 서명을 갱신하세요.")}header:{Text("수신 및 설치")}footer:{Text("앱 내부의 아이콘은 Galaxy 원본 앱 아이콘입니다. 잠금화면 알림의 앱 아이콘은 Relay로 표시됩니다.")}
   }.navigationTitle("설정")
    .alert("모든 알림 기록을 삭제할까요?",isPresented:$erase){Button("삭제",role:.destructive){try? model.history.clear()};Button("취소",role:.cancel){}}
    .alert("Galaxy 연결을 해제할까요?",isPresented:$revoke){Button("연결 해제",role:.destructive){Task{await model.unpair()}};Button("취소",role:.cancel){}}
  }
 }
}

private extension View {
 func relayCard()->some View{background(Color(uiColor:.secondarySystemGroupedBackground),in:RoundedRectangle(cornerRadius:28,style:.continuous))}
 @ViewBuilder func relayPrimaryButton()->some View{
  #if compiler(>=6.2)
  if #available(iOS 26.0,*){buttonStyle(.glassProminent)}else{buttonStyle(.borderedProminent)}
  #else
  buttonStyle(.borderedProminent)
  #endif
 }
}


@MainActor private struct GalaxyCallView:View {
 @ObservedObject var calls:CallAlerts
 var body:some View{VStack(spacing:28){Spacer();Image(systemName:"phone.connection.fill").font(.system(size:52)).foregroundStyle(.green);Text(calls.caller).font(.title2.bold());Text("Galaxy에서 통화 중").font(.headline);if let date=calls.connectedAt{Text(date,style:.timer).monospacedDigit().font(.largeTitle)};Text("음성은 Galaxy 또는 Galaxy에 연결된 이어폰에서 들립니다.").font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center);Text(calls.status).font(.caption).foregroundStyle(.secondary);Spacer();Button{calls.hangup()}label:{Label("Galaxy 통화 종료",systemImage:"phone.down.fill").font(.headline).padding(24).frame(maxWidth:.infinity)}.buttonStyle(.borderedProminent).tint(.red).padding(.bottom,40)}.padding(30).frame(maxWidth:.infinity,maxHeight:.infinity).background(Color(uiColor:.systemGroupedBackground)).interactiveDismissDisabled()}
}
