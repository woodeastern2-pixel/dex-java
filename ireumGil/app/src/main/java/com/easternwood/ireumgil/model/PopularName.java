package com.easternwood.ireumgil.model;

public class PopularName {
    public final String gender;
    public final int rank;
    public final String name;
    public final int count;
    public final String periodEnd;
    public final String updatedAt;

    public PopularName(String gender, int rank, String name, int count, String periodEnd, String updatedAt) {
        this.gender = gender;
        this.rank = rank;
        this.name = name;
        this.count = count;
        this.periodEnd = periodEnd;
        this.updatedAt = updatedAt;
    }
}
