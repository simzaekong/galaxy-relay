import SwiftUI
import UIKit
@MainActor struct CallAlertSettings:View {
 @ObservedObject var calls:CallAlerts
 @State private var confirm=false
 var body:some View{Section{
  Toggle("전화 수신 화면 및 벨소리",isOn:Binding(get:{calls.enabled},set:{if $0{confirm=true}else{calls.setEnabled(false)}}))
  Text(calls.status).font(.caption).foregroundStyle(.secondary)
  if calls.diagnostic != nil{
   VStack(alignment:.leading,spacing:6){Text("마지막 CallKit 요청 결과").font(.caption).foregroundStyle(.secondary);Text(calls.diagnosticSummary).font(.footnote).textSelection(.enabled)}
   Button("진단 코드 복사"){UIPasteboard.general.string=calls.diagnosticSummary}
   Button("진단 기록 삭제"){calls.clearDiagnostic()}
  }
 }header:{Text("Galaxy 전화 알림")}footer:{Text("Bluetooth로 연결된 동안 실제 Galaxy 전화 수신에만 사용합니다. iPhone에서 통화할 수 없습니다. 받기는 Galaxy에서 하세요. iPhone의 거절·종료는 Galaxy의 실제 통화를 끊습니다. Galaxy에서 받으면 통화 중 상태를 표시합니다.")}
 .alert("전화 수신 알림을 켤까요?",isPresented:$confirm){Button("알림 켜기"){calls.setEnabled(true)};Button("취소",role:.cancel){}}message:{Text("iOS 통화 화면과 벨소리를 사용합니다. 받기는 Galaxy에서 하세요. 거절·종료 버튼은 권한이 허용된 Galaxy의 실제 통화를 끊습니다. 음성은 전달되지 않습니다.")}
 }
}
