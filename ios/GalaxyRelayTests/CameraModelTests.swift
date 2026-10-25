import XCTest
@testable import GalaxyRelay
@MainActor final class CameraModelTests:XCTestCase {
 private func fixture()->PairSettings{PairSettings(pairId:NativeCrypto.uuid(),serviceUUID:NativeCrypto.uuid(),galaxyName:"Fixture",linkKey:NativeCrypto.b64(Data(repeating:1,count:32)),commandKey:NativeCrypto.b64(Data(repeating:2,count:32)),backend:"https://example.invalid",deviceId:NativeCrypto.uuid(),token:"fixture-only")}
 private func grant(_ p:PairSettings)->[String:Any]{["sessionId":"evt_"+NativeCrypto.uuid(),"nativeId":p.deviceId,"role":"viewer","expires":NativeCrypto.now()+600000]}
 func testCloseCancelsExactlyOnceAndDiscardsCredentials()async{
  let p=fixture(),g=grant(p);var ended=0
  let model=CameraModel(start:{_ in g},end:{_,_ in ended+=1});await model.start(p);XCTAssertNotNil(model.config)
  model.close();model.close();await Task.yield();XCTAssertTrue(model.ended);XCTAssertNil(model.config);XCTAssertEqual(ended,1)
 }
 func testCloseDuringRequestCancelsLateGrantWithoutOpeningCamera()async{
  let p=fixture(),g=grant(p);var continuation:CheckedContinuation<[String:Any],Error>?;var ended=0
  let model=CameraModel(start:{_ in try await withCheckedThrowingContinuation{continuation=$0}},end:{_,_ in ended+=1})
  let pending=Task{await model.start(p)};await Task.yield();model.close();continuation?.resume(returning:g);await pending.value
  XCTAssertTrue(model.ended);XCTAssertNil(model.config);XCTAssertEqual(ended,1)
 }
 func testServerFailureDoesNotOpenAReceiveView()async{
  let model=CameraModel(start:{_ in throw URLError(.notConnectedToInternet)},end:{_,_ in XCTFail("No grant to cancel")});await model.start(fixture());XCTAssertNil(model.config);XCTAssertFalse(model.status.isEmpty)
 }
 func testRemoteEndCannotBeReversedByLateStatus()async{
  let p=fixture(),g=grant(p);let model=CameraModel(start:{_ in g},end:{_,_ in});await model.start(p)
  model.event(["type":"closed","text":"ended"]);model.event(["type":"connected","text":"late"])
  XCTAssertTrue(model.ended);XCTAssertNil(model.config);XCTAssertEqual(model.status,"ended")
 }
}
