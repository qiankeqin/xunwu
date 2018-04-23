package com.ximua.xunwu.service.search;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 百度位置信息结构体
 */
public class BaiduMapLocation {
    //经度
    @JsonProperty("lon")
    private double longitude;

    //维度
    @JsonProperty("lat")
    private double latitude;

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }
}
