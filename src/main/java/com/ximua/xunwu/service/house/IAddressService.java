package com.ximua.xunwu.service.house;

import com.ximua.xunwu.entity.SupportAddress;
import com.ximua.xunwu.service.ServiceResult;
import com.ximua.xunwu.service.search.BaiduMapLocation;
import com.ximua.xunwu.web.dto.SubwayDTO;
import com.ximua.xunwu.web.dto.SubwayStationDTO;
import com.ximua.xunwu.web.dto.SupportAddressDTO;
import com.ximua.xunwu.web.controller.user.ServiceMultiResult;

import java.util.Map;

/**
 * 地址服务接口
 */
public interface IAddressService {
    /**
     * 注意，我们返回到类型最好不要使SupportAddress这种数据库类型，因为前端并不需要那么多信息，并且为了保护数据，应该使用DTO类型
     * @return
     */
    ServiceMultiResult<SupportAddressDTO> findAllCities();

    /**
     * 根据英文简写获取具体区域信息
     * @param cityEnName
     * @param regionEnName
     * @return
     */
    Map<SupportAddress.Level,SupportAddressDTO> findCityAndRegion(String cityEnName, String regionEnName);

    /**
     * 查询城市下面的区域
     * @param cityName
     * @return
     */
    ServiceMultiResult<SupportAddressDTO> findAllRegionByCityName(String cityName);

    /**
     * 查询城市的地铁线路列表
     * @param cityEnName
     * @return
     */
    ServiceMultiResult<SubwayDTO> findAllSubwayByCityEnName(String cityEnName);

    /**
     * 根据地铁线路号获取地铁口列表
     * @param subwayId
     * @return
     */
    ServiceMultiResult<SubwayStationDTO> findAllSubwayStationBySubwayId(Long subwayId);

    /**
     * 根据地铁线号获取地铁
     * @param subwayLineId
     * @return
     */
    ServiceResult<SubwayDTO> findAllSubway(Long subwayLineId);

    /**
     * 根据地铁站ID获取地铁站
     * @param subwayStationId
     * @return
     */
    ServiceResult<SubwayStationDTO> findAllSubwayStation(Long subwayStationId);

    /**
     * 根据英文城市名获取城市详细信息
     * @param cityEnName
     * @return
     */
    ServiceResult<SupportAddressDTO> findCity(String cityEnName);

    /**
     * 根据城市以及具体位置获取百度地图到经纬度
     */
    ServiceResult<BaiduMapLocation> getBaiduMapLocation(String city,String address);
}
