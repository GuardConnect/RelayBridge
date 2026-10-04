package ru.eyeone.relaybridge;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.net.ssl.*;
/** SMTP transport independent of JavaMail. AUTH is never sent without verified TLS. */
final class SmtpClient {
    private static final ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"smtp-deadline");t.setDaemon(true);return t;});
    private Socket socket;
    private InputStream input;
    private OutputStream output;
    private String stage="TCP";
    private boolean secure;
    private final Consumer<String> trace;
    private final SSLSocketFactory factory;
    private SmtpClient(Consumer<String> log,SSLSocketFactory f){trace=log;factory=f;}
    static void send(Config c,String kind,String body,String id,Consumer<String> trace) throws Exception {
        send(c,kind,body,id,trace,(SSLSocketFactory)SSLSocketFactory.getDefault());
    }
    static void send(Config c,String kind,String body,String id,Consumer<String> trace,SSLSocketFactory factory) throws Exception {
        c.validateEmail();new SmtpClient(trace,factory).deliver(c,kind,body,id);
    }
    private void step(String next){stage=next;trace.accept("Этап: "+next);}
    private void streams() throws IOException {input=socket.getInputStream();output=socket.getOutputStream();}
    private void tls(Config c) throws Exception {
        step("TLS handshake / проверка сертификата");
        SSLSocket ssl=(SSLSocket)factory.createSocket(socket,c.host,c.port,true);socket=ssl;ssl.setSoTimeout(20000);
        SSLParameters parameters=ssl.getSSLParameters();parameters.setEndpointIdentificationAlgorithm("HTTPS");ssl.setSSLParameters(parameters);
        List<String> enabled=new ArrayList<>();for(String p:ssl.getSupportedProtocols())if(p.equals("TLSv1.2")||p.equals("TLSv1.3"))enabled.add(p);
        ssl.setEnabledProtocols(enabled.toArray(new String[0]));ssl.startHandshake();secure=true;streams();
        trace.accept("TLS: "+ssl.getSession().getProtocol()+" / "+ssl.getSession().getCipherSuite()+"; сертификат подтверждён");
    }
    private void deliver(Config c,String kind,String body,String id) throws Exception {
        socket=new Socket();ScheduledFuture<?> deadline=deadlines.schedule(()->{try{socket.close();}catch(Exception ignored){}},120,TimeUnit.SECONDS);
        boolean accepted=false;
        try {
            trace.accept("Сервер: "+c.host+":"+c.port+"; "+c.tls+"; AUTH "+c.auth);
            step("DNS / TCP connect");socket.connect(new InetSocketAddress(c.host,c.port),15000);socket.setSoTimeout(20000);streams();
            if(c.tls.equals("SSL"))tls(c);
            step("Приветствие сервера");expect(reply(),220);
            Reply hello=ehlo(c);
            if(c.tls.equals("STARTTLS")) {
                if(!hello.has("STARTTLS"))throw new Senders.SendError(false,"EHLO: сервер не объявил STARTTLS. Проверьте режим TLS и порт.");
                step("STARTTLS");command("STARTTLS","STARTTLS");expect(reply(),220);tls(c);hello=ehlo(c);
            }
            authenticate(c,hello);
            boolean utf8=!StandardCharsets.US_ASCII.newEncoder().canEncode(c.from+c.to);
            if(utf8&&!hello.has("SMTPUTF8"))throw new Senders.SendError(false,"MAIL FROM: сервер не поддерживает международные адреса SMTPUTF8");
            step("MAIL FROM");command("MAIL FROM:<"+c.from+">"+(utf8?" SMTPUTF8":""),"MAIL FROM: <адрес скрыт>");expect(reply(),250);
            for(String recipient:c.to.split("[,;]")){
                step("RCPT TO");command("RCPT TO:<"+recipient.trim()+">","RCPT TO: <адрес скрыт>");expect(reply(),250,251,252);
            }
            step("DATA / запрос передачи письма");command("DATA","DATA");expect(reply(),354);
            step("DATA / передача MIME");String mime=mime(c,kind,body,id);
            output.write(mime.getBytes(StandardCharsets.UTF_8));output.write(".\r\n".getBytes(StandardCharsets.US_ASCII));output.flush();
            trace.accept("C: <заголовки и содержимое письма скрыты>");step("DATA / подтверждение приёма");expect(reply(),250);accepted=true;
            trace.accept("УСПЕХ: SMTP-сервер принял письмо; это не подтверждает доставку во Входящие");
            step("QUIT");try{command("QUIT","QUIT");expect(reply(),221);}catch(Exception ignored){trace.accept("QUIT: соединение закрыто после подтверждённого приёма; письмо не повторяется");}
        } catch(Senders.SendError e){trace.accept("ОШИБКА: "+e.safe);throw e;}
          catch(Exception e){String message=stage+": "+(e instanceof EOFException?"Сервер закрыл соединение до ответа":DeliveryErrors.describe(e))+(stage.equals("DATA / подтверждение приёма")?" Результат доставки неизвестен; при повторе возможен дубликат.":"");trace.accept("ОШИБКА: "+message);throw new Senders.SendError(true,message);}
        finally {deadline.cancel(false);try{socket.close();}catch(Exception ignored){}trace.accept(accepted?"Сессия завершена: принято сервером":"Сессия завершена: не подтверждено");}
    }
    private Reply ehlo(Config c) throws Exception {
        step("EHLO");command("EHLO relaybridge.local","EHLO relaybridge.local");Reply hello=reply();
        if((hello.code==500||hello.code==502||hello.code==504)&&c.auth.equals("NONE")&&!c.tls.equals("STARTTLS")){
            command("HELO relaybridge.local","HELO relaybridge.local");hello=reply();
        }
        expect(hello,250);trace.accept("Возможности: STARTTLS="+hello.has("STARTTLS")+"; AUTH="+String.join(",",hello.mechanisms())+"; SMTPUTF8="+hello.has("SMTPUTF8"));return hello;
    }
    private void authenticate(Config c,Reply hello) throws Exception {
        if(c.auth.equals("NONE")){trace.accept("AUTH: выключена пользователем (SMTP relay)");return;}
        if(!secure)throw new Senders.SendError(false,"AUTH: авторизация разрешена только через проверенное TLS-соединение");
        Set<String> offered=hello.mechanisms();String mechanism=c.auth;
        if(mechanism.equals("AUTO"))mechanism=offered.contains("LOGIN")?"LOGIN":offered.contains("PLAIN")?"PLAIN":"";
        if(mechanism.isEmpty()||!offered.contains(mechanism))throw new Senders.SendError(false,"AUTH: сервер не объявил выбранный способ. Доступно: "+String.join(",",offered)+". Выберите способ во вкладке Отправка.");
        step("AUTH "+mechanism);
        String password=passwordForAuth(c);
        Reply result;
        if(mechanism.equals("LOGIN")) {
            command("AUTH LOGIN","AUTH LOGIN");result=reply();expect(result,334);
            command(base64(c.user),"<логин скрыт>");expect(reply(),334);
            command(base64(password),"<пароль скрыт>");result=reply();
        }else if(mechanism.equals("PLAIN")){
            String value=base64("\0"+c.user+"\0"+password);command("AUTH PLAIN "+value,"AUTH PLAIN <данные скрыты>");result=reply();
            if(result.code==334){command(value,"<данные AUTH скрыты>");result=reply();}
        }else throw new Senders.SendError(false,"AUTH: неизвестный способ авторизации");
        expect(result,235);trace.accept("AUTH: сервер подтвердил авторизацию");
    }
    static String passwordForAuth(Config c){
        if(!c.host.equalsIgnoreCase("smtp.gmail.com")&&!c.host.equalsIgnoreCase("smtp.googlemail.com"))return c.password;
        String compact=c.password.replaceAll("[\\s\\u00A0\\u202F]+","");
        return compact.matches("[A-Za-z0-9]{16}")?compact:c.password;
    }
    private void command(String wire,String visible) throws IOException {
        if(wire.indexOf('\r')>=0||wire.indexOf('\n')>=0)throw new IOException("Invalid command");
        trace.accept("C: "+visible);output.write((wire+"\r\n").getBytes(StandardCharsets.UTF_8));output.flush();
    }
    private Reply reply() throws Exception {
        List<String> lines=new ArrayList<>();int code=-1;
        for(int n=0;n<100;n++) {
            String line=line();if(line.length()<3||!line.substring(0,3).matches("[0-9]{3}"))throw new IOException("Malformed SMTP reply");
            int next=Integer.parseInt(line.substring(0,3));if(code!=-1&&code!=next)throw new IOException("Inconsistent SMTP reply");code=next;lines.add(line);
            trace.accept("S: "+code+enhanced(line)+(code==334?" <вызов AUTH скрыт>":""));
            if(line.length()==3||line.charAt(3)==' ')return new Reply(code,lines);
            if(line.charAt(3)!='-')throw new IOException("Malformed SMTP continuation");
        }throw new IOException("SMTP reply too long");
    }
    private String line() throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream();int previous=-1;
        for(int n=0;n<8192;n++){int next=input.read();if(next<0)throw new EOFException("SMTP closed");if(previous=='\r'&&next=='\n')return b.toString(StandardCharsets.US_ASCII).substring(0,b.size()-1);b.write(next);previous=next;}
        throw new IOException("SMTP line too long");
    }
    private void expect(Reply response,int...allowed) throws Senders.SendError {
        for(int code:allowed)if(code==response.code)return;
        String numeric=response.code+enhanced(String.join(" ",response.lines));
        String hint=stage.startsWith("AUTH")?SmtpDiagnostics.auth(numeric):"SMTP "+numeric+": команда отклонена сервером";
        throw new Senders.SendError(response.code/100==4,stage+": "+hint);
    }
    private static String enhanced(String line){java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?<![0-9])([245]\\.[0-9]{1,3}\\.[0-9]{1,3})(?![0-9.])").matcher(line);return m.find()?" / "+m.group(1):"";}
    private static String base64(String value){return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));}
    static String mime(Config c,String kind,String body,String id){
        StringBuilder subject=new StringBuilder();String label="RelayBridge: "+kind;int cursor=0;
        while(cursor<label.length()){int end=Math.min(label.length(),cursor+12);if(end<label.length()&&Character.isHighSurrogate(label.charAt(end-1)))end--;if(subject.length()>0)subject.append("\r\n ");subject.append("=?UTF-8?B?").append(base64(label.substring(cursor,end))).append("?=");cursor=end;}
        String safeId=id.replaceAll("[^A-Za-z0-9._-]","_");if(safeId.length()>150)safeId=safeId.substring(0,150);
        String date=new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z",Locale.US).format(new Date());
        String encoded=Base64.getMimeEncoder(76,"\r\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(body.getBytes(StandardCharsets.UTF_8));
        return "Date: "+date+"\r\nFrom: <"+c.from+">\r\nTo: "+String.join(",\r\n ",java.util.Arrays.stream(c.to.split("[,;]")).map(String::trim).toArray(String[]::new))+"\r\nMessage-ID: <"+safeId+"@relaybridge.local>\r\nSubject: "+subject+"\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n"+encoded+"\r\n";
    }
    private static final class Reply {
        final int code;final List<String> lines;Reply(int c,List<String> l){code=c;lines=l;}
        boolean has(String feature){for(String line:lines)if(line.length()>4&&line.substring(4).toUpperCase(Locale.ROOT).split("[ =]",2)[0].equals(feature))return true;return false;}
        Set<String> mechanisms(){Set<String> result=new LinkedHashSet<>();for(String line:lines){String value=line.length()>4?line.substring(4).toUpperCase(Locale.ROOT):"";if(value.startsWith("AUTH ")||value.startsWith("AUTH="))for(String s:value.substring(5).split("\\s+"))if(s.equals("PLAIN")||s.equals("LOGIN"))result.add(s);}return result;}
    }
}
