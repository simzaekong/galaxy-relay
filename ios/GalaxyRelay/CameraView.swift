import SwiftUI
import WebKit

@MainActor final class CameraModel:ObservableObject {
 typealias Start=(PairSettings)async throws->[String:Any]
 typealias End=([String:Any],PairSettings)async->Void
 private let startRequest:Start
 private let endRequest:End
 init(start:@escaping Start={settings in try await CloudLink(settings:settings).call("/api/native/camera/start")},end:@escaping End={grant,settings in await CameraModel.cancelRequest(grant,settings)}){startRequest=start;endRequest=end}
 @Published private(set) var status="Galaxy 카메라에 연결하는 중…"
 @Published private(set) var config:[String:Any]?
 @Published private(set) var ended=false
 private var settings:PairSettings?
 private var grant:[String:Any]?
 private var generation=UUID()
 func start(_ settings:PairSettings)async{
  guard self.settings==nil else{return};self.settings=settings;let ticket=generation
  do{
   let value=try await startRequest(settings)
   guard !ended,generation==ticket,!Task.isCancelled else{await endRequest(value,settings);return}
   guard let expires=value["expires"] as? NSNumber,expires.int64Value>NativeCrypto.now(),value["role"] as? String=="viewer",value["nativeId"] as? String==settings.deviceId else{throw RelayError.invalid}
   grant=value;var client=value;client["linkKey"]=settings.linkKey;config=client;status="Galaxy가 카메라 요청을 확인하고 있습니다…"
  }catch{guard generation==ticket,!ended else{return};status="카메라 연결을 시작하지 못했습니다. Galaxy 업데이트·자동 수락 설정·인터넷 상태를 확인해주세요."}
 }
 func event(_ event:[String:Any]){
  guard !ended else{return}
  if let text=event["text"] as? String{status=String(text.prefix(300))}
  if event["type"] as? String=="closed"{close()}
 }
 func close(){
  guard !ended else{return};ended=true;generation=UUID();config=nil
  if let grant,let settings{Task{await endRequest(grant,settings)}}
 }
 static func cancelRequest(_ grant:[String:Any],_ settings:PairSettings)async{
  guard let base=URL(string:settings.backend),base.scheme=="https",base.host != nil else{return}
  var body=grant;body["op"]="end";body["after"]=0
  var request=URLRequest(url:base.appendingPathComponent("api/camera/session"));request.httpMethod="POST";request.timeoutInterval=5;request.setValue("application/json",forHTTPHeaderField:"Content-Type");request.httpBody=try? NativeCrypto.json(body)
  let session=URLSession(configuration:.ephemeral);defer{session.invalidateAndCancel()};_ = try? await session.data(for:request)
 }
}

@MainActor struct CameraView:View {
 let settings:PairSettings
 @StateObject private var camera=CameraModel()
 @Environment(\.dismiss) private var dismiss
 @Environment(\.scenePhase) private var phase
 var body:some View{
  NavigationStack{
   VStack(spacing:0){
    if let config=camera.config,!camera.ended{CameraWebView(settings:settings,config:config,camera:camera)}
    else{Spacer();Image(systemName:"video.fill").font(.system(size:48)).foregroundStyle(.blue);Text(camera.status).multilineTextAlignment(.center).padding(24);Spacer()}
   }.background(.black).foregroundStyle(.white).navigationTitle("Galaxy 카메라").navigationBarTitleDisplayMode(.inline)
    .toolbar{ToolbarItem(placement:.cancellationAction){Button("닫기"){camera.close();dismiss()}}}
  }.interactiveDismissDisabled()
   .task{await camera.start(settings)}
   .onChange(of:phase){if $0 != .active{camera.close();dismiss()}}
   .onDisappear{camera.close()}
 }
}

@MainActor private struct CameraWebView:UIViewRepresentable {
 let settings:PairSettings
 let config:[String:Any]
 let camera:CameraModel
 func makeCoordinator()->Coordinator{Coordinator(settings:settings,config:config,camera:camera)}
 func makeUIView(context:Context)->WKWebView{
  let config=WKWebViewConfiguration();config.allowsInlineMediaPlayback=true;config.mediaTypesRequiringUserActionForPlayback=[];config.websiteDataStore = .nonPersistent();config.userContentController.add(context.coordinator,name:"camera")
  let view=WKWebView(frame:.zero,configuration:config);view.navigationDelegate=context.coordinator;view.uiDelegate=context.coordinator;view.isOpaque=false;view.backgroundColor = .black;view.scrollView.backgroundColor = .black
  if let url=context.coordinator.url{view.load(URLRequest(url:url,cachePolicy:.reloadIgnoringLocalCacheData))};return view
 }
 func updateUIView(_ view:WKWebView,context:Context){}
 static func dismantleUIView(_ view:WKWebView,coordinator:Coordinator){
  view.evaluateJavaScript("window.closeCamera && window.closeCamera()",completionHandler:nil);view.configuration.userContentController.removeScriptMessageHandler(forName:"camera");view.stopLoading();view.loadHTMLString("",baseURL:nil);view.navigationDelegate=nil;view.uiDelegate=nil
 }
 final class Coordinator:NSObject,WKNavigationDelegate,WKUIDelegate,WKScriptMessageHandler {
  let url:URL?
  let config:[String:Any]
  weak var camera:CameraModel?
  init(settings:PairSettings,config:[String:Any],camera:CameraModel){
   let base=URLComponents(string:settings.backend)
   if base?.scheme=="https",base?.host != nil,base?.user==nil,base?.password==nil,base?.query==nil,base?.fragment==nil,base?.path=="" || base?.path=="/"{url=URL(string:settings.backend)?.appendingPathComponent("api/camera/client")}else{url=nil}
   self.config=config;self.camera=camera
  }
  func webView(_ view:WKWebView,decidePolicyFor action:WKNavigationAction,decisionHandler:@escaping(WKNavigationActionPolicy)->Void){decisionHandler(action.targetFrame?.isMainFrame==true&&action.request.url==url ? .allow:.cancel)}
  func webView(_ view:WKWebView,didFinish navigation:WKNavigation!){guard view.url==url,let data=try? NativeCrypto.json(config),let raw=String(data:data,encoding:.utf8) else{return};view.evaluateJavaScript("window.startCamera("+raw+")",completionHandler:nil)}
  func webView(_ view:WKWebView,didFailProvisionalNavigation navigation:WKNavigation!,withError error:Error){camera?.event(["type":"status","text":"연결 화면을 열지 못했습니다. 서버 배포와 인터넷 상태를 확인해주세요."]);camera?.close()}
  func webViewWebContentProcessDidTerminate(_ view:WKWebView){camera?.close()}
  func userContentController(_ controller:WKUserContentController,didReceive message:WKScriptMessage){guard message.frameInfo.isMainFrame,message.frameInfo.request.url==url,let event=message.body as? [String:Any] else{return};camera?.event(event)}
  // iPhone never captures a camera or microphone, even if page content requests it.
  func webView(_ view:WKWebView,requestMediaCapturePermissionFor origin:WKSecurityOrigin,initiatedByFrame frame:WKFrameInfo,type:WKMediaCaptureType,decisionHandler:@escaping(WKPermissionDecision)->Void){decisionHandler(.deny)}
 }
}
