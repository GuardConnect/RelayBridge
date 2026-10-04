package ru.eyeone.relaybridge;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
public class SmtpClientTest {
    private static SSLContext context() throws Exception {
        KeyStore keys=KeyStore.getInstance("PKCS12");try(InputStream in=SmtpClientTest.class.getResourceAsStream("/smtp-fixture.p12")){keys.load(in,"fixture-only".toCharArray());}
        KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(keys,"fixture-only".toCharArray());
        TrustManagerFactory tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(keys);
        SSLContext ctx=SSLContext.getInstance("TLS");ctx.init(km.getKeyManagers(),tm.getTrustManagers(),null);return ctx;
    }
    interface Script {void run(Peer p) throws Exception;}
    static final class Peer {
        Socket socket;BufferedReader in;OutputStream out;
        Peer(Socket s)throws Exception{socket=s;reset();}
        void reset()throws Exception{socket.setSoTimeout(8000);in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));out=socket.getOutputStream();}
        String read()throws Exception{String value=in.readLine();if(value==null)throw new EOFException();return value;}
        void reply(String s)throws Exception{out.write((s+"\r\n").getBytes(StandardCharsets.US_ASCII));out.flush();}
        void ehlo(String caps)throws Exception{assertEquals("EHLO relaybridge.local",read());reply("250-localhost\r\n"+caps);}
        void envelope()throws Exception{assertEquals("MAIL FROM:<sender@example.com>",read());reply("250 2.1.0 OK");assertEquals("RCPT TO:<recipient@example.com>",read());reply("250 2.1.5 OK");}
        String data()throws Exception{assertEquals("DATA",read());reply("354 Continue");StringBuilder content=new StringBuilder();String line;while(!(line=read()).equals("."))content.append(line).append("\r\n");return content.toString();}
        void finish()throws Exception{envelope();String mime=data();assertTrue(mime.contains("Content-Transfer-Encoding: base64"));String encoded=mime.substring(mime.indexOf("\r\n\r\n")+4);assertEquals("Привет\n.Line\n",new String(Base64.getMimeDecoder().decode(encoded),StandardCharsets.UTF_8));reply("250 2.0.0 queued");assertEquals("QUIT",read());reply("221 Bye");}
    }
    private void exercise(String tls,String auth,Script script,boolean error,String expected,boolean badHost)throws Exception {
        exercise(tls,auth,script,error,expected,badHost,"private-user@example.com","secret-fixture-token");
    }
    private void exercise(String tls,String auth,Script script,boolean error,String expected,boolean badHost,String login,String password)throws Exception {
        SSLContext ssl=context();ServerSocket listener=tls.equals("SSL")?ssl.getServerSocketFactory().createServerSocket(0,1,InetAddress.getLoopbackAddress()):new ServerSocket(0,1,InetAddress.getLoopbackAddress());listener.setSoTimeout(10000);
        ExecutorService pool=Executors.newSingleThreadExecutor();Future<?> task=pool.submit(()->{try(Socket s=listener.accept()){script.run(new Peer(s));}catch(Exception e){throw new RuntimeException(e);}});
        Config c=new Config();c.host=badHost?"127.0.0.1":"localhost";c.port=listener.getLocalPort();c.tls=tls;c.auth=auth;c.user=login;c.password=password;c.from="sender@example.com";c.to="recipient@example.com";
        List<String> log=new ArrayList<>();
        try{
            if(error){Senders.SendError e=assertThrows(Senders.SendError.class,()->SmtpClient.send(c,"Тест","Привет\n.Line\n","stable-id",log::add,ssl.getSocketFactory()));assertTrue(e.safe,e.safe.contains(expected));if(expected.equals("451"))assertTrue(e.retry);}
            else SmtpClient.send(c,"Тест","Привет\n.Line\n","stable-id",log::add,ssl.getSocketFactory());
            task.get(12,TimeUnit.SECONDS);
            String trace=String.join("\n",log);assertFalse(trace.contains(c.user));assertFalse(trace.contains(c.password));assertFalse(trace.contains(Base64.getEncoder().encodeToString(c.password.getBytes(StandardCharsets.UTF_8))));assertFalse(trace.contains("Привет\n.Line\n"));
        }finally{listener.close();pool.shutdownNow();}
    }
    @Test public void gmailDisplayPasswordIsNormalizedForBothAuthModes(){
        Config c=new Config();c.host="smtp.gmail.com";c.password="abcd efgh ijkl mnop";assertEquals("abcdefghijklmnop",SmtpClient.passwordForAuth(c));
        c.auth="PLAIN";c.password=" abcd\u00a0efgh\u202fijkl\tmnop ";assertEquals("abcdefghijklmnop",SmtpClient.passwordForAuth(c));
        c.host="SMTP.GOOGLEMAIL.COM";c.password="abcdefghijklmnop";assertEquals(c.password,SmtpClient.passwordForAuth(c));
    }
    @Test public void passwordsOfOtherServersAndNonAppPasswordsAreNotAltered(){
        Config c=new Config();c.host="smtp.example.com";c.password="abcd efgh ijkl mnop";assertEquals(c.password,SmtpClient.passwordForAuth(c));
        c.host="smtp.gmail.com";c.password="short password";assertEquals(c.password,SmtpClient.passwordForAuth(c));
    }
    @Test public void implicitTlsLoginDeliversUtf8Mime()throws Exception {
        exercise("SSL","AUTO",p->{p.reply("220 Ready");p.ehlo("250 AUTH LOGIN PLAIN");assertEquals("AUTH LOGIN",p.read());p.reply("334 VXNlcm5hbWU6");assertEquals("private-user@example.com",new String(Base64.getDecoder().decode(p.read()),StandardCharsets.UTF_8));p.reply("334 UGFzc3dvcmQ6");assertEquals("secret-fixture-token",new String(Base64.getDecoder().decode(p.read()),StandardCharsets.UTF_8));p.reply("235 2.7.0 OK");p.finish();},false,"",false);
    }
    @Test public void genericLoginPasswordNotEmailIsSupported()throws Exception {
        exercise("SSL","LOGIN",p->{p.reply("220 Ready");p.ehlo("250 AUTH LOGIN");assertEquals("AUTH LOGIN",p.read());p.reply("334 VXNlcm5hbWU6");assertEquals("relay_login",new String(Base64.getDecoder().decode(p.read()),StandardCharsets.UTF_8));p.reply("334 UGFzc3dvcmQ6");assertEquals("relay_password",new String(Base64.getDecoder().decode(p.read()),StandardCharsets.UTF_8));p.reply("235 2.7.0 OK");p.finish();},false,"",false,"relay_login","relay_password");
    }
    @Test public void starttlsRefreshesEhloAndPlainChallenge()throws Exception {
        SSLContext ssl=context();exercise("STARTTLS","PLAIN",p->{p.reply("220 Ready");p.ehlo("250 STARTTLS");assertEquals("STARTTLS",p.read());p.reply("220 Begin TLS");SSLSocket upgraded=(SSLSocket)ssl.getSocketFactory().createSocket(p.socket,"localhost",p.socket.getPort(),true);upgraded.setUseClientMode(false);upgraded.startHandshake();p.socket=upgraded;p.reset();p.ehlo("250 AUTH PLAIN");assertTrue(p.read().startsWith("AUTH PLAIN "));p.reply("334 ");assertEquals("\0private-user@example.com\0secret-fixture-token",new String(Base64.getDecoder().decode(p.read()),StandardCharsets.UTF_8));p.reply("235 OK");p.finish();},false,"",false);
    }
    @Test public void unknownAuthenticationIsRejected() {
        Config c=new Config();c.host="smtp.example.com";c.user="sender@example.com";c.from=c.user;c.to="recipient@example.com";c.password="test";c.auth="UNSUPPORTED";
        assertThrows(IllegalArgumentException.class,c::validateEmail);
    }
    @Test public void authorizationRefusedHasStageCodeAndNoSecrets()throws Exception {
        exercise("SSL","PLAIN",p->{p.reply("220 Ready");p.ehlo("250 AUTH PLAIN");assertTrue(p.read().startsWith("AUTH PLAIN "));p.reply("535 5.7.80 private-user@example.com secret-fixture-token rejected");},true,"AUTH PLAIN: SMTP 535",false);
    }
    @Test public void temporaryDataRefusalIsRetryable()throws Exception {
        exercise("SSL","NONE",p->{p.reply("220 Ready");p.ehlo("250 OK");p.envelope();p.data();p.reply("451 4.3.0 temporary");},true,"451",false);
    }
    @Test public void recipientRefusalIdentifiesRcptStage()throws Exception {
        exercise("SSL","NONE",p->{p.reply("220 Ready");p.ehlo("250 OK");assertTrue(p.read().startsWith("MAIL FROM:"));p.reply("250 OK");assertTrue(p.read().startsWith("RCPT TO:"));p.reply("550 5.1.1 no user");},true,"RCPT TO: SMTP 550",false);
    }
    @Test public void acceptedMailRemainsSuccessWhenQuitDrops()throws Exception {
        exercise("SSL","NONE",p->{p.reply("220 Ready");p.ehlo("250 OK");p.envelope();p.data();p.reply("250 OK");assertEquals("QUIT",p.read());},false,"",false);
    }
    @Test public void anonymousLegacyHelo()throws Exception {
        exercise("SSL","NONE",p->{p.reply("220 Ready");assertTrue(p.read().startsWith("EHLO "));p.reply("502 unsupported");assertEquals("HELO relaybridge.local",p.read());p.reply("250 OK");p.finish();},false,"",false);
    }
    @Test public void refusesMissingStarttlsBeforeAuth()throws Exception {
        exercise("STARTTLS","AUTO",p->{p.reply("220 Ready");p.ehlo("250 AUTH LOGIN");assertNull(p.in.readLine());},true,"не объявил STARTTLS",false);
    }
    @Test public void certificateHostnameMustMatch()throws Exception {
        exercise("SSL","AUTO",p->{try{p.reply("220 Ready");p.read();fail("Client accepted wrong certificate hostname");}catch(SSLException|EOFException|SocketException expected){}},true,"TLS",true);
    }
    @Test public void rejectsPlaintextCredentialsBeforeConnect(){Config c=new Config();c.tls="NONE";c.auth="LOGIN";c.user="x";c.password="x";c.from="a@b.com";c.to="x@y.com";assertThrows(IllegalArgumentException.class,c::validateEmail);}
}
