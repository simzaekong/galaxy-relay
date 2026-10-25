import XCTest
import CoreBluetooth
@testable import GalaxyRelay
@MainActor final class DisconnectTests:XCTestCase {
 private func clean(){Secrets.remove("pair");Secrets.remove("pending-revocations")}
 private func fixture()->PairSettings{PairSettings(pairId:NativeCrypto.uuid(),serviceUUID:NativeCrypto.uuid(),galaxyName:"Fixture Galaxy",linkKey:NativeCrypto.b64(Data(repeating:1,count:32)),commandKey:NativeCrypto.b64(Data(repeating:2,count:32)),backend:"https://example.invalid",deviceId:NativeCrypto.uuid(),token:"fixture-only")}
 func testOfflineUnpairClearsLocalPairAndStopsSearching()async throws{
  clean();defer{clean()};let model=RelayModel(revokeRequest:{_ in throw URLError(.notConnectedToInternet)})
  let p=fixture();try Secrets.put("pair",JSONEncoder().encode(p));model.settings=p;model.ble.configure(p)
  await model.unpair()
  XCTAssertNil(model.settings);XCTAssertNil(Secrets.get("pair"));XCTAssertFalse(model.ble.ready)
  XCTAssertEqual(model.ble.status,"Galaxy 연결을 준비하세요")
  model.ble.connect();XCTAssertEqual(model.ble.status,"Galaxy 연결을 준비하세요")
  let pending=try JSONDecoder().decode([PairSettings].self,from:XCTUnwrap(Secrets.get("pending-revocations")))
  XCTAssertEqual(pending.map(\.deviceId),[p.deviceId])
  let qr=PairQR(version:2,pairId:NativeCrypto.uuid(),serviceUUID:NativeCrypto.uuid(),secret:NativeCrypto.b64(Data(repeating:3,count:32)),expires:NativeCrypto.now()+120000,name:"New Galaxy")
  XCTAssertNoThrow(try model.pair(qr));model.ble.stop()
 }
 func testStopResetsAnUnfinishedQRSession()async throws{
  let link=BLELink();let qr=PairQR(version:2,pairId:NativeCrypto.uuid(),serviceUUID:NativeCrypto.uuid(),secret:NativeCrypto.b64(Data(repeating:4,count:32)),expires:NativeCrypto.now()+120000,name:"Fixture")
  try link.pair(qr);link.stop();link.connect()
  XCTAssertEqual(link.status,"Galaxy 연결을 준비하세요");XCTAssertFalse(link.ready)
  XCTAssertThrowsError(try link.send(["kind":"command"]))
 }
 func testAlreadyRevokedCredentialDoesNotRestorePair()async throws{
  clean();defer{clean()};let model=RelayModel(revokeRequest:{_ in throw CloudFailure.http(401)})
  model.settings=fixture();await model.unpair();XCTAssertNil(model.settings)
  let pending=try JSONDecoder().decode([PairSettings].self,from:XCTUnwrap(Secrets.get("pending-revocations")))
  XCTAssertTrue(pending.isEmpty)
 }
 func testPairingTimeoutStopsSearchAndAllowsFreshQR()async throws{
  let link=BLELink();let qr=PairQR(version:2,pairId:NativeCrypto.uuid(),serviceUUID:NativeCrypto.uuid(),secret:NativeCrypto.b64(Data(repeating:5,count:32)),expires:NativeCrypto.now()+120000,name:"Fixture")
  try link.pair(qr);link.progress(.searching,"Searching fixture");link.expireConnectionAttempt()
  XCTAssertEqual(link.phase,.failed);XCTAssertFalse(link.ready);let failure=link.status
  link.connect();XCTAssertEqual(link.status,failure)
  XCTAssertNoThrow(try link.pair(qr));XCTAssertNotEqual(link.phase,.failed);link.stop()
 }
 func testCannotDeclareConnectedBeforeAuthenticatedGalaxyReceipt()async throws{
  let link=BLELink();XCTAssertThrowsError(try link.confirmConnection(NativeCrypto.uuid()))
  XCTAssertFalse(link.ready);XCTAssertEqual(link.phase,.idle)
 }
 func testPairedRecoveryDoesNotGiveUpAfterRepeatedTimeouts()async throws{
  let link=BLELink();link.configure(fixture());defer{link.stop()}
  for _ in 0..<12{link.progress(.searching,"out of range");link.expireConnectionAttempt();XCTAssertTrue(link.automaticReconnectEnabled);XCTAssertNotEqual(link.phase,.failed)}
  XCTAssertFalse(link.ready)
 }
 func testTransportRecoveryKeepsPairedIdentityButExplicitStopDisablesRetry()async throws{
  let link=BLELink();link.configure(fixture());link.recoverConnection("temporary transport failure")
  XCTAssertTrue(link.automaticReconnectEnabled);XCTAssertEqual(link.phase,.connecting)
  link.stop();link.recoverConnection("late callback");link.connect()
  XCTAssertFalse(link.automaticReconnectEnabled);XCTAssertEqual(link.phase,.idle);XCTAssertEqual(link.status,"Galaxy 연결을 준비하세요")
 }
 func testFreshQrDoesNotTurnTransportFailureIntoUntrustedReconnect()async throws{
  let link=BLELink();let qr=PairQR(version:2,pairId:NativeCrypto.uuid(),serviceUUID:NativeCrypto.uuid(),secret:NativeCrypto.b64(Data(repeating:8,count:32)),expires:NativeCrypto.now()+120000,name:"Fixture")
  try link.pair(qr);link.recoverConnection("first connection failure")
  XCTAssertFalse(link.automaticReconnectEnabled);XCTAssertEqual(link.phase,.failed);link.stop()
 }

 func testBatteryCacheIsBoundToItsPairedDevice()async throws{
  let key="peerBattery",previous=UserDefaults.standard.data(forKey:"peerBattery")
  defer{if let previous{UserDefaults.standard.set(previous,forKey:key)}else{UserDefaults.standard.removeObject(forKey:key)}}
  let first=fixture();let saved=PeerBattery(pairId:first.pairId,battery:BatteryReading(level:73,state:"charging"),reportedAt:NativeCrypto.now())
  UserDefaults.standard.set(try JSONEncoder().encode(saved),forKey:key)
  let link=BLELink();link.configure(first);XCTAssertEqual(link.remoteBattery?.battery.level,73)
  link.configure(fixture());XCTAssertNil(link.remoteBattery);link.stop()
 }

}
