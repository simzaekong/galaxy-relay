import Foundation
import Combine
import CoreBluetooth
import CryptoKit
import UIKit
import Network
enum PairPhase {case idle,searching,connecting,discovering,authenticating,confirming,connected,failed}
@MainActor final class BLELink:NSObject,ObservableObject,@preconcurrency CBCentralManagerDelegate,@preconcurrency CBPeripheralDelegate {
 static let rx=CBUUID(string:"58b80002-a8da-4eae-bba8-50938ca8ca5b"),tx=CBUUID(string:"58b80003-a8da-4eae-bba8-50938ca8ca5b")
 @Published private(set) var phase:PairPhase = .idle
 var isConnecting:Bool{[PairPhase.searching,.connecting,.discovering,.authenticating,.confirming].contains(phase)}
 var isActivelyConnecting:Bool{
  #if targetEnvironment(simulator)
  if ProcessInfo.processInfo.arguments.contains("--design-preview"){return isConnecting}
  #endif
  return isConnecting && enabled && manager.state == .poweredOn
 }
 @Published private(set) var ready=false
 @Published private(set) var status="Galaxy 연결을 준비하세요"
 let disconnectLocation=DisconnectLocationRecorder()
 let remote=RemoteModel()
 @Published private(set) var remoteSupported=false
 @Published private(set) var signal=SignalTrend()
 @Published private(set) var signalAt:Date?
 func readSignal(){guard ready else{return};if signalAt.map({Date().timeIntervalSince($0)>8}) ?? false{signal=SignalTrend();signalAt=nil};peripheral?.readRSSI()}
 func peripheral(_ p:CBPeripheral,didReadRSSI RSSI:NSNumber,error:Error?){guard active(p),ready else{return};guard error==nil,(-127 ... -1).contains(RSSI.intValue) else{signal=SignalTrend();signalAt=nil;return};signal.record(RSSI.intValue);signalAt=Date()}

 var message:(([String:Any])->Void)?
 var transportLost:(()->Void)?
 private var manager:CBCentralManager!;private var peripheral:CBPeripheral?;private var input:CBCharacteristic?;private var output:CBCharacteristic?
 private var pairId="",service="",root=Data(),session:Data?,handshake:ClientHandshake?
 private var enabled=false,resumable=false
 var automaticReconnectEnabled:Bool{enabled&&resumable}
 private var retryAttempt=0
 private var cancelling:CBPeripheral?
 private var cancelDeadline:Task<Void,Never>?
 private var knownIdentifier:UUID?
 @Published private(set) var localBattery=BatteryReading(level:nil,state:"unknown")
 @Published private(set) var remoteBattery:PeerBattery?
 @Published private(set) var lastContactAt:Date?
 var connectionVerified:Bool{ready && lastContactAt.map{Date().timeIntervalSince($0)<=330}==true}
 @Published private(set) var phoneNetwork:PhoneNetwork = .unknown
 private let networkMonitor=NWPathMonitor()
 private var networkKnown=false,networkSupported=false
 private func networkSnapshot()->PhoneNetwork{guard networkKnown else{return .unknown};let path=networkMonitor.currentPath;return PhoneNetwork.classify(available:path.status == .satisfied,unavailable:path.status == .unsatisfied)}
 var acceptsOfflineRelay:Bool{networkSnapshot() == .offline}
 private func sendNetwork(_ probe:String=""){guard ready,networkSupported else{return};phoneNetwork=networkSnapshot();try? send(["kind":"network_status","probeId":probe,"network":phoneNetwork.rawValue])}
 private var healthSupported=false,health=LinkHealth(),healthDeadline:Task<Void,Never>?
 func checkHealth(){guard ready,healthSupported else{return};let now=ProcessInfo.processInfo.systemUptime;guard health.begin(NativeCrypto.uuid(),now:now),let id=health.pending else{return};status="블루투스 연결됨 · 실제 통신 확인 중…"
  do{updateLocalBattery();try send(["kind":"health_ping","probeId":id,"battery":batteryObject(),"network":networkSnapshot().rawValue]);healthDeadline?.cancel();healthDeadline=Task{[weak self] in do{try await Task.sleep(nanoseconds:30_000_000_000)}catch{return};guard let self,self.ready,self.health.expired(now:ProcessInfo.processInfo.systemUptime) else{return};self.recoverConnection("응답 없음 · 자동 재연결 중…",recordLocation:false)}}catch{recoverConnection("통신 확인 실패 · 자동 재연결 중…",recordLocation:false)}
 }
 private func batteryObject()->[String:Any]{(try? JSONSerialization.jsonObject(with:JSONEncoder().encode(localBattery)) as? [String:Any]) ?? ["state":"unknown","level":NSNull()]}
 private func acceptBattery(_ plain:[String:Any])throws{guard let raw=plain["battery"] as? [String:Any],let time=plain["timestamp"] as? NSNumber else{throw RelayError.invalid};let battery=try JSONDecoder().decode(BatteryReading.self,from:NativeCrypto.json(raw));guard battery.valid else{throw RelayError.invalid};let saved=PeerBattery(pairId:pairId,battery:battery,reportedAt:time.int64Value);remoteBattery=saved;if let data=try? JSONEncoder().encode(saved){UserDefaults.standard.set(data,forKey:"peerBattery")}}
 private var batteryPolicy=BatterySyncPolicy(),peerBatterySupported=false
 private var batteryTask:Task<Void,Never>?
 private var retry:Task<Void,Never>?
 private var deadline:Task<Void,Never>?
 func progress(_ next:PairPhase,_ text:String){phase=next;status=text;deadline?.cancel();deadline=nil;guard isConnecting,!(resumable && (next == .searching || next == .connecting)) else{return};let seconds:UInt64=resumable ? 60:30;deadline=Task{[weak self] in do{try await Task.sleep(nanoseconds:seconds*1_000_000_000)}catch{return};guard !Task.isCancelled else{return};self?.expireConnectionAttempt()}}
 func expireConnectionAttempt(){guard isConnecting else{return};if resumable{recoverConnection("연결 확인 지연 · 자동 재연결 중…");return};let searching=phase == .searching;stop();progress(.failed,searching ? "Galaxy를 찾지 못했습니다. Galaxy QR 화면과 블루투스를 확인하고 다시 스캔해주세요.":"연결 확인 시간이 초과됐습니다. 두 기기를 가까이 두고 새 QR로 다시 연결해주세요.")}

 private var buffer=FrameBuffer(),writes=[Data](),writing=false,seen=MessageReplayWindow()
 override init(){super.init();networkMonitor.pathUpdateHandler={[weak self] _ in Task{@MainActor in guard let self else{return};self.networkKnown=true;self.phoneNetwork=self.networkSnapshot();self.sendNetwork()}};networkMonitor.start(queue:DispatchQueue(label:"relay.network"));UIDevice.current.isBatteryMonitoringEnabled=true;updateLocalBattery();NotificationCenter.default.addObserver(self,selector:#selector(batteryChanged),name:UIDevice.batteryLevelDidChangeNotification,object:nil);NotificationCenter.default.addObserver(self,selector:#selector(batteryChanged),name:UIDevice.batteryStateDidChangeNotification,object:nil);manager=CBCentralManager(delegate:self,queue:nil,options:[CBCentralManagerOptionRestoreIdentifierKey:"GalaxyRelay.Native.central.v2"])}
 func configure(_ p:PairSettings){stop();enabled=true;resumable=true;pairId=p.pairId;disconnectLocation.bind(pairId);if UserDefaults.standard.string(forKey:"blePeerPairId")==pairId{knownIdentifier=UserDefaults.standard.string(forKey:"blePeerIdentifier").flatMap(UUID.init(uuidString:))};service=p.serviceUUID;if let data=UserDefaults.standard.data(forKey:"peerBattery"),let saved=try? JSONDecoder().decode(PeerBattery.self,from:data),saved.pairId==pairId,saved.battery.valid{remoteBattery=saved};root=(try? NativeCrypto.bytes(p.linkKey)) ?? Data();if manager.state == .poweredOn{connect()}}
 func pair(_ qr:PairQR)throws{let key=try NativeCrypto.bytes(qr.secret);guard key.count==32 else{throw RelayError.invalid};stop();enabled=true;pairId=qr.pairId;disconnectLocation.bind(pairId);service=qr.serviceUUID;root=key;connect()}
 func connect(){
  updateLocalBattery();queueBattery()
  guard enabled,!service.isEmpty,root.count==32 else{return}
  if let p=cancelling{if p.state == .disconnected{cancelling=nil;cancelDeadline?.cancel()}else{return}}
  guard manager.state == .poweredOn else{status=manager.state == .unauthorized ? "설정에서 블루투스 권한을 허용해주세요.":"블루투스가 켜지면 자동으로 연결합니다.";return}
  if let p=peripheral{
   if p.state == .connected{if ready,healthSupported,lastContactAt.map({Date().timeIntervalSince($0)>60}) ?? true{checkHealth()};if phase != .discovering && input==nil{beginDiscovery(p)};return}
   if p.state == .connecting{return}
   p.delegate=nil;peripheral=nil
  }
  if resumable,let id=knownIdentifier,let p=manager.retrievePeripherals(withIdentifiers:[id]).first{
   peripheral=p;p.delegate=self;progress(.connecting,"Galaxy를 기다리는 중 · 자동 재연결");manager.connect(p,options:nil);return
  }
  if phase != .searching{progress(.searching,resumable ? "Galaxy 자동 재연결 대기 중…":"QR 확인 완료 · Galaxy를 찾고 있습니다…")}
  if !manager.isScanning{manager.scanForPeripherals(withServices:[CBUUID(string:service)],options:nil)}
 }
 func centralManagerDidUpdateState(_ central:CBCentralManager){
  guard enabled else{status="Galaxy 연결을 준비하세요";return}
  if central.state == .poweredOn{connect()}
  else{if ready && (central.state == .poweredOff || central.state == .resetting){disconnectLocation.connectionLost()};deadline?.cancel();retry?.cancel();peripheral?.delegate=nil;peripheral=nil;cancelling=nil;resetSession();phase = .searching;status=central.state == .unauthorized ? "설정에서 블루투스 권한을 허용해주세요.":"블루투스가 켜지면 자동으로 연결합니다."}
 }
 func centralManager(_ central:CBCentralManager,willRestoreState dict:[String:Any]){
  guard let list=dict[CBCentralManagerRestoredStatePeripheralsKey] as? [CBPeripheral] else{return}
  for p in list{
   let matches=p.identifier==knownIdentifier || p.services?.contains(where:{$0.uuid==CBUUID(string:service)})==true
   if enabled&&resumable&&matches&&peripheral==nil{peripheral=p;knownIdentifier=p.identifier;p.delegate=self;progress(.connecting,"저장된 Galaxy 연결 복원 중…");if central.state == .poweredOn{connect()}}
   else{central.cancelPeripheralConnection(p)}
  }
 }
 private func beginDiscovery(_ p:CBPeripheral){progress(.discovering,"Galaxy 연결됨 · 연결 기능 확인 중…");p.discoverServices([CBUUID(string:service)])}
 func centralManager(_ central:CBCentralManager,didDiscover p:CBPeripheral,advertisementData:[String:Any],rssi:NSNumber){guard enabled,peripheral==nil else{return};manager.stopScan();peripheral=p;p.delegate=self;progress(.connecting,"Galaxy 발견 · 블루투스 연결 중…");manager.connect(p,options:nil)}
 func centralManager(_ central:CBCentralManager,didConnect p:CBPeripheral){guard active(p) else{central.cancelPeripheralConnection(p);return};beginDiscovery(p)}
 func centralManager(_ central:CBCentralManager,didFailToConnect p:CBPeripheral,error:Error?){guard active(p) else{return};peripheral?.delegate=nil;peripheral=nil;resetSession();progress(.connecting,"Galaxy 재연결 대기 중…");scheduleRetry()}
 func centralManager(_ central:CBCentralManager,didDisconnectPeripheral p:CBPeripheral,error:Error?){if cancelling === p{cancelDeadline?.cancel();cancelling=nil;if enabled{scheduleRetry()};return};guard active(p) else{return};if resumable{knownIdentifier=p.identifier};disconnected()}
 private func active(_ p:CBPeripheral)->Bool{enabled&&peripheral === p}
 private func resetSession(){remote.disconnected();remoteSupported=false;signal=SignalTrend();signalAt=nil;networkSupported=false;healthDeadline?.cancel();healthDeadline=nil;healthSupported=false;lastContactAt=nil;health.reset(now:ProcessInfo.processInfo.systemUptime);if ready{transportLost?()};batteryTask?.cancel();batteryTask=nil;batteryPolicy=BatterySyncPolicy();peerBatterySupported=false;ready=false;session=nil;handshake=nil;input=nil;output=nil;buffer=FrameBuffer();writes.removeAll();writing=false;seen=MessageReplayWindow()}
 private func disconnected(){guard enabled else{return};if ready{disconnectLocation.connectionLost()};peripheral?.delegate=nil;peripheral=nil;resetSession();retry?.cancel()
  if resumable{progress(.connecting,"블루투스 끊김 · 자동 재연결 중…");connect()}
  else{scheduleRetry()}
 }
 private func scheduleRetry(){
  guard enabled else{return};retry?.cancel();let delay:[UInt64]=[1,5,15,30,60];let seconds=delay[min(retryAttempt,delay.count-1)];retryAttempt=min(retryAttempt+1,delay.count-1)
  retry=Task{[weak self] in do{try await Task.sleep(nanoseconds:seconds*1_000_000_000)}catch{return};guard !Task.isCancelled else{return};self?.connect()}
 }
 func recoverConnection(_ text:String,recordLocation:Bool=true){
  guard enabled else{return};guard resumable else{fail(text);return};if ready&&recordLocation{disconnectLocation.connectionLost()}
  deadline?.cancel();retry?.cancel();manager.stopScan();let previous=peripheral;peripheral=nil;previous?.delegate=nil;resetSession();progress(.connecting,text)
  if let p=previous,p.state == .connected || p.state == .connecting{cancelling=p;manager.cancelPeripheralConnection(p)
   cancelDeadline?.cancel();cancelDeadline=Task{[weak self,weak p] in do{try await Task.sleep(nanoseconds:10_000_000_000)}catch{return};guard !Task.isCancelled,let self,let p,self.enabled,self.cancelling === p else{return};self.cancelling=nil;self.scheduleRetry()}
  }else{scheduleRetry()}
 }
 func peripheral(_ p:CBPeripheral,didDiscoverServices error:Error?){guard active(p),phase == .discovering else{return};guard error==nil,let found=p.services?.first(where:{$0.uuid==CBUUID(string:service)}) else{recoverConnection("Galaxy 연결 기능 재확인 중 · 자동 복구 대기");return};p.discoverCharacteristics([Self.rx,Self.tx],for:found)}
 func peripheral(_ p:CBPeripheral,didDiscoverCharacteristicsFor s:CBService,error:Error?){guard active(p),phase == .discovering else{return};guard error==nil else{recoverConnection("Galaxy 연결 기능 확인 실패 (B02) · 자동 복구 대기");return};for c in s.characteristics ?? []{if c.uuid==Self.rx{input=c};if c.uuid==Self.tx{output=c}};guard input != nil,let out=output else{fail("Galaxy 연결 기능이 맞지 않습니다. 양쪽 앱을 업데이트해주세요.");return};progress(.authenticating,"Galaxy 확인 완료 · 보안 연결 설정 중…");p.setNotifyValue(true,for:out)}
 func peripheral(_ p:CBPeripheral,didUpdateNotificationStateFor c:CBCharacteristic,error:Error?){guard active(p),c.uuid==Self.tx else{return};guard error==nil,c.isNotifying else{recoverConnection("Galaxy 응답 수신 설정 실패 (B03) · 자동 복구 대기");return};do{if handshake==nil{handshake=try ClientHandshake(pairId:pairId,root:root)};if let hello=handshake?.hello(){try enqueue(hello)}}catch{fail("보안 연결 요청을 만들지 못했습니다. 새 QR로 다시 연결해주세요. (B04)")}}
 func peripheral(_ p:CBPeripheral,didUpdateValueFor c:CBCharacteristic,error:Error?){guard active(p),c.uuid==Self.tx else{return};guard error==nil,let data=c.value else{recoverConnection("Galaxy 응답 수신 지연 (B05) · 자동 복구 대기");return};let complete:Data?;do{complete=try buffer.feed(data)}catch{recoverConnection("연결 응답 조립 실패 (B06) · 자동 복구 대기");return};guard let complete else{return};do{try receive(NativeCrypto.object(complete))}catch HandshakeFailure.proofMismatch{fail("QR 인증 정보가 Galaxy와 일치하지 않습니다. Galaxy에서 새 QR을 만들어 스캔해주세요. (B07)")}catch HandshakeFailure.invalidWelcome{fail("Galaxy의 인증 응답이 예상과 다릅니다. 새 QR로 다시 연결해주세요. (B08)")}catch HandshakeFailure.invalidPublicKey{fail("Galaxy의 보안 키를 확인하지 못했습니다. 다시 연결해주세요. (B09)")}catch LinkReceiveFailure.decrypt{fail("Galaxy 연결 데이터를 복호화하지 못했습니다. 새 QR로 다시 연결해주세요. (B10)")}catch LinkReceiveFailure.clock{recoverConnection("오래된 응답 또는 기기 시간 차이 (B11) · 자동 재연결 대기")}catch LinkReceiveFailure.capacity{recoverConnection("보안 세션 갱신 · 자동 재연결 중…")}catch LinkReceiveFailure.replay{fail("중복되거나 오래된 연결 응답을 받았습니다. 다시 연결해주세요. (B12)")}catch{fail("Galaxy의 연결 데이터 형식을 확인하지 못했습니다. 다시 연결해주세요. (B13)")}}
 func peripheral(_ p:CBPeripheral,didWriteValueFor c:CBCharacteristic,error:Error?){guard active(p),c.uuid==Self.rx else{return};writing=false;if error != nil{recoverConnection("Galaxy 전송 요청 실패 (B14) · 자동 복구 대기");return};drain()}
 enum LinkReceiveFailure:Error{case decrypt,clock,replay,capacity}
 private func receive(_ wire:[String:Any])throws{
  if wire["kind"] as? String=="welcome"{
   guard session==nil,var attempt=handshake else{throw HandshakeFailure.invalidWelcome}
   session=try attempt.welcome(wire);handshake=attempt;return
  }
  guard let key=session,let iv=wire["iv"] as? String,let cipher=wire["ciphertext"] as? String else{throw RelayError.authentication}
  let packet=SealedPacket(iv:iv,ciphertext:cipher),decoded:Data
  do{decoded=try NativeCrypto.open(packet,key:key,aad:NativeCrypto.wireAAD(pairId,"android-to-ios"))}catch{throw LinkReceiveFailure.decrypt}
  let plain=try NativeCrypto.object(decoded)
  guard let id=plain["messageId"] as? String,UUID(uuidString:id) != nil,let time=plain["timestamp"] as? NSNumber else{throw RelayError.invalid}
  guard abs(NativeCrypto.now()-time.int64Value)<120000 else{throw LinkReceiveFailure.clock}
  switch seen.record(id,timestamp:time.int64Value,now:NativeCrypto.now()){case .replay:throw LinkReceiveFailure.replay;case .capacity:throw LinkReceiveFailure.capacity;case .accepted:
   if plain["kind"] as? String=="network_query"{guard ready,networkSupported,let probe=plain["probeId"] as? String,UUID(uuidString:probe) != nil else{throw RelayError.invalid};lastContactAt=Date();sendNetwork(probe);return}
   if ["health_ping","health_pong"].contains(plain["kind"] as? String ?? ""){
    guard ready,healthSupported,let probe=plain["probeId"] as? String,UUID(uuidString:probe) != nil else{throw RelayError.invalid};try acceptBattery(plain);lastContactAt=Date();status="Galaxy · 블루투스 통신 정상"
    if plain["kind"] as? String=="health_ping"{updateLocalBattery();try send(["kind":"health_pong","probeId":probe,"battery":batteryObject(),"network":networkSnapshot().rawValue])}
    else if health.acknowledge(probe,now:ProcessInfo.processInfo.systemUptime){healthDeadline?.cancel();healthDeadline=nil}
    return
   }
   lastContactAt=Date()
   if plain["kind"] as? String=="remote_state"{guard ready,remoteSupported else{return};do{try remote.accept(plain)}catch{remote.invalidResponse(plain["requestId"] as? String)};return}
   if plain["kind"] as? String=="device_status"{guard ready,peerBatterySupported,let raw=plain["battery"] as? [String:Any] else{throw RelayError.invalid};let battery=try JSONDecoder().decode(BatteryReading.self,from:NativeCrypto.json(raw));guard battery.valid else{throw RelayError.invalid};let saved=PeerBattery(pairId:pairId,battery:battery,reportedAt:time.int64Value);remoteBattery=saved;if let data=try? JSONEncoder().encode(saved){UserDefaults.standard.set(data,forKey:"peerBattery")}}else{message?(plain)}}
 }
 func send(_ value:[String:Any])throws{guard let key=session else{throw RelayError.unavailable};var body=value;body["messageId"]=NativeCrypto.uuid();body["timestamp"]=NativeCrypto.now();let packet=try NativeCrypto.seal(NativeCrypto.json(body),key:key,aad:NativeCrypto.wireAAD(pairId,"ios-to-android"));try enqueue(["iv":packet.iv,"ciphertext":packet.ciphertext])}
 func activate(_ config:PairSettings)throws{guard config.pairId==pairId else{throw RelayError.authentication};root=try NativeCrypto.bytes(config.linkKey);peerBatterySupported=config.capabilities?.contains("battery_v1")==true;healthSupported=config.capabilities?.contains("health_v1")==true;networkSupported=config.capabilities?.contains("network_v1")==true;remoteSupported=config.capabilities?.contains("remote_v1")==true;progress(.confirming,"서버 설정 전달 완료 · Galaxy의 최종 확인을 기다리는 중…");try send(["kind":"ready","capabilities":["battery_v1","health_v1","network_v1"]])}
 func confirmConnection(_ id:String)throws{guard enabled,session != nil,id==pairId,phase == .confirming else{throw RelayError.authentication};ready=true;lastContactAt=Date();health.reset(now:ProcessInfo.processInfo.systemUptime);disconnectLocation.connected();resumable=true;retryAttempt=0;knownIdentifier=peripheral?.identifier;if let id=knownIdentifier{UserDefaults.standard.set(pairId,forKey:"blePeerPairId");UserDefaults.standard.set(id.uuidString,forKey:"blePeerIdentifier")};progress(.connected,"Galaxy · 블루투스 연결됨");queueBattery();sendNetwork()}
 @objc private func batteryChanged(_ notification:Notification){updateLocalBattery();queueBattery()}
 private func updateLocalBattery(){let device=UIDevice.current,level=UIDevice.current.batteryLevel;let state:String;switch device.batteryState{case .charging:state="charging";case .full:state="full";case .unplugged:state="unplugged";default:state="unknown"};localBattery=BatteryReading(level:level<0 ? nil:Int((level*100).rounded()),state:state)}
 private func queueBattery(){
  batteryTask?.cancel();batteryTask=nil;guard ready,peerBatterySupported,let delay=batteryPolicy.delay(localBattery,now:ProcessInfo.processInfo.systemUptime) else{return}
  batteryTask=Task{[weak self] in
   if delay>0{do{try await Task.sleep(nanoseconds:UInt64(delay*1_000_000_000))}catch{return}}
   guard !Task.isCancelled,let self,self.ready,self.peerBatterySupported else{return}
   do{let raw=try JSONSerialization.jsonObject(with:JSONEncoder().encode(self.localBattery)) as! [String:Any];try self.send(["kind":"device_status","battery":raw]);self.batteryPolicy.sent(self.localBattery,now:ProcessInfo.processInfo.systemUptime)}catch{}
  }
 }
 private func enqueue(_ value:[String:Any])throws{let chunks=try FrameBuffer.pack(NativeCrypto.json(value));guard writes.count+chunks.count<=4096 else{throw RelayError.invalid};writes.append(contentsOf:chunks);drain()}
 private func drain(){guard !writing,!writes.isEmpty,let p=peripheral,let c=input else{return};writing=true;p.writeValue(writes.removeFirst(),for:c,type:.withResponse)}
 private func fail(_ text:String="보안 연결을 완료하지 못했습니다. 새 QR로 다시 연결해주세요. (B15)"){stop();progress(.failed,text)}
 func stop(){disconnectLocation.detached();remoteBattery=nil;cancelDeadline?.cancel();cancelDeadline=nil;deadline?.cancel();deadline=nil;phase = .idle;enabled=false;resumable=false;knownIdentifier=nil;cancelling=nil;retryAttempt=0;retry?.cancel();retry=nil;if manager.isScanning{manager.stopScan()};let previous=peripheral;peripheral=nil;previous?.delegate=nil;pairId="";service="";root=Data();resetSession();status="Galaxy 연결을 준비하세요";if let previous{manager.cancelPeripheralConnection(previous)}}
 #if targetEnvironment(simulator)
 /// UI fixture only; never establishes or persists a real connection.
 func previewConnection(){
  guard ProcessInfo.processInfo.arguments.contains("--design-preview") else{return}
  ready=true;phase = .connected;status="Galaxy · 블루투스 연결됨";lastContactAt=Date().addingTimeInterval(-60)
  remoteBattery=PeerBattery(pairId:"preview",battery:BatteryReading(level:78,state:"unplugged"),reportedAt:NativeCrypto.now()-180000);localBattery=BatteryReading(level:94,state:"charging")
  remoteSupported=true;remote.preview();for value in [-85,-84,-86,-85,-65,-64,-66,-65]{signal.record(value)};signalAt=Date()
  if ProcessInfo.processInfo.arguments.contains("--design-disconnected"){ready=false;phase = .connecting;status="블루투스 끊김 · 자동 재연결 중…";remote.disconnected();signal=SignalTrend();signalAt=nil}
  if ProcessInfo.processInfo.arguments.contains("--design-server") || ProcessInfo.processInfo.arguments.contains("--design-offline"){ready=false;phase = .idle;status="블루투스 연결이 끊어졌습니다.";remote.disconnected();signal=SignalTrend();signalAt=nil}
 }

 #endif
}
