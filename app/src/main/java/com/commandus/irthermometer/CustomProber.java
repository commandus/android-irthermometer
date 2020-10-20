package com.commandus.irthermometer;

import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
import com.hoho.android.usbserial.driver.ProbeTable;
import com.hoho.android.usbserial.driver.UsbSerialProber;

/**
 * add devices here, that are not known to DefaultProber
 * if the App should auto start for these devices, also
 * add IDs to app/src/main/res/xml/usb_device_filter.xml
 */
class CustomProber {
    static UsbSerialProber getCustomProber() {
        ProbeTable customTable = new ProbeTable();
        customTable.addProduct(Settings.USB_VENDOR_ID, Settings.USB_PRODUCT_ID, CdcAcmSerialDriver.class); // e.g. Digispark CDC
        return new UsbSerialProber(customTable);
    }

}
