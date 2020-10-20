package com.commandus.irthermometer;

public interface ParserListener {
    void onValue(final Measurement value);
    void onCurrentTemperature(final int value);
}
