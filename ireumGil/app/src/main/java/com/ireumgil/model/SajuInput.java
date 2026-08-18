package com.ireumgil.model;

public class SajuInput {
    public final int year;
    public final int month;
    public final int day;
    public final int hour;
    public final Integer minute;
    public final boolean lunar;
    public final String gender;

    public SajuInput(int year, int month, int day, int hour, Integer minute, boolean lunar, String gender) {
        this.year = year;
        this.month = month;
        this.day = day;
        this.hour = hour;
        this.minute = minute;
        this.lunar = lunar;
        this.gender = gender;
    }

    public int minuteOrZero() {
        return minute == null ? 0 : minute;
    }
}
