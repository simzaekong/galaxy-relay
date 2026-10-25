import SwiftUI
@MainActor struct DisconnectLocationCard:View {
 @ObservedObject var recorder:DisconnectLocationRecorder
 var body:some View{DisconnectLocationContent(recorder:recorder).padding(22).background(Color(uiColor:.secondarySystemGroupedBackground),in:RoundedRectangle(cornerRadius:28))}
}
@MainActor struct DisconnectLocationContent:View {
 @ObservedObject var recorder:DisconnectLocationRecorder
 var body:some View{VStack(alignment:.leading,spacing:12){
  Label("마지막 연결 해제 위치",systemImage:"mappin.and.ellipse").font(.headline)
  Text(recorder.status).font(.footnote).foregroundStyle(.secondary)
  if let point=recorder.record{
   Text(Date(timeIntervalSince1970:Double(point.triggeredAt)/1000),format:.dateTime.month().day().hour().minute()).font(.subheadline)
   Text("정확도 약 \(Int(point.accuracy.rounded()))m · 이 iPhone의 위치").font(.caption).foregroundStyle(.secondary)
   Button("지도에서 보기"){recorder.openMap()}.buttonStyle(.bordered)
  }else{Text("Galaxy의 실제 위치가 아닌, 연결이 끊겼을 때 이 iPhone이 있던 곳을 저장합니다.").font(.caption).foregroundStyle(.secondary)}
 }.frame(maxWidth:.infinity,alignment:.leading)}
}
@MainActor struct DisconnectLocationSettings:View {
 @ObservedObject var recorder:DisconnectLocationRecorder
 @State private var erase=false
 var body:some View{Section{
  Toggle("연결 해제 시에만 위치 기록",isOn:Binding(get:{recorder.enabled},set:{recorder.setEnabled($0)}))
  Button("위치 권한 설정"){recorder.requestPermission()}
  Text(recorder.authorization).font(.caption).foregroundStyle(.secondary)
  Button("마지막 위치 기록 삭제",role:.destructive){erase=true}
 }header:{Text("연결 해제 위치")}footer:{Text("사용 중 허용 후 다시 눌러 항상 허용을 설정하세요. 연결 중에는 위치를 측정하지 않으며, 단절 후 최대 15초만 한 번 조회합니다. 위치는 이 기기에만 저장합니다.")}
 .alert("마지막 위치 기록을 삭제할까요?",isPresented:$erase){Button("삭제",role:.destructive){recorder.clear()};Button("취소",role:.cancel){}}
 }
}
