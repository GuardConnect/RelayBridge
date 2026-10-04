package ru.eyeone.relaybridge;
import java.util.Set;
/** Subscription IDs select cards; slot indices are used only for display. */
final class SimRules {
    static boolean accepts(boolean all,Set<Integer> selected,int subscription){return all||(subscription>=0&&selected.contains(subscription));}
    static int unique(int[] ids,int count){return count==1?ids[0]:-1;}
    static String callData(String sim,String number){return "SIM: "+sim+"\nНомер: "+number;}
    static String label(int slot,CharSequence carrier){
        String name=carrier==null?"":carrier.toString().replaceAll("[\\p{Cntrl}\\s]+"," ").trim();
        if(name.equalsIgnoreCase("МТС")||name.equalsIgnoreCase("MTS"))name="MTS";
        if(name.equalsIgnoreCase("МегаФон")||name.equalsIgnoreCase("MegaFon"))name="Megafon";
        if(name.isEmpty())name="Оператор не определён";
        return "Sim "+(slot>=0?String.valueOf(slot+1):"?")+" "+name;
    }
}
