package kr.infosphere.galaxyrelay;
import org.junit.Test;
import static org.junit.Assert.*;
public class PairingSessionTest {
 @Test public void transportConnectionAloneDoesNotCloseQr(){PairingSession s=new PairingSession();long ticket=s.begin("new-qr");assertTrue(s.current(ticket));assertFalse(s.authenticated("new-qr"));}
 @Test public void latePreviousSessionCannotAuthenticateTheNewQr(){PairingSession s=new PairingSession();long old=s.begin("old-qr");s.invalidate();long current=s.begin("new-qr");assertFalse(s.current(old));assertFalse(s.authenticate(old,"new-qr"));assertFalse(s.authenticated("new-qr"));assertTrue(s.authenticate(current,"new-qr"));assertTrue(s.authenticated("new-qr"));}
 @Test public void wrongQrAndDisconnectDoNotRemainAuthenticated(){PairingSession s=new PairingSession();long ticket=s.begin("qr");assertFalse(s.authenticate(ticket,"other-qr"));assertTrue(s.authenticate(ticket,"qr"));s.clearAuthentication();assertFalse(s.authenticated("qr"));s.invalidate();assertFalse(s.authenticate(ticket,"qr"));}
}
