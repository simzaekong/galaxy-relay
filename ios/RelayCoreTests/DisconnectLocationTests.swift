import XCTest
@testable import RelayCore
final class DisconnectLocationTests:XCTestCase {
 func testOnlyConfirmedLossArmsOneAttempt(){var p=DisconnectLocationPolicy();XCTAssertFalse(p.lost(now:0,allowed:true));p.connected();XCTAssertTrue(p.lost(now:0,allowed:true));XCTAssertFalse(p.lost(now:400,allowed:true))}
 func testRapidFlapsAndClockRollbackDoNotRequestAgain(){var p=DisconnectLocationPolicy();p.connected();XCTAssertTrue(p.lost(now:100,allowed:true));p.connected();XCTAssertFalse(p.lost(now:101,allowed:true));p.connected();XCTAssertFalse(p.lost(now:1,allowed:true));p.connected();XCTAssertTrue(p.lost(now:400,allowed:true))}
 func testDeniedOrDisabledDoesNotDeferAnAttempt(){var p=DisconnectLocationPolicy();p.connected();XCTAssertFalse(p.lost(now:0,allowed:false));XCTAssertFalse(p.lost(now:100,allowed:true));p.connected();p.stopped();XCTAssertFalse(p.lost(now:400,allowed:true))}
 func testFixRejectsStaleFutureInaccurateAndInvalidCoordinates(){
  func fix(_ lat:Double=37,_ lon:Double=127,_ accuracy:Double=100,_ measured:Int64=100000)->DisconnectFix{DisconnectFix(pairId:"fixture",latitude:lat,longitude:lon,accuracy:accuracy,triggeredAt:100000,measuredAt:measured)}
  XCTAssertTrue(fix().acceptable(now:101000));XCTAssertTrue(fix(37,127,100,70000).acceptable(now:101000))
  XCTAssertFalse(fix(91).valid);XCTAssertFalse(fix(37,181).valid);XCTAssertFalse(fix(.nan).valid);XCTAssertFalse(fix(37,127,-1).valid);XCTAssertFalse(fix(37,127,10001).valid)
  XCTAssertFalse(fix(37,127,100,69999).valid);XCTAssertFalse(fix(37,127,100,107000).acceptable(now:101000));XCTAssertFalse(fix().acceptable(now:121000));XCTAssertFalse(fix().acceptable(now:99000))
 }
 func testStoredFixRoundTripRetainsTriggerAndMeasurementTimes()throws{let value=DisconnectFix(pairId:"fixture",latitude:37,longitude:127,accuracy:100,triggeredAt:100000,measuredAt:100500);let restored=try JSONDecoder().decode(DisconnectFix.self,from:JSONEncoder().encode(value));XCTAssertEqual(restored.pairId,"fixture");XCTAssertEqual(restored.triggeredAt,100000);XCTAssertEqual(restored.measuredAt,100500);XCTAssertTrue(restored.valid)}
}
