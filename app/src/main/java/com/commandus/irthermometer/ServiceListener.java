package com.commandus.irthermometer;

public interface ServiceListener {
    // ParserListener
    void onValue(final Measurement value);
    void onCurrentTemperature(final int currentTemperature);

    // Serial I/O
    void onConnect();
    void onConnectError(final Exception e);
    void onReadError(final Exception e);
    void onWriteError(final Exception e);
    void onInfo(String msg);
}
