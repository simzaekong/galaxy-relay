import SwiftUI
@main @MainActor struct GalaxyRelayApp:App {
 @StateObject private var model=RelayModel()
 @Environment(\.scenePhase) private var phase
 var body:some Scene {WindowGroup{InboxView(model:model).onAppear{
 #if targetEnvironment(simulator)
 DesignPreview.prepare(model)
 #endif
 }.onChange(of:phase){new in model.foreground=new == .active;model.ble.disconnectLocation.foregroundChanged(new == .active);if new == .active{model.ble.connect();Task{await model.retryRevocations();await model.sync()}}}.task{model.foreground=true;while !Task.isCancelled{await model.retryRevocations();await model.sync();try? await Task.sleep(nanoseconds:60_000_000_000)}}}}
}
