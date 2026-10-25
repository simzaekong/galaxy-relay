import Foundation
import Combine
import CoreLocation
import UIKit
import MapKit

@MainActor final class DisconnectLocationRecorder:NSObject,ObservableObject,@preconcurrency CLLocationManagerDelegate {
 @Published private(set) var enabled=UserDefaults.standard.object(forKey:"disconnectLocationEnabled") as? Bool ?? true
 @Published private(set) var record:DisconnectFix?
 @Published private(set) var status="연결이 끊길 때만 이 iPhone의 위치를 기록합니다."
 @Published private(set) var authorization="위치 권한 설정"
 private var pairId="",policy=DisconnectLocationPolicy()
 private var task:Task<Void,Never>?,timeout:Task<Void,Never>?
 private var manager:CLLocationManager?,permissionManager:CLLocationManager?
 private var generation=UUID(),triggeredAt:Int64=0
 private var backgroundTask=UIBackgroundTaskIdentifier.invalid
 private let load:()->Data?;private let store:(Data)throws->Void;private let erase:()->Void
 init(load:@escaping()->Data?={Secrets.get("disconnect-fix")},store:@escaping(Data)throws->Void={try Secrets.put("disconnect-fix",$0)},erase:@escaping()->Void={Secrets.remove("disconnect-fix")}){self.load=load;self.store=store;self.erase=erase;super.init();refreshAuthorization()}
 func bind(_ id:String){cancel();policy.stopped();pairId=id;record=nil;if let data=load(),let value=try? JSONDecoder().decode(DisconnectFix.self,from:data),value.pairId==id,value.valid{record=value}}
 func connected(){cancel();policy.connected();status="연결 중 · 위치 측정 안 함"}
 func detached(){cancel();policy.stopped();pairId="";record=nil}
 func setEnabled(_ value:Bool){enabled=value;UserDefaults.standard.set(value,forKey:"disconnectLocationEnabled");if !value{cancel();status="위치 기록 꺼짐"}}
 func refreshAuthorization(){switch CLLocationManager.authorizationStatus(){case .authorizedAlways:authorization="항상 허용됨";case .authorizedWhenInUse:authorization="앱 사용 중 허용 · 백그라운드는 항상 허용 필요";case .denied,.restricted:authorization="위치 권한 꺼짐";default:authorization="위치 권한 설정"}}
 func requestPermission(){
  let value=CLLocationManager.authorizationStatus();let m=permissionManager ?? CLLocationManager();permissionManager=m;m.delegate=self
  if value == .notDetermined{m.requestWhenInUseAuthorization()}
  else if value == .authorizedWhenInUse{m.requestAlwaysAuthorization()}
  else if value != .authorizedAlways,let url=URL(string:UIApplication.openSettingsURLString){UIApplication.shared.open(url)}
  refreshAuthorization()
 }
 func connectionLost(){
  let auth=CLLocationManager.authorizationStatus(),permitted=auth == .authorizedAlways || auth == .authorizedWhenInUse && UIApplication.shared.applicationState == .active
  guard policy.lost(now:ProcessInfo.processInfo.systemUptime,allowed:enabled && permitted && !pairId.isEmpty) else{status = !enabled ? "위치 기록 꺼짐":!permitted ? "위치 권한을 허용하면 연결 해제 시 기록합니다.":"반복 단절 · 추가 측정 생략";return}
  cancel();triggeredAt=NativeCrypto.now();let ticket=generation;
  backgroundTask=UIApplication.shared.beginBackgroundTask(withName:"disconnect-location"){[weak self] in Task{@MainActor in guard let self,self.generation==ticket else{return};self.finish("백그라운드 실행 시간이 끝나 위치 기록을 중단했습니다.")}};status="연결 해제 · 위치 기록 준비 중"
  task=Task{[weak self] in do{try await Task.sleep(nanoseconds:3_000_000_000)}catch{return};guard !Task.isCancelled,let self,self.generation==ticket else{return};self.begin(ticket)}
 }
 private func begin(_ ticket:UUID){
  guard enabled,!pairId.isEmpty else{return};let auth=CLLocationManager.authorizationStatus()
  guard auth == .authorizedAlways || auth == .authorizedWhenInUse && UIApplication.shared.applicationState == .active else{finish("위치 권한이 없어 기록하지 못했습니다.");return}
  let m=CLLocationManager();manager=m;m.delegate=self;m.desiredAccuracy=kCLLocationAccuracyHundredMeters;m.allowsBackgroundLocationUpdates=auth == .authorizedAlways
  status="연결 해제 · 위치 1회 확인 중";m.requestLocation()
  timeout=Task{[weak self] in do{try await Task.sleep(nanoseconds:15_000_000_000)}catch{return};guard !Task.isCancelled,let self,self.generation==ticket else{return};self.finish("제한 시간 안에 위치를 확인하지 못했습니다.")}
 }
 func locationManagerDidChangeAuthorization(_ manager:CLLocationManager){refreshAuthorization();if CLLocationManager.authorizationStatus() == .denied || CLLocationManager.authorizationStatus() == .restricted{cancel()}}
 func locationManager(_ manager:CLLocationManager,didUpdateLocations locations:[CLLocation]){
  guard manager === self.manager,let point=locations.last else{return}
  let value=DisconnectFix(pairId:pairId,latitude:point.coordinate.latitude,longitude:point.coordinate.longitude,accuracy:point.horizontalAccuracy,triggeredAt:triggeredAt,measuredAt:Int64(point.timestamp.timeIntervalSince1970*1000))
  guard value.acceptable(now:NativeCrypto.now()) else{finish("현재 시점의 위치를 확인하지 못했습니다.");return}
  do{try store(JSONEncoder().encode(value));record=value;finish("이 iPhone의 연결 해제 위치를 기록했습니다.")}catch{finish("위치 기록을 저장하지 못했습니다.")}
 }
 func locationManager(_ manager:CLLocationManager,didFailWithError error:Error){guard manager === self.manager else{return};finish("위치 서비스를 사용할 수 없어 기록하지 못했습니다.")}
 func foregroundChanged(_ active:Bool){refreshAuthorization();if !active && CLLocationManager.authorizationStatus() != .authorizedAlways{cancel()}}
 private func finish(_ message:String){cancel();status=message}
 private func cancel(){generation=UUID();task?.cancel();task=nil;timeout?.cancel();timeout=nil;manager?.stopUpdatingLocation();manager?.allowsBackgroundLocationUpdates=false;manager?.delegate=nil;manager=nil;if backgroundTask != .invalid{UIApplication.shared.endBackgroundTask(backgroundTask);backgroundTask = .invalid}}
 func clear(){cancel();record=nil;erase();status="위치 기록을 삭제했습니다."}
 func openMap(){guard let record,record.valid else{return};let item=MKMapItem(placemark:MKPlacemark(coordinate:CLLocationCoordinate2D(latitude:record.latitude,longitude:record.longitude)));item.name="이 iPhone의 연결 해제 위치";item.openInMaps()}
}
