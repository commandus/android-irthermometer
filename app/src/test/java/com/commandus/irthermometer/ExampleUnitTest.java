package com.commandus.irthermometer;

import android.view.KeyEvent;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void parser_is_ok() {
        ThermometerParser p = new ThermometerParser(new ParserListener() {
            @Override
            public void onValue(Measurement value) {
                System.out.println("Time: " + Long.toString(value.startTime));
                System.out.println("value: " + Integer.toString(value.maxT));
                // assertEquals(27315 + 3300, value.maxT);
            }

            @Override
            public void onCurrentTemperature(int currentTemperature) {
                // System.out.println(currentTemperature);
            }
        });

        // p.setDtSeconds(0);

        for (int i = 0; i < 100; i++) {
            String s = Integer.toString(27315 + 3300 + i);
            // System.out.println("put " + s);
            p.put(s);
            s = "\r\n";
            p.put(s);
        }

        p.flush();
        System.out.println("done");
    }

    @Test
    public void rfid_reader_is_ok() {
        RFIDReader r = new RFIDReader();
        long c;
        c = r.put(KeyEvent.KEYCODE_1);
        assertEquals(0, c);
        c = r.put(KeyEvent.KEYCODE_5);
        assertEquals(0, c);
        c = r.put(KeyEvent.KEYCODE_9);
        assertEquals(0, c);
        c = r.put(KeyEvent.KEYCODE_0);
        assertEquals(0, c);
        c = r.put(KeyEvent.KEYCODE_ENTER);
        assertEquals(1590, c);
        System.out.println(Long.toString(c));
    }

}