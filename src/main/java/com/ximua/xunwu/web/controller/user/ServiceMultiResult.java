package com.ximua.xunwu.web.controller.user;

import java.util.List;

/**
 * 通用结果集
 * @param <T>
 */
public class ServiceMultiResult<T> {
    private long total;
    private List<T> result;

    public ServiceMultiResult(long total, List<T> result) {
        this.total = total;
        this.result = result;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<T> getResult() {
        return result;
    }

    public void setResult(List<T> result) {
        this.result = result;
    }

    //结果集的size
    public int getResultSize(){
        if(this.result ==null ){
            return 0;
        }
        return this.result.size();
    }
}
