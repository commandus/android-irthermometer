package com.commandus.irthermometer;

import android.view.KeyEvent;

/**
 * Read RFID card from the USB keyboard
 */
public class RFIDReader {
    private StringBuilder buf;
    private long r;

    RFIDReader() {
        buf = new StringBuilder();
    }

    public long put(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            try {
                r = Long.parseLong(buf.toString());
            } catch (NumberFormatException e) {
                return 0;
            }
            buf.delete(0, buf.length());
            return r;

        } else {
            if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                buf.append((char) ((int) '0' + keyCode - KeyEvent.KEYCODE_0));
            }
        }
        return 0;
    }
}
