package ru.eyeone.relaybridge;

import android.telephony.TelephonyManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class PhoneNumbersTest {
    @Test public void australianSmsAndCallHaveSameInternationalNumber() {
        assertEquals("+61447369039", PhoneNumbers.format("0447369039", "au"));
        assertEquals("+61447369039", PhoneNumbers.format("+61 447 369 039", "RU"));
    }
    @Test public void nationalFormatsUseTheirCountryRules() {
        assertEquals("+79001234567", PhoneNumbers.format("8 (900) 123-45-67", "ru"));
        assertEquals("+447911123456", PhoneNumbers.format("07911123456", "gb"));
        assertEquals("+12025550123", PhoneNumbers.format("202-555-0123", "us"));
        assertEquals("+61447369039", PhoneNumbers.format("0061447369039", "GB"));
    }
    @Test public void missingCountryDoesNotGuessFromPhoneLanguage() {
        assertEquals("0447369039", PhoneNumbers.format("0447369039", ""));
        assertEquals("+61447369039", PhoneNumbers.format("+61447369039", ""));
        assertEquals("", PhoneNumbers.country(RuntimeEnvironment.getApplication(), -1));
    }
    @Test public void shortCodesNamesAndHiddenNumbersStayIntact() {
        for (String raw : new String[]{"BANK", "112", "12345", "*100#", "0447 x369039", "+123"})
            assertEquals(raw, PhoneNumbers.format(raw, "AU"));
        assertEquals("Скрыт или недоступен", PhoneNumbers.format(null, "AU"));
    }
    @Test public void eventSubscriptionProvidesNetworkCountryThenSimFallback() {
        android.app.Application app = RuntimeEnvironment.getApplication();
        TelephonyManager scoped = app.getSystemService(TelephonyManager.class);
        Shadows.shadowOf(scoped).setTelephonyManagerForSubscriptionId(22, scoped);
        Shadows.shadowOf(scoped).setNetworkCountryIso("au");
        Shadows.shadowOf(scoped).setSimCountryIso("gb");
        assertEquals("+61447369039", PhoneNumbers.international(app, "0447369039", 22));
        Shadows.shadowOf(scoped).setNetworkCountryIso("");
        assertEquals("GB", PhoneNumbers.country(app, 22));
    }
    @Test public void normalizedNumberIsUsedInBothMessagePayloads() {
        String number = PhoneNumbers.format("0447369039", "AU");
        assertTrue(ContactNames.smsData("Аня", number, "Sim 2", "Тест").contains("Номер: +61447369039"));
        assertTrue(ContactNames.callData("Аня", number, "Sim 2").contains("Номер: +61447369039"));
    }
}
