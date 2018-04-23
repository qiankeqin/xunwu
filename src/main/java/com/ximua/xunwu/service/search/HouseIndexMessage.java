package com.ximua.xunwu.service.search;

/**
 * kafka消息结构体
 */
public class HouseIndexMessage {
    public static final String INDEX = "index";
    public static final String REMOVE = "remove";
    public static final int MAX_RETRY = 3;//最大重试次数
    private Long houseId;
    private String operation;
    private int retry = 0;//消息并不一定是一次就消费完，如果发生异常等，可下次消费

    /**
     * 默认构造，防止jackson序列化失败
     */
    public HouseIndexMessage() {
    }

    /**
     * 完整构造器
     * @param houseId
     * @param operation
     * @param retry
     */
    public HouseIndexMessage(Long houseId, String operation, int retry) {
        this.houseId = houseId;
        this.operation = operation;
        this.retry = retry;
    }

    public Long getHouseId() {
        return houseId;
    }

    public void setHouseId(Long houseId) {
        this.houseId = houseId;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public int getRetry() {
        return retry;
    }

    public void setRetry(int retry) {
        this.retry = retry;
    }
}
