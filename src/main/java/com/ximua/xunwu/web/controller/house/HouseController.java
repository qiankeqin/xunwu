package com.ximua.xunwu.web.controller.house;

import com.ximua.xunwu.base.ApiResponse;
import com.ximua.xunwu.base.LoginUserUtil;
import com.ximua.xunwu.base.RentValueBlock;
import com.ximua.xunwu.entity.SupportAddress;
import com.ximua.xunwu.service.IUserService;
import com.ximua.xunwu.service.ServiceResult;
import com.ximua.xunwu.service.house.IAddressService;
import com.ximua.xunwu.service.house.IHouseService;
import com.ximua.xunwu.service.search.HouseBucketDTO;
import com.ximua.xunwu.service.search.ISearchService;
import com.ximua.xunwu.service.search.MapSearch;
import com.ximua.xunwu.web.dto.*;
import com.ximua.xunwu.web.controller.user.ServiceMultiResult;
import com.ximua.xunwu.web.form.RentSearch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.sql.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * created by qiankeqin
 */
@Controller
public class HouseController {

    @Autowired
    private IAddressService addressService;
    @Autowired
    private IHouseService houseService;
    @Autowired
    private IUserService userService;
    @Autowired
    private ISearchService searchService;

    /**
     * 自动补全接口
     * @param prefix
     * @return
     */
    @GetMapping("/rent/house/autocomplete")
    @ResponseBody
    public ApiResponse autocomplete(@RequestParam(value = "prefix") String prefix){
        if(prefix.isEmpty()){
            return ApiResponse.ofStatus(ApiResponse.Status.BAD_REQUEST);
        }
//        List<String> result = new ArrayList<>();
//        result.add("超级qiankeqin");
//        result.add("super千克勤");
        ServiceResult<List<String>> result = this.searchService.suggest(prefix);
        return ApiResponse.ofSuccess(result.getResult());
    }

    @GetMapping("/address/support/cities")
    //获取城市
    @ResponseBody
    public ApiResponse getSupportCities(){
        ServiceMultiResult<SupportAddressDTO> result = addressService.findAllCities();
        if(result.getResultSize() == 0){
            return ApiResponse.ofStatus(ApiResponse.Status.NOT_FOUND);
        }

        return ApiResponse.ofSuccess(result.getResult());
    }

    //获取区域
    @GetMapping("/address/support/regions")
    @ResponseBody
    public ApiResponse getSupportRegions(@RequestParam(name="city_name") String cityEnName){
        ServiceMultiResult<SupportAddressDTO> result = addressService.findAllRegionByCityName(cityEnName);
        if(result.getResultSize() == 0){
            return ApiResponse.ofStatus(ApiResponse.Status.NOT_FOUND);
        }
        return ApiResponse.ofSuccess(result.getResult());
    }

    //获取地铁线路
    @GetMapping("/address/support/subway/line")
    @ResponseBody
    public ApiResponse getSupportSubway(@RequestParam(name="city_name") String cityEnName){
        ServiceMultiResult<SubwayDTO> result = addressService.findAllSubwayByCityEnName(cityEnName);
        if(result.getResultSize()==0){
            return ApiResponse.ofStatus(ApiResponse.Status.NOT_FOUND);
        }
        return ApiResponse.ofSuccess(result.getResult());
    }

    //获取地铁站
    @GetMapping("/address/support/subway/station")
    @ResponseBody
    public ApiResponse getSupportSubwayStation(@RequestParam(name="subway_id") Long subwayId){
        System.out.println(subwayId);
        ServiceMultiResult<SubwayStationDTO> result = addressService.findAllSubwayStationBySubwayId(subwayId);
        if(result.getResultSize() == 0){
            return ApiResponse.ofStatus(ApiResponse.Status.NOT_FOUND);
        }
        return ApiResponse.ofSuccess(result.getResult());
    }

    /**
     * 租房信息查询
     */
    @GetMapping("/rent/house")
    public String rentHousePage(@ModelAttribute RentSearch rentSearch,
                                Model model, HttpSession session, RedirectAttributes redirectAttributes){
        //当前城市不能为空
        if(rentSearch.getCityEnName()==null){
            String cityEnNameInSession = (String) session.getAttribute("cityEnName");
            if(cityEnNameInSession == null){
                redirectAttributes.addAttribute("msg","must_chose_city");
                return "redirect:/index";
            } else {
                rentSearch.setCityEnName(cityEnNameInSession);
            }
        } else {
            session.setAttribute("cityEnName",rentSearch.getCityEnName());
        }


        ServiceResult<SupportAddressDTO> city = addressService.findCity(rentSearch.getCityEnName());
        if(!city.isSuccess()){
            redirectAttributes.addAttribute("msg","must_chose_city");
            return "redirect:/index";
        }
        model.addAttribute("currentCity",city.getResult());

        System.out.println(rentSearch.getCityEnName());

        ServiceMultiResult<SupportAddressDTO> addressResult = addressService.findAllRegionByCityName(rentSearch.getCityEnName());
        if(addressResult.getResult() == null || addressResult.getTotal()<1){
            redirectAttributes.addAttribute("msg","must_chose_city");
            return "redirect:/index";
        }

        //查询数据
        ServiceMultiResult<HouseDTO> houseDTOServiceMultiResult = houseService.query(rentSearch);
        model.addAttribute("total",houseDTOServiceMultiResult.getTotal());
        model.addAttribute("houses", houseDTOServiceMultiResult.getResult());
        //如果区域为空，匹配所有的区域
        if(rentSearch.getRegionEnName() == null){
            rentSearch.setRegionEnName("*");
        }
        model.addAttribute("searchBody",rentSearch);//为了回显，并于下次查询
        model.addAttribute("regions",addressResult.getResult());

        //设置区间属性
        model.addAttribute("priceBlocks", RentValueBlock.PRICE_BLOCK);
        model.addAttribute("areaBlocks",RentValueBlock.AREA_BLOCK);

        //保存范围的属性
        model.addAttribute("currentPriceBlock",RentValueBlock.matchPrice(rentSearch.getPriceBlock()));
        model.addAttribute("currentAreaBlock",RentValueBlock.matchArea(rentSearch.getAreaBlock()));

        return "rent-list";
    }

    @GetMapping("/rent/house/show/{id}")
    public String show(@PathVariable(value="id") Long houseId,Model model){
        if(houseId<0){
            return "404";
        }
        ServiceResult<HouseDTO> houseDTOServiceResult = houseService.findCompleteOne(houseId);
        if(!houseDTOServiceResult.isSuccess()){
            return "404";
        }
        HouseDTO houseDTO = houseDTOServiceResult.getResult();
        Map<SupportAddress.Level, SupportAddressDTO> addressMap = addressService.findCityAndRegion(houseDTO.getCityEnName(), houseDTO.getRegionEnName());
        SupportAddressDTO city = addressMap.get(SupportAddress.Level.CITY);
        SupportAddressDTO region = addressMap.get(SupportAddress.Level.REGION);
        model.addAttribute("city",city);
        model.addAttribute("region",region);

        ServiceResult<UserDTO> userDTOServiceResult = userService.findById(houseDTO.getAdminId());

        model.addAttribute("agent",userDTOServiceResult.getResult());
        model.addAttribute("house",houseDTO);

        //聚合信息，使用到ES，这里先空出来
        //显示同小区有多少套房子
        ServiceResult<Long> aggResult = searchService.aggregateDistrictHouse(city.getEnName(), region.getEnName(), houseDTO.getDistrict());
        model.addAttribute("houseCountInDistrict",aggResult.getResult());

        return "house-detail";
    }

    /**
     * 显示找房Map
     * @param cityEnName
     * @param model
     * @param session
     * @param redirectAttributes
     * @return
     */
    @GetMapping("/rent/house/map")
    public String rentMapPage(@RequestParam(value="cityEnName") String cityEnName,
                              Model model,
                              HttpSession session,
                              RedirectAttributes redirectAttributes){
        ServiceResult<SupportAddressDTO> city = addressService.findCity(cityEnName);
        if(!city.isSuccess()){
            redirectAttributes.addAttribute("msg","must_chose_city");
            return "redirect:/index";
        }else{
            session.setAttribute("cityName",cityEnName);
            model.addAttribute("city",city.getResult());
        }
        ServiceMultiResult<SupportAddressDTO> regions = addressService.findAllRegionByCityName(cityEnName);
        ServiceMultiResult<HouseBucketDTO> serviceResult = searchService.magAggregate(cityEnName);
        model.addAttribute("aggData",serviceResult.getResult());//共地图使用
        model.addAttribute("total",serviceResult.getTotal());
        model.addAttribute("regions",regions.getResult());
        return "rent-map";
    }

    /**
     * 地图上的搜索
     * @param mapSearch
     * @return
     */
    @GetMapping("/rent/house/map/houses")
    @ResponseBody
    public ApiResponse rentMapHouses(@ModelAttribute MapSearch mapSearch){
        if(mapSearch.getCityEnName() == null){
            return ApiResponse.ofMessage(HttpStatus.BAD_REQUEST.value(),"必须选择城市");
        }
        ServiceMultiResult<HouseDTO> serviceMultiResult = houseService.wholeMapQuery(mapSearch);
        ApiResponse response = ApiResponse.ofSuccess(serviceMultiResult.getResult());
        response.setMore(serviceMultiResult.getTotal()> (mapSearch.getStart()));
        return response;
    }
}
