import XCTest
import UIKit
@testable import GalaxyRelay

@MainActor final class AppearanceTests:XCTestCase {
 private func event(_ extra:[String:Any]=[:])throws->RelayEvent{
  var value:[String:Any]=["id":NativeCrypto.uuid("evt_"),"type":"notification","timestamp":NativeCrypto.now(),"app":"카카오톡","title":"테스트 대화","body":"메시지","packageName":"com.kakao.talk","notificationKey":"fixture-key"]
  value.merge(extra){_,new in new};return try JSONDecoder().decode(RelayEvent.self,from:NativeCrypto.json(value))
 }
 func testExistingHistoryWithoutIconsStillDecodes()throws{
  let old=try event();XCTAssertNil(old.appIcon);XCTAssertEqual(old.heading,"테스트 대화")
  XCTAssertNil(AppIconDecoder.image(nil));XCTAssertNil(AppIconDecoder.image("invalid-image"))
 }
 func testIconDecodeBoundsAndRoundTrip()throws{
  func image(_ size:CGFloat)->Data{let format=UIGraphicsImageRendererFormat();format.scale=1;return UIGraphicsImageRenderer(size:CGSize(width:size,height:size),format:format).jpegData(withCompressionQuality:0.5){context in UIColor.systemBlue.setFill();context.fill(CGRect(x:0,y:0,width:size,height:size))}}
  let data=image(32);XCTAssertLessThanOrEqual(data.count,1536)
  let encoded=NativeCrypto.b64(data),record=try event(["appIcon":encoded])
  XCTAssertNotNil(AppIconDecoder.image(record.appIcon))
  XCTAssertEqual(try JSONDecoder().decode(RelayEvent.self,from:JSONEncoder().encode(record)).appIcon,encoded)
  XCTAssertNil(AppIconDecoder.image(NativeCrypto.b64(image(65))));XCTAssertNil(AppIconDecoder.image(String(repeating:"a",count:2049)))
 }
 func testIconHistorySurvivesReloadBeyondNetworkPacketLimit()throws{
  let history=History();try history.deleteLocal(Set(history.state.events.map{$0.id}));defer{try? history.clear()}
  let icon=String(repeating:"A",count:2000)
  for index in 0..<24{_ = try history.apply(event(["appIcon":icon,"notificationKey":"fixture-\(index)"]))}
  XCTAssertEqual(History().state.events.count,24)
  let key=Data(repeating:7,count:32),large=Data(repeating:0,count:40000)
  let packet=try NativeCrypto.seal(large,key:key,aad:"fixture")
  XCTAssertThrowsError(try NativeCrypto.open(packet,key:key,aad:"fixture"))
  XCTAssertEqual(try NativeCrypto.open(packet,key:key,aad:"fixture",maximumCiphertext:48000),large)
 }
 func testViewingPreservesHistoryAndRemovalStillSynchronizes()throws{
  let history=History();try history.deleteLocal(Set(history.state.events.map{$0.id}));defer{try? history.clear()}
  let notification=try event();XCTAssertTrue(try history.apply(notification));history.read(notification.id)
  XCTAssertEqual(history.state.events.count,1);XCTAssertEqual(history.state.events.first?.read,true)
  let removal=try event(["id":NativeCrypto.uuid("evt_"),"type":"removed","targetEventId":notification.id])
  XCTAssertFalse(try history.apply(removal));XCTAssertTrue(history.state.events.isEmpty)
 }
 func testUnansweredTerminationCanDisplayWithoutRinging()throws{let e=try event(["type":"call","state":"ended","callId":NativeCrypto.uuid("evt_"),"unanswered":true]);XCTAssertEqual(e.unanswered,true);XCTAssertTrue(e.message.contains("받지 않은"));let history=History();try history.deleteLocal(Set(history.state.events.map{$0.id}));XCTAssertTrue(try history.apply(e));XCTAssertFalse(try history.apply(e));try history.deleteLocal(Set(history.state.events.map{$0.id}))}
}
