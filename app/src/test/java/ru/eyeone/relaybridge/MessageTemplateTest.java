package ru.eyeone.relaybridge;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class MessageTemplateTest {
 private Map<String,String> sample(){return MessageTemplate.values("SMS","SMS от +7","Привет","От: +7\nSIM: Sim 1 MTS\nПривет","+7","Sim 1 MTS","","",0);}
 @Test public void defaultCallEnvelopeIncludesDestinationSim(){Map<String,String> call=MessageTemplate.values("Входящий звонок","Входящий звонок","Номер: +420000000000",SimRules.callData("Sim 2 Megafon","+420000000000"),"+420000000000","Sim 2 Megafon","","",0);assertEquals("RelayBridge · Входящий звонок\n"+call.get("time")+"\n\nSIM: Sim 2 Megafon\nНомер: +420000000000",MessageTemplate.render(MessageTemplate.DEFAULT,call));}
 @Test public void defaultPreservesOldEnvelope(){Map<String,String> v=sample();assertEquals("RelayBridge · SMS\n"+v.get("time")+"\n\n"+v.get("data"),MessageTemplate.render(MessageTemplate.DEFAULT,v));}
 @Test public void customFieldsAndNewlinesWork(){assertEquals("SMS\nSMS от +7\nПривет\n+7 Sim 1 MTS",MessageTemplate.render("{{type}}\n{{title}}\n{{message}}\n{{number}} {{sim}}",sample()));}
 @Test public void substitutionIsLiteralAndNeverRecursive(){assertEquals("$5 \\ {{time}}",MessageTemplate.render("{{message}}",Map.of("message","$5 \\ {{time}}")));}
 @Test public void unknownAndMalformedVariablesAreRejected(){assertThrows(IllegalArgumentException.class,()->MessageTemplate.validate("{{password}}"));assertThrows(IllegalArgumentException.class,()->MessageTemplate.validate("{{time}"));assertThrows(IllegalArgumentException.class,()->MessageTemplate.validate("{{}}"));}
 @Test public void whitespaceCaseRepeatedTokensAndMissingFields(){assertEquals("SMS SMS ",MessageTemplate.render("{{ TYPE }} {{type}} {{app}}",sample()));}
 @Test public void blankTemplateAndBlankResultAreRejected(){assertThrows(IllegalArgumentException.class,()->MessageTemplate.validate(" \n"));assertThrows(IllegalArgumentException.class,()->MessageTemplate.render("{{app}}",sample()));assertThrows(IllegalArgumentException.class,()->MessageTemplate.validate("x".repeat(4001)));}
 @Test public void outputIsBoundedWithoutSplittingEmoji(){String text=MessageTemplate.render("{{message}}{{message}}",Map.of("message","😀".repeat(20000)));assertEquals(32000,text.length());assertFalse(Character.isHighSurrogate(text.charAt(text.length()-1)));}
 @Test public void notificationAndCallHaveIndependentFields(){Map<String,String> push=MessageTemplate.values("Уведомление","Заголовок","Текст","Полные данные","","","Signal","org.signal",0);assertEquals("Signal org.signal Заголовок Текст",MessageTemplate.render("{{app}} {{package}} {{title}} {{message}}",push));Map<String,String> call=MessageTemplate.values("Входящий звонок","Входящий звонок","Номер: +7","Номер: +7","+7","Sim 2 Megafon","","",0);assertEquals("+7 Sim 2 Megafon",MessageTemplate.render("{{number}} {{sim}}",call));assertTrue(call.get("time").matches("\\d{4}-\\d{2}-\\d{2} .*"));}
}
