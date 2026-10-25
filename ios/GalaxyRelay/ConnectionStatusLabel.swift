import SwiftUI

extension RelayConnectionStatus {
 var color:Color{switch self{case .bluetooth:.green;case .server:.blue;case .connecting:.orange;case .disconnected:.red}}
}

@MainActor struct ConnectionStatusLabel:View {
 @ObservedObject var model:RelayModel
 @ObservedObject var ble:BLELink
 var body:some View{
  TimelineView(.periodic(from:.now,by:1)){context in
   let status=model.connectionStatus(at:context.date)
   Label(status.title,systemImage:status.symbol).foregroundStyle(status.color)
  }
 }
}
