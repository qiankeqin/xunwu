package com.ximua.xunwu.service.house;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ximua.xunwu.entity.Subway;
import com.ximua.xunwu.entity.SubwayStation;
import com.ximua.xunwu.entity.SupportAddress;
import com.ximua.xunwu.repository.SubwayRepository;
import com.ximua.xunwu.repository.SubwayStationRepository;
import com.ximua.xunwu.repository.SupportAddressRepository;
import com.ximua.xunwu.service.ServiceResult;
import com.ximua.xunwu.service.search.BaiduMapLocation;
import com.ximua.xunwu.web.dto.SubwayDTO;
import com.ximua.xunwu.web.dto.SubwayStationDTO;
import com.ximua.xunwu.web.dto.SupportAddressDTO;
import com.ximua.xunwu.web.controller.user.ServiceMultiResult;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 地址服务
 */
@Service
public class AddressServiceImpl implements IAddressService {
    @Autowired
    private SupportAddressRepository supportAddressRepository;
    @Autowired
    private SubwayRepository subwayRepository;
    @Autowired
    private SubwayStationRepository subwayStationRepository;
    @Autowired
    private ModelMapper modelMapper;
    @Autowired
    private ObjectMapper objectMapper;

    //百度api，服务端key
    private static final String BAIDU_MAP_KEY = "yQcaA5hDKQonDfjx4EL2ZMDT1ygwZYYf";
    //百度api，地理位置转码ap，如：http://api.map.baidu.com/geocoder/v2/?address=北京市海淀区上地十街10号&output=json&ak=您的ak&callback=showLocation
    private static final String BAIDU_MAP_GEOCONV_API = "http://api.map.baidu.com/geocoder/v2/?";//

    private static final Logger logger = LoggerFactory.getLogger(IAddressService.class);

    /**
     * 获取城市列表
     * @return
     */
    @Override
    public ServiceMultiResult<SupportAddressDTO> findAllCities() {
        List<SupportAddress> addresses = supportAddressRepository.findAllByLevel(SupportAddress.Level.CITY.getValue());
        List<SupportAddressDTO> addressDTOS = new ArrayList<>();
        for(SupportAddress supportAddress : addresses){
            //使用ModelMapper复制Bean
            SupportAddressDTO target = modelMapper.map(supportAddress, SupportAddressDTO.class);
            addressDTOS.add(target);
        }
        ServiceMultiResult<SupportAddressDTO> result = new ServiceMultiResult<>(addressDTOS.size(),addressDTOS);
        return result;
    }

    @Override
    public Map<SupportAddress.Level, SupportAddressDTO> findCityAndRegion(String cityEnName, String regionEnName) {
        Map<SupportAddress.Level, SupportAddressDTO> result = new HashMap<>();

        SupportAddress city = supportAddressRepository.findByEnNameAndLevel(cityEnName, SupportAddress.Level.CITY
                .getValue());
        SupportAddress region = supportAddressRepository.findByEnNameAndBelongTo(regionEnName, city.getEnName());

        result.put(SupportAddress.Level.CITY, modelMapper.map(city, SupportAddressDTO.class));
        result.put(SupportAddress.Level.REGION, modelMapper.map(region, SupportAddressDTO.class));
        return result;
    }

    /**
     * 获取区域列表
     * @param cityName 城市名称
     * @return
     */
    @Override
    public ServiceMultiResult<SupportAddressDTO> findAllRegionByCityName(String cityName) {
        if(cityName.isEmpty()){
            ServiceMultiResult<SupportAddressDTO> result = new ServiceMultiResult<>(0,null);
            return result;
        }
        System.out.println(cityName);
        List<SupportAddress> addresses = supportAddressRepository.findAllByLevelAndBelongTo(SupportAddress.Level.REGION.getValue(), cityName);
        System.out.println(addresses);
        List<SupportAddressDTO> addressDTOS = new ArrayList<>();
        for(SupportAddress supportAddress:addresses){
            SupportAddressDTO supportAddressDTO = modelMapper.map(supportAddress,SupportAddressDTO.class);
            addressDTOS.add(supportAddressDTO);
        }
        ServiceMultiResult<SupportAddressDTO> result = new ServiceMultiResult<>(addressDTOS.size(),addressDTOS);
        return result;
    }

    /**
     * 根据城市名称获取地铁列表
     * @param cityEnName
     * @return
     */
    @Override
    public ServiceMultiResult<SubwayDTO> findAllSubwayByCityEnName(String cityEnName) {
        List<Subway> subways = subwayRepository.findAllByCityEnName(cityEnName);
        List<SubwayDTO> subwayDTOS = new ArrayList<>();
        subways.forEach(subway->{
            SubwayDTO subwayDTO = modelMapper.map(subway, SubwayDTO.class);
            subwayDTOS.add(subwayDTO);
        });
        ServiceMultiResult<SubwayDTO> result = new ServiceMultiResult<>(subwayDTOS.size(),subwayDTOS);
        return result;
    }

    /**
     * 根据地铁线路获取地铁口列表
     * @param subwayId
     * @return
     */
    @Override
    public ServiceMultiResult<SubwayStationDTO> findAllSubwayStationBySubwayId(Long subwayId) {
        List<SubwayStation> subwayStations = subwayStationRepository.findAllBySubwayId(subwayId);
        List<SubwayStationDTO> subwayStationDTOS = new ArrayList<>();
        for(SubwayStation subwayStation : subwayStations){
            SubwayStationDTO subwayStationDTO = modelMapper.map(subwayStation,SubwayStationDTO.class);
            subwayStationDTOS.add(subwayStationDTO);
        }
        ServiceMultiResult<SubwayStationDTO> result = new ServiceMultiResult<>(subwayStationDTOS.size(),subwayStationDTOS);
        return result;
    }

    /**
     * 获取地铁线
     * @param subwayId
     * @return
     */
    @Override
    public ServiceResult<SubwayDTO> findAllSubway(Long subwayId) {
        if(subwayId==null){
            return ServiceResult.notFound();
        }
        Subway subway = subwayRepository.findOne(subwayId);
        if(subway==null){
            return ServiceResult.notFound();
        }
        return ServiceResult.of(modelMapper.map(subway,SubwayDTO.class));
    }

    /**
     * 获取地铁站
     * @param stationId
     * @return
     */
    @Override
    public ServiceResult<SubwayStationDTO> findAllSubwayStation(Long stationId) {
        if (stationId == null) {
            return ServiceResult.notFound();
        }
        SubwayStation station = subwayStationRepository.findOne(stationId);
        System.out.println(station);
        if (station == null) {
            return ServiceResult.notFound();
        }
        return ServiceResult.of(modelMapper.map(station, SubwayStationDTO.class));
    }

    /**
     * 根据城市英文简写获取城市详细信息
     * @param cityEnName
     * @return
     */
    @Override
    public ServiceResult<SupportAddressDTO> findCity(String cityEnName) {
        if(cityEnName == null){
            return ServiceResult.notFound();
        }
        SupportAddress supportAddress = supportAddressRepository.findByEnNameAndLevel(cityEnName, SupportAddress.Level.CITY.getValue());

        System.out.println(supportAddress);
        if(supportAddress == null){
            return ServiceResult.notFound();
        }
        SupportAddressDTO addressDTO = modelMapper.map(supportAddress,SupportAddressDTO.class);
        return ServiceResult.of(addressDTO);

    }

    @Override
    public ServiceResult<BaiduMapLocation> getBaiduMapLocation(String city, String address) {
        String encodeAddress;
        String encodeCity;
        try {
            //转码
            encodeAddress = URLEncoder.encode(address, "UTF-8");
            encodeCity = URLEncoder.encode(city,"UTF-8");
            System.out.println(encodeAddress + "-" + encodeCity);

        } catch (UnsupportedEncodingException e) {
            logger.error("Error to encode house address",e);
            return new ServiceResult<BaiduMapLocation>(false,"Error to encode house address");
        }

        HttpClient httpClient = HttpClients.createDefault();
        StringBuilder sb = new StringBuilder(BAIDU_MAP_GEOCONV_API);
        sb.append("address=").append(encodeAddress).append("&")
                .append("city=").append(encodeCity).append("&")
                .append("output=json&")
                .append("ak=").append(BAIDU_MAP_KEY);
        System.out.println(sb.toString());
        HttpGet get = new HttpGet(sb.toString());
        try {
            HttpResponse response = httpClient.execute(get);
            if(response.getStatusLine().getStatusCode() != HttpStatus.SC_OK){
                return new ServiceResult<BaiduMapLocation>(false,"Can not get baidumap location");
            }

            String result = EntityUtils.toString(response.getEntity(),"UTF-8");
            JsonNode jsonNode = objectMapper.readTree(result);
            int status = jsonNode.get("status").asInt();
            if(status != 0){
                return new ServiceResult<BaiduMapLocation>(false,"Error to get map location for status:"+status);
            } else {
                BaiduMapLocation location = new BaiduMapLocation();
                JsonNode jsonLocation = jsonNode.get("result").get("location");
                location.setLongitude(jsonLocation.get("lng").asDouble());
                location.setLatitude(jsonLocation.get("lat").asDouble());
                return ServiceResult.of(location);
            }

        } catch (IOException e) {
            logger.error("Error to fetch baidumap api",e);
            return new ServiceResult<BaiduMapLocation>(false,"Error to fetch baidumap api");
        }
    }


}
