package ru.eyeone.relaybridge;
import org.junit.Test;
import java.util.Set;
import static org.junit.Assert.*;
public class SimRulesTest {
 @Test public void ringingSubscriptionMustBeUnique(){assertEquals(4,SimRules.unique(new int[]{4,99},1));assertEquals(-1,SimRules.unique(new int[]{4,99},2));assertEquals(-1,SimRules.unique(new int[0],0));}
 @Test public void callBodyIncludesSimBeforeNumber(){assertEquals("SIM: Sim 2 Megafon\nНомер: +420000000000",SimRules.callData("Sim 2 Megafon","+420000000000"));}
 @Test public void callsAndSmsCanUseDifferentSubscriptions(){assertTrue(SimRules.accepts(false,Set.of(4),4));assertFalse(SimRules.accepts(false,Set.of(99),4));assertTrue(SimRules.accepts(false,Set.of(99),99));}
 @Test public void slotNumbersAreIndependentOfSubscriptionIds(){assertEquals("Sim 1 MTS",SimRules.label(0,"МТС"));assertEquals("Sim 2 Megafon",SimRules.label(1,"МегаФон"));}
 @Test public void otherOperatorsAndUnknownsArePreserved(){assertEquals("Sim 2 Vodafone",SimRules.label(1," Vodafone "));assertEquals("Sim ? Оператор не определён",SimRules.label(-1,null));assertEquals("Sim 1 Оператор не определён",SimRules.label(0," "));}
 @Test public void carrierCannotInjectLines(){assertEquals("Sim 1 MTS x",SimRules.label(0,"MTS\r\nx"));}
 @Test public void allSimsIncludesFutureAndUnknownSubscription(){assertTrue(SimRules.accepts(true,Set.of(),4));assertTrue(SimRules.accepts(true,Set.of(),-1));assertTrue(SimRules.accepts(true,Set.of(4),99));}
 @Test public void selectionUsesSubscriptionIdNotSlotNumber(){assertTrue(SimRules.accepts(false,Set.of(4),4));assertFalse(SimRules.accepts(false,Set.of(4),0));assertFalse(SimRules.accepts(false,Set.of(4),1));assertFalse(SimRules.accepts(false,Set.of(4),99));}
 @Test public void noSelectionOrUnknownSourceCapturesNothing(){assertFalse(SimRules.accepts(false,Set.of(),4));assertFalse(SimRules.accepts(false,Set.of(4),-1));}
}
