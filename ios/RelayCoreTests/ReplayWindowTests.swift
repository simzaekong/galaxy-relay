import XCTest
@testable import RelayCore
final class ReplayWindowTests:XCTestCase {
 func testLongLivedSessionPrunesOnlyExpiredIds(){var w=MessageReplayWindow();for i in 0..<10000{let now:Int64=1000000+Int64(i)*1000;XCTAssertEqual(w.record("id-\(i)",timestamp:now,now:now),.accepted)}}
 func testFutureTimestampReplayRemainsBlocked(){var w=MessageReplayWindow();XCTAssertEqual(w.record("future",timestamp:1120000,now:1000000),.accepted);XCTAssertEqual(w.record("future",timestamp:1120000,now:1120001),.replay);XCTAssertEqual(w.record("future",timestamp:1120000,now:1240001),.replay)}
 func testCapacityDoesNotEvictLiveReplayProtection(){var w=MessageReplayWindow();for i in 0..<4096{XCTAssertEqual(w.record("id-\(i)",timestamp:1000000,now:1000000),.accepted)};XCTAssertEqual(w.record("extra",timestamp:1000000,now:1000000),.capacity);XCTAssertEqual(w.record("id-0",timestamp:1000000,now:1000000),.replay)}
}
