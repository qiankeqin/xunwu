package com.ximua.xunwu.service.search;

import com.ximua.xunwu.service.ServiceResult;
import com.ximua.xunwu.web.controller.user.ServiceMultiResult;
import com.ximua.xunwu.web.form.RentSearch;

import java.util.List;
import java.util.Map;

/**
 * 检索接口
 */
public interface ISearchService {
    /**
     * 索引目标房源
     * @param houseId
     */
    boolean index(Long houseId);

    /**
     * 移除房源索引
     * @param houseId
     */
    void remove(Long houseId);

    /**
     * 根据查询条件，查询房屋信息，并获取到houseid，存储到ServiceResult中
     * @param rentSearch
     * @return
     */
    ServiceMultiResult<Long> query(RentSearch rentSearch);

    /**
     * search as you type 搜索输入时提示信息功，自动补全功能
     * @param prefix
     * @return
     */
    ServiceResult<List<String>> suggest(String prefix);

    /**
     * 聚合特定小区的房源数量
     * @param cityEnName
     * @param regionEnName
     * @return
     */
    ServiceResult<Long> aggregateDistrictHouse(String cityEnName,String regionEnName,String district);

    /**
     * 聚合bucket数据
     * @param cityEnName
     * @return
     */
    ServiceMultiResult<HouseBucketDTO> magAggregate(String cityEnName);

    /**
     * 城市级别查询
     * @return
     */
    ServiceMultiResult<Long> mapQuery(String cityEnName,String orderBy,String orderDirection,int start,int size);


    /**
     * 精确范围查询
     * @return
     */
    ServiceMultiResult<Long> mapQuery(MapSearch mapSearch);

}
