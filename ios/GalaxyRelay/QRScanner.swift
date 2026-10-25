import SwiftUI
import AVFoundation
struct QRScanner:UIViewControllerRepresentable {
 let found:(String)->Void
 func makeUIViewController(context:Context)->ScanController{ScanController(found:found)}
 func updateUIViewController(_ controller:ScanController,context:Context){}
}
final class ScanController:UIViewController,AVCaptureMetadataOutputObjectsDelegate {
 private let session=AVCaptureSession();private let worker=DispatchQueue(label:"relay.qr.camera");private var preview:AVCaptureVideoPreviewLayer?;private var delivered=false;private var stopped=false
 private let found:(String)->Void
 init(found:@escaping(String)->Void){self.found=found;super.init(nibName:nil,bundle:nil)}
 required init?(coder:NSCoder){fatalError("Unsupported initialization")}
 override func viewDidLoad(){super.viewDidLoad();view.backgroundColor = .black;AVCaptureDevice.requestAccess(for:.video){[weak self] allowed in guard let self else{return};if allowed{self.worker.async{self.setup()}}else{DispatchQueue.main.async{let label=UILabel();label.text="설정에서 Relay 카메라 권한을 허용해주세요.";label.textColor = .white;label.numberOfLines=0;label.textAlignment = .center;label.frame=self.view.bounds;label.autoresizingMask=[.flexibleWidth,.flexibleHeight];self.view.addSubview(label)}}}}
 private func setup(){guard !stopped else{return};do{guard let device=AVCaptureDevice.default(for:.video) else{showMessage("카메라를 사용할 수 없습니다.");return};let input=try AVCaptureDeviceInput(device:device);guard session.canAddInput(input) else{showMessage("카메라를 시작할 수 없습니다.");return};session.addInput(input);let output=AVCaptureMetadataOutput();guard session.canAddOutput(output) else{showMessage("QR 인식을 시작할 수 없습니다.");return};session.addOutput(output);output.setMetadataObjectsDelegate(self,queue:.main);output.metadataObjectTypes=[.qr];DispatchQueue.main.async{let layer=AVCaptureVideoPreviewLayer(session:self.session);layer.videoGravity = .resizeAspectFill;layer.frame=self.view.bounds;self.view.layer.insertSublayer(layer,at:0);self.preview=layer;self.addHint()};session.startRunning()}catch{showMessage("카메라를 시작할 수 없습니다. 카메라 권한을 확인해주세요.")}}
 private func showMessage(_ text:String){DispatchQueue.main.async{let label=UILabel();label.text=text;label.textColor = .white;label.numberOfLines=0;label.textAlignment = .center;label.frame=self.view.bounds.insetBy(dx:24,dy:80);label.autoresizingMask=[.flexibleWidth,.flexibleHeight];self.view.addSubview(label)}}
 private func addHint(){let label=UILabel();label.text="Galaxy의 연결 QR을 화면 안에 맞춰주세요";label.textColor = .white;label.backgroundColor=UIColor.black.withAlphaComponent(0.6);label.textAlignment = .center;label.numberOfLines=0;label.frame=CGRect(x:20,y:view.bounds.height-140,width:view.bounds.width-40,height:60);label.autoresizingMask=[.flexibleTopMargin,.flexibleWidth];label.layer.cornerRadius=14;label.clipsToBounds=true;view.addSubview(label)}
 override func viewDidLayoutSubviews(){super.viewDidLayoutSubviews();preview?.frame=view.bounds}
 override func viewDidDisappear(_ animated:Bool){super.viewDidDisappear(animated);worker.async{self.stopped=true;self.session.stopRunning()}}
 func metadataOutput(_ output:AVCaptureMetadataOutput,didOutput metadata:[AVMetadataObject],from connection:AVCaptureConnection){guard !delivered,let text=(metadata.first as? AVMetadataMachineReadableCodeObject)?.stringValue else{return};delivered=true;UINotificationFeedbackGenerator().notificationOccurred(.success);worker.async{self.session.stopRunning()};found(text)}
}

