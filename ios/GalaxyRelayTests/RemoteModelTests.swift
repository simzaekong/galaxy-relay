import XCTest
import Combine
@testable import GalaxyRelay

@MainActor final class RemoteModelTests:XCTestCase {
 private final class Requests {
  var items:[[String:Any]]=[]
  func send(_ body:[String:Any]){items.append(body)}
  var last:[String:Any]{items.last!}
  var id:String{last["requestId"] as! String}
 }
 private func fixture()->RemoteState{var state=RemoteState();state.mediaAvailable=true;state.mediaId="music";state.canPlay=true;state.canPause=true;return state}
 private func response(_ request:[String:Any],_ state:RemoteState,result:String="OK")throws->[String:Any]{["requestId":request["requestId"]!,"result":result,"state":try NativeCrypto.object(JSONEncoder().encode(state))]}
 private func load(_ model:RemoteModel,_ requests:Requests)throws{model.perform("state",send:requests.send);try model.accept(response(requests.last,fixture()));XCTAssertTrue(model.loaded)}

 func testRepeatedAutomaticReadsNeverLockControlsOrRepublishUnchangedState()throws{
  let model=RemoteModel(),requests=Requests();defer{model.disconnected()};try load(model,requests)
  var changes=0;let subscription=model.objectWillChange.sink{changes+=1};defer{subscription.cancel()}
  for _ in 0..<4{
   model.perform("state",send:requests.send)
   XCTAssertFalse(model.busy);XCTAssertNil(model.transaction.pending);XCTAssertTrue(model.loaded)
   try model.accept(response(requests.last,fixture()))
  }
  XCTAssertEqual(changes,0)
 }
 func testTapDuringReadTakesPriorityAndLateSnapshotCannotUndoOptimisticButton()throws{
  let model=RemoteModel(),requests=Requests();defer{model.disconnected()};try load(model,requests)
  model.perform("state",send:requests.send);let poll=requests.last
  model.perform("play",send:requests.send);let command=requests.last
  XCTAssertEqual(command["action"] as? String,"play");XCTAssertTrue(model.busy);XCTAssertTrue(model.state.playing)
  try model.accept(response(poll,fixture()));XCTAssertTrue(model.busy);XCTAssertTrue(model.state.playing)
  var playing=fixture();playing.playing=true;try model.accept(response(command,playing))
  XCTAssertFalse(model.busy);XCTAssertTrue(model.state.playing)
 }
 func testReadIsSkippedUntilUserCommandCompletes()throws{
  let model=RemoteModel(),requests=Requests();defer{model.disconnected()};try load(model,requests)
  model.perform("play",send:requests.send);let count=requests.items.count
  model.perform("state",send:requests.send);XCTAssertEqual(requests.items.count,count);XCTAssertTrue(model.state.playing)
 }
 func testReadTimeoutKeepsLoadedControlsAndLastKnownState()throws{
  let model=RemoteModel(),requests=Requests();defer{model.disconnected()};try load(model,requests)
  model.perform("state",send:requests.send);let id=requests.id
  model.expireRefresh(id)
  XCTAssertTrue(model.loaded);XCTAssertFalse(model.busy);XCTAssertEqual(model.state,fixture());XCTAssertTrue(model.notice.isEmpty)
  model.perform("play",send:requests.send);XCTAssertTrue(model.state.playing)
 }
 func testMalformedObsoleteReadDoesNotCancelNewCommand()throws{
  let model=RemoteModel(),requests=Requests();defer{model.disconnected()};try load(model,requests)
  model.perform("state",send:requests.send);let old=requests.id
  model.perform("play",send:requests.send);model.invalidResponse(old)
  XCTAssertTrue(model.busy);XCTAssertTrue(model.state.playing)
 }
 func testCommandTimeoutRestoresButtonAndResyncDoesNotLockControls()throws{
  let model=RemoteModel(),requests=Requests();defer{model.disconnected()};try load(model,requests)
  model.perform("play",send:requests.send);let command=requests.last
  model.expireCommand(requests.id,send:requests.send)
  XCTAssertFalse(model.busy);XCTAssertFalse(model.state.playing);XCTAssertEqual(requests.last["action"] as? String,"state")
  var playing=fixture();playing.playing=true;try model.accept(response(command,playing));XCTAssertFalse(model.state.playing)
  try model.accept(response(requests.last,playing));XCTAssertTrue(model.state.playing);XCTAssertFalse(model.busy)
 }
 func testRejectedCommandRestoresButton()throws{
  let model=RemoteModel(),requests=Requests();defer{model.disconnected()};try load(model,requests)
  model.perform("play",send:requests.send);XCTAssertTrue(model.state.playing)
  try model.accept(response(requests.last,fixture(),result:"UNAVAILABLE"));XCTAssertFalse(model.state.playing);XCTAssertFalse(model.busy)
 }
 func testChangedSnapshotRefreshesWithoutCommandBusyState()throws{
  let model=RemoteModel(),requests=Requests();defer{model.disconnected()};try load(model,requests)
  model.perform("state",send:requests.send);var state=fixture();state.mediaVolume=80
  try model.accept(response(requests.last,state));XCTAssertEqual(model.state.mediaVolume,80);XCTAssertFalse(model.busy)
 }
 func testDisconnectDiscardsOutstandingRead()throws{
  let model=RemoteModel(),requests=Requests();try load(model,requests)
  model.perform("state",send:requests.send);let poll=requests.last;model.disconnected()
  try model.accept(response(poll,fixture()));XCTAssertFalse(model.loaded);XCTAssertFalse(model.busy)
 }
}
