package com.hello.suripu.app.sensors;

public enum  SensorUnit {
    CELSIUS("ºC"),
    FAHRENHEIT("ºF"),
    MG_CM("micro grams per cubic meter"),
    PERCENT("precent"),
    LUX("lux"),
    DB("decibels"),
    VOC("micro grams per cubic meter"),
    PPM("ppm"),
    RATIO("ratio"),
    KELVIN("kelvin"),
    KPA("kilopascal"),
    COUNT("count"),
    MILLIBAR("milli bar");

    private String value;

    SensorUnit(final String value) {
        this.value = value;
    }

    public String value() { return this.value; }
}
