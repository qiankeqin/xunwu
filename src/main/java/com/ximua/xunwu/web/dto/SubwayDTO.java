package com.ximua.xunwu.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SubwayDTO {
    private String id;
    private String name;
    @JsonProperty(value = "city_en_name")
    private String cityEnName;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCityEnName() {
        return cityEnName;
    }

    public void setCityEnName(String cityEnName) {
        this.cityEnName = cityEnName;
    }
}
