import XCTest
@testable import RelayCore

final class ConnectionStatusTests:XCTestCase {
 func testConfirmedTransportTakesPriorityOverReconnectAttempts(){
  XCTAssertEqual(RelayConnectionStatus.resolve(bluetoothConnected:true,serverConnected:true,attempting:true),.bluetooth)
  XCTAssertEqual(RelayConnectionStatus.resolve(bluetoothConnected:false,serverConnected:true,attempting:true),.server)
  XCTAssertEqual(RelayConnectionStatus.resolve(bluetoothConnected:false,serverConnected:false,attempting:true),.connecting)
  XCTAssertEqual(RelayConnectionStatus.resolve(bluetoothConnected:false,serverConnected:false,attempting:false),.disconnected)
 }
 func testStartingOrFinishingARequestDoesNotProveGalaxyIsReachable(){
  var state=ServerConnectionState();let now=Date()
  state.begin();XCTAssertTrue(state.attempting);XCTAssertFalse(state.isVerified(at:now))
  state.finish();XCTAssertFalse(state.attempting);XCTAssertFalse(state.isVerified(at:now))
 }
 func testRefreshKeepsConfirmedStateUntilItsLeaseExpires(){
  var state=ServerConnectionState();let now=Date();state.confirm(at:now);state.begin()
  XCTAssertTrue(state.isVerified(at:now.addingTimeInterval(60)));state.finish()
  XCTAssertTrue(state.isVerified(at:now.addingTimeInterval(119)))
  XCTAssertFalse(state.isVerified(at:now.addingTimeInterval(120)))
  XCTAssertFalse(state.isVerified(at:now.addingTimeInterval(-1)))
 }
 func testFailureImmediatelyInvalidatesTheLastConfirmation(){
  var state=ServerConnectionState();let now=Date();state.confirm(at:now);state.begin();state.fail()
  XCTAssertFalse(state.attempting);XCTAssertNil(state.verifiedAt);XCTAssertFalse(state.isVerified(at:now))
 }
}
