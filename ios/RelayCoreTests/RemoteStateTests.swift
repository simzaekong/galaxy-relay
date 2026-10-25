import XCTest
@testable import RelayCore
final class RemoteStateTests:XCTestCase {
 func testSnapshotCannotOverwriteInFlightButtonChange(){
  var t=RemoteTransaction();var state=RemoteState();state.mediaVolume=40;XCTAssertTrue(t.refresh(state))
  XCTAssertFalse(t.refresh(state));_ = t.begin(id:"command",action:"mediaVolume",value:80)
  state.mediaVolume=20;XCTAssertFalse(t.refresh(state));XCTAssertEqual(t.displayed.mediaVolume,80);XCTAssertEqual(t.pending?.id,"command")
 }
 func testOptimisticPlaybackAndFailedReceiptRestoreButton(){
  var t=RemoteTransaction();XCTAssertTrue(t.begin(id:"load",action:"state"));var state=RemoteState();state.mediaId="music";state.canPlay=true
  XCTAssertEqual(t.resolve(id:"load",state:state,result:"OK"),true)
  XCTAssertTrue(t.begin(id:"play",action:"play"));XCTAssertTrue(t.displayed.playing)
  XCTAssertEqual(t.resolve(id:"play",state:state,result:"OK"),false);XCTAssertFalse(t.displayed.playing)
 }
 func testVerifiedStateKeepsButtonAndIgnoresLateReceipt(){
  var t=RemoteTransaction();XCTAssertTrue(t.begin(id:"one",action:"findStart"));var state=RemoteState();state.finding=true
  XCTAssertNil(t.resolve(id:"old",state:RemoteState(),result:"OK"));XCTAssertTrue(t.displayed.finding)
  XCTAssertEqual(t.resolve(id:"one",state:state,result:"OK"),true);XCTAssertTrue(t.displayed.finding)
  XCTAssertTrue(t.begin(id:"two",action:"findStop"));XCTAssertFalse(t.displayed.finding);t.cancel();XCTAssertTrue(t.displayed.finding)
 }
 func testCannotOverlapOrAcceptInvalidState(){
  var t=RemoteTransaction();XCTAssertTrue(t.begin(id:"one",action:"ringerMode",value:0));XCTAssertEqual(t.displayed.ringerMode,0)
  XCTAssertFalse(t.begin(id:"two",action:"ringerMode",value:1));var invalid=RemoteState();invalid.ringVolume=200
  XCTAssertNil(t.resolve(id:"one",state:invalid,result:"OK"));t.cancel();XCTAssertEqual(t.displayed.ringerMode,2)
 }
 func testVolumeUsesGalaxyHardwareSteps(){
  var state=RemoteState();state.mediaVolumeMax=15;state.mediaVolume=53
  XCTAssertTrue(state.confirms("mediaVolume",value:50,previous:RemoteState()))
  state.mediaVolume=47;XCTAssertFalse(state.confirms("mediaVolume",value:50,previous:RemoteState()))
 }
 func testSignalFiltersInvalidValuesAndShowsTrend(){
  var s=SignalTrend();s.record(127);s.record(0);XCTAssertNil(s.rssi)
  for value in [-85,-84,-86,-85,-65,-64,-66,-65]{s.record(value)}
  XCTAssertEqual(s.text,"신호가 강해지고 있어요");XCTAssertEqual(s.rssi,-65)
  for _ in 0..<4{s.record(-90)};XCTAssertEqual(s.text,"신호가 약해지고 있어요")
 }
 func testDisconnectDiscardsOptimisticState(){var t=RemoteTransaction();_ = t.begin(id:"one",action:"findStart");t.reset();XCTAssertNil(t.pending);XCTAssertFalse(t.displayed.finding)}
}
