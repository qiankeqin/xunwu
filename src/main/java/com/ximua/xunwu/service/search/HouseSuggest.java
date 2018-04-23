package com.ximua.xunwu.service.search;

/**
 * 输入前缀返回的实体信息，用户返回suggest查询到到信息
 */
public class HouseSuggest {
    private String input;
    private int weight = 10;//默认权重

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }
}
