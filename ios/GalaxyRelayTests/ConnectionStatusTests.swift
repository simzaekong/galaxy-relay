import XCTest
@testable import GalaxyRelay

@MainActor final class ConnectionStatusTests:XCTestCase {
 private func fixture()->PairSettings{PairSettings(pairId:NativeCrypto.uuid(),serviceUUID:NativeCrypto.uuid(),galaxyName:"Fixture",linkKey:NativeCrypto.b64(Data(repeating:1,count:32)),commandKey:NativeCrypto.b64(Data(repeating:2,count:32)),backend:"https://example.invalid",deviceId:NativeCrypto.uuid(),token:"fixture-only")}
 private func packet(_ p:PairSettings,_ id:String)throws->[String:Any]{
  let data=try NativeCrypto.json(["requestId":id,"events":[]])
  let sealed=try NativeCrypto.seal(data,key:NativeCrypto.bytes(p.linkKey),aad:NativeCrypto.cloudAAD(id,p.deviceId))
  return ["ready":true,"packet":try NativeCrypto.object(JSONEncoder().encode(sealed))]
 }
 private func prepare(_ model:RelayModel,_ p:PairSettings){model.ble.stop();model.settings=p;model.foreground=true}
 func testOnlyAnAuthenticatedGalaxyReplyAndAcknowledgementConfirmServerConnection()async throws{
  let p=fixture(),id=NativeCrypto.uuid("req_");let reply=try packet(p,id);var failNext=false;var model:RelayModel!
  model=RelayModel(syncRequest:{_,path,_ in
   if path.hasSuffix("start"){
    XCTAssertEqual(model.connectionStatus(),failNext ? .server:.connecting)
    if failNext{throw URLError(.notConnectedToInternet)}
    return ["requestId":id,"expires":NativeCrypto.now()+120000,"grant":"fixture"]
   }
   if path.hasSuffix("result"){XCTAssertEqual(model.connectionStatus(),.connecting);return reply}
   XCTAssertEqual(model.connectionStatus(),.connecting);return [:]
  });prepare(model,p);defer{model.foreground=false;model.settings=nil;model=nil}
  await model.sync();XCTAssertEqual(model.connectionStatus(),.server)
  failNext=true;await model.sync();XCTAssertEqual(model.connectionStatus(),.disconnected)
 }
 func testServerAcceptanceWithoutAnAuthenticatedPeerPacketNeverShowsConnected()async throws{
  let p=fixture(),id=NativeCrypto.uuid("req_");let wrongPair=fixture();let reply=try packet(wrongPair,id)
  let model=RelayModel(syncRequest:{_,path,_ in
   if path.hasSuffix("start"){return ["requestId":id]}
   if path.hasSuffix("result"){return reply}
   XCTFail("Invalid Galaxy response must not be acknowledged");return [:]
  });prepare(model,p);defer{model.foreground=false;model.settings=nil}
  await model.sync();XCTAssertEqual(model.connectionStatus(),.disconnected);XCTAssertNil(model.serverConnection.verifiedAt)
 }
 func testBackgroundingDuringAcknowledgementCannotRestoreAStaleServerState()async throws{
  let p=fixture(),id=NativeCrypto.uuid("req_");let reply=try packet(p,id);var model:RelayModel!
  model=RelayModel(syncRequest:{_,path,_ in
   if path.hasSuffix("start"){return ["requestId":id]}
   if path.hasSuffix("result"){return reply}
   model.foreground=false;return [:]
  });prepare(model,p);defer{model.settings=nil;model=nil}
  await model.sync();XCTAssertEqual(model.connectionStatus(),.disconnected);XCTAssertNil(model.serverConnection.verifiedAt)
 }
 func testOfflinePathClearsTheConfirmedServerState()async throws{
  let p=fixture(),id=NativeCrypto.uuid("req_");let reply=try packet(p,id)
  let model=RelayModel(syncRequest:{_,path,_ in path.hasSuffix("start") ? ["requestId":id]:path.hasSuffix("result") ? reply:[:]})
  prepare(model,p);defer{model.foreground=false;model.settings=nil}
  await model.sync();XCTAssertEqual(model.connectionStatus(),.server)
  model.networkChanged(.offline);XCTAssertEqual(model.connectionStatus(),.disconnected)
 }
}
