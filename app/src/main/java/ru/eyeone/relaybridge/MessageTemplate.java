package ru.eyeone.relaybridge;
import java.util.*;
import java.util.regex.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
final class MessageTemplate {
    static final String DEFAULT="RelayBridge · {{type}}\n{{time}}\n\n{{data}}";
    static final Set<String> VARIABLES=Set.of("time","date","type","data","title","message","number","sim","app","package");
    private static final Pattern TOKEN=Pattern.compile("\\{\\{\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\}\\}");
    static void validate(String template){
        if(template==null||template.isBlank())throw new IllegalArgumentException("Шаблон сообщения не может быть пустым");
        if(template.length()>4000)throw new IllegalArgumentException("Шаблон: не более 4000 символов");
        Matcher matcher=TOKEN.matcher(template);
        while(matcher.find())if(!VARIABLES.contains(matcher.group(1).toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Неизвестная переменная: {{"+matcher.group(1)+"}}");
        String literals=matcher.replaceAll("");if(literals.contains("{{")||literals.contains("}}"))throw new IllegalArgumentException("Переменные записываются как {{time}} — проверьте скобки");
    }
    static Map<String,String> values(String type,String title,String message,String data,String number,String sim,String app,String pkg,long millis){
        ZonedDateTime time=Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault());Map<String,String> v=new HashMap<>();
        v.put("time",time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX",Locale.ROOT)));v.put("date",time.format(DateTimeFormatter.ISO_LOCAL_DATE));
        String[] names={"type","title","message","data","number","sim","app","package"};String[] content={type,title,message,data,number,sim,app,pkg};
        for(int i=0;i<names.length;i++)v.put(names[i],content[i]==null?"":content[i]);return v;
    }
    static String render(String template,Map<String,String> values){
        validate(template);Matcher matcher=TOKEN.matcher(template);StringBuilder out=new StringBuilder();int position=0;
        while(matcher.find()){append(out,template.substring(position,matcher.start()));String value=values.get(matcher.group(1).toLowerCase(Locale.ROOT));append(out,value==null?"":value);position=matcher.end();}
        append(out,template.substring(position));if(out.toString().isBlank())throw new IllegalArgumentException("Шаблон дал пустое сообщение — добавьте {{data}} или {{message}}");return out.toString();
    }
    private static void append(StringBuilder out,String value){int available=32000-out.length();if(available>0)out.append(value,0,TextTools.chunkEnd(value,0,available));}
}
