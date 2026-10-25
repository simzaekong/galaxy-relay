import XCTest
@testable import RelayCore
final class BatteryStatusTests:XCTestCase {
 private let a=BatteryReading(level:80,state:"unplugged")
 func testUnchangedValueDoesNotCreateHeartbeat(){var p=BatterySyncPolicy();XCTAssertEqual(p.delay(a,now:0),0);p.sent(a,now:0);XCTAssertNil(p.delay(a,now:86400))}
 func testLevelChangesCoalesceUntilFiveMinutes(){var p=BatterySyncPolicy();p.sent(a,now:1);XCTAssertEqual(p.delay(BatteryReading(level:79,state:"unplugged"),now:11),290);XCTAssertNil(p.delay(a,now:12));XCTAssertEqual(p.delay(BatteryReading(level:75,state:"unplugged"),now:301),0)}
 func testChargingChangesHaveOneMinuteMinimum(){var p=BatterySyncPolicy();p.sent(a,now:1);let charging=BatteryReading(level:80,state:"charging");XCTAssertEqual(p.delay(charging,now:11),50);XCTAssertEqual(p.delay(charging,now:61),0)}
 func testNewConnectionSendsImmediatelyAndClockRollbackDoesNotFlood(){var p=BatterySyncPolicy();p.sent(a,now:100);XCTAssertEqual(p.delay(BatteryReading(level:79,state:"unplugged"),now:0),300);p=BatterySyncPolicy();XCTAssertEqual(p.delay(a,now:101),0)}
 func testUntrustedBatteryValuesAreRejected(){XCTAssertFalse(BatteryReading(level:101,state:"charging").valid);XCTAssertFalse(BatteryReading(level:-1,state:"charging").valid);XCTAssertFalse(BatteryReading(level:50,state:"invalid").valid);XCTAssertTrue(BatteryReading(level:nil,state:"unknown").valid)}
 func testPeerBatteryRoundTripPreservesPairAndObservationTime()throws{let value=PeerBattery(pairId:"fixture-pair",battery:a,reportedAt:1234);let restored=try JSONDecoder().decode(PeerBattery.self,from:JSONEncoder().encode(value));XCTAssertEqual(restored.pairId,value.pairId);XCTAssertEqual(restored.battery,a);XCTAssertEqual(restored.reportedAt,1234)}
 func testPreviousPairSettingsWithoutBatteryCapabilityStillDecode()throws{var fields:[String:Any]=["pairId":"fixture-pair","serviceUUID":"fixture-service","galaxyName":"Galaxy","linkKey":"fixture-link","commandKey":"fixture-command","backend":"https://example.invalid","deviceId":"fixture-device","token":"fixture-only"];let old=try JSONDecoder().decode(PairSettings.self,from:JSONSerialization.data(withJSONObject:fields));XCTAssertNil(old.capabilities);fields["capabilities"]=["battery_v1"];let updated=try JSONDecoder().decode(PairSettings.self,from:JSONSerialization.data(withJSONObject:fields));XCTAssertEqual(updated.capabilities,["battery_v1"])}
}
