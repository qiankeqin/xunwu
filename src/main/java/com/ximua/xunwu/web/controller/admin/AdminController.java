package com.ximua.xunwu.web.controller.admin;

import com.google.gson.Gson;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.ximua.xunwu.base.ApiDataTableResponse;
import com.ximua.xunwu.base.ApiResponse;
import com.ximua.xunwu.base.HouseOperation;
import com.ximua.xunwu.base.HouseStatus;
import com.ximua.xunwu.entity.HouseDetail;
import com.ximua.xunwu.entity.SupportAddress;
import com.ximua.xunwu.service.ServiceResult;
import com.ximua.xunwu.service.house.IAddressService;
import com.ximua.xunwu.service.house.IHouseService;
import com.ximua.xunwu.service.house.IQiNiuService;
import com.ximua.xunwu.web.controller.user.ServiceMultiResult;
import com.ximua.xunwu.web.dto.*;
import com.ximua.xunwu.web.form.DatatableSearch;
import com.ximua.xunwu.web.form.HouseForm;
import org.assertj.core.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@Controller
public class AdminController {

    @Autowired
    private IQiNiuService qiNiuService;

    @Autowired
    private IAddressService addressService;

    @Autowired
    private IHouseService houseService;

    @Autowired
    private Gson gson;

    @GetMapping("/admin/center")
    public String adminCenterPage(){
        return "admin/center";
    }

    @GetMapping("/admin/welcome")
    public String welcomePage(){
        return "admin/welcome";
    }

    //重新映射登录页面入口的页面
    @GetMapping("/admin/login")
    public String adminLoginPage(){
        return "admin/login";
    }

    //现实新增房源
    @GetMapping("/admin/add/house")
    public String addHousePage(){
        return "admin/house-add";
    }

    //显示房源列表
    @GetMapping("/admin/house/list")
    public String houseListPage(){
        return "admin/house-list";
    }

    /**
     * 编辑房源接口
     */
    @PostMapping("/admin/house/edit")
    @ResponseBody
    public ApiResponse saveHouse(@Valid @ModelAttribute HouseForm houseForm,BindingResult bindingResult){
        if(bindingResult.hasErrors()){
            return new ApiResponse(HttpStatus.BAD_REQUEST.value(),bindingResult.getAllErrors().get(0).getDefaultMessage(),null);
        }
        Map<SupportAddress.Level, SupportAddressDTO> addressDTOMap = addressService.findCityAndRegion(houseForm.getCityEnName(), houseForm.getRegionEnName());
        if(addressDTOMap.keySet().size()!= 2){
            return ApiResponse.ofStatus(ApiResponse.Status.NOT_VALID_PARAM);
        }
        System.out.println(houseForm);
        ServiceResult result = houseService.update(houseForm);
        if(result.isSuccess()){
            return ApiResponse.ofSuccess(null);
        }
        ApiResponse apiResponse = ApiResponse.ofStatus(ApiResponse.Status.BAD_REQUEST);
        apiResponse.setMessage(result.getMessage());
        return apiResponse;
    }



    //显示编辑页面
    @GetMapping("/admin/house/edit")
    public String houseEditPage(@RequestParam(value="id") Long id, Model model){
        if(id ==null || id<1){
            return "404";
        }
        ServiceResult<HouseDTO> serviceResult = houseService.findCompleteOne(id);
        if(!serviceResult.isSuccess()){
            return "404";
        }
        HouseDTO result = serviceResult.getResult();
        model.addAttribute("house",result);

        Map<SupportAddress.Level, SupportAddressDTO> addressMap = addressService.findCityAndRegion(result.getCityEnName(), result.getRegionEnName());
        model.addAttribute("city",addressMap.get(SupportAddress.Level.CITY));
        model.addAttribute("region",addressMap.get(SupportAddress.Level.REGION));

        HouseDetailDTO detailDTO = result.getHouseDetail();
        ServiceResult<SubwayDTO> subwayDTOServiceResult = addressService.findAllSubway(detailDTO.getSubwayLineId());
        if(subwayDTOServiceResult.isSuccess()){
            model.addAttribute("subway",subwayDTOServiceResult.getResult());
        }
        ServiceResult<SubwayStationDTO> subwayStationDTOServiceResult = addressService.findAllSubwayStation(detailDTO.getSubwayStationId());
        System.out.println(subwayStationDTOServiceResult);
        if(subwayStationDTOServiceResult.isSuccess()){
            model.addAttribute("station",subwayStationDTOServiceResult.getResult());
        }

        return "admin/house-edit";
    }

    @PostMapping("/admin/houses")
    @ResponseBody
    public ApiDataTableResponse houses(@ModelAttribute DatatableSearch searchBody){
        ServiceMultiResult<HouseDTO> result = houseService.adminQuery(searchBody);
        ApiDataTableResponse response = new ApiDataTableResponse(ApiResponse.Status.SUCCESS);

        response.setData(result.getResult());
        response.setRecordsTotal(result.getTotal());
        response.setRecordsFiltered(result.getTotal());
        //用于回显，必须赋值
        response.setDraw(searchBody.getDraw());
        return response;
    }

    //上次图片
    @PostMapping(value="/admin/upload/photo",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ApiResponse uploadPhoto(@RequestParam("file")MultipartFile file){
        if(file.isEmpty()){
            return ApiResponse.ofStatus(ApiResponse.Status.NOT_VALID_PARAM);
        }
        String fileName = file.getOriginalFilename();
        try {
            InputStream inputStream = file.getInputStream();
            Response response = qiNiuService.uploadFile(inputStream);
            if(response.isOK()){
                QiNiuPutRet qiNiuPutRet = gson.fromJson(response.bodyString(), QiNiuPutRet.class);
                return ApiResponse.ofSuccess(qiNiuPutRet);
            }else{
                return ApiResponse.ofMessage(response.statusCode,response.getInfo());
            }

        } catch(QiniuException qiniuEx) {
            Response response = qiniuEx.response;
            try {
                return ApiResponse.ofMessage(response.statusCode,response.bodyString());
            } catch (QiniuException e) {
                return ApiResponse.ofStatus(ApiResponse.Status.INTERNAL_SERVER_ERROR);
            }
        } catch (IOException e) {
            return ApiResponse.ofStatus(ApiResponse.Status.INTERNAL_SERVER_ERROR);
        }
        //上传文件到本地
//        File target = new File("/Users/qiankeqin/Documents/workspace/xunwu/tmp/"+fileName);
//        try{
//            //上传文件到本地
//            file.transferTo(target);
//        } catch(Exception ex) {
//            ex.printStackTrace();
//            return ApiResponse.ofStatus(ApiResponse.Status.INTERNAL_SERVER_ERROR);
//        }
//        return ApiResponse.ofSuccess(null);
    }

    /**
     * 新增房源方法
     * @Valid表单校验，校验结果放到BindingResult中
     * @param houseForm
     * @param bindingResult
     * @return
     */
    @PostMapping("/admin/add/house")
    @ResponseBody
    public ApiResponse addHouse(@Valid @ModelAttribute("form-house-add") HouseForm houseForm, BindingResult bindingResult){
        System.out.println(houseForm);

        //首先校验表单
        if(bindingResult.hasErrors()){
            return new ApiResponse(HttpStatus.BAD_REQUEST.value(),bindingResult.getAllErrors().get(0).getDefaultMessage(),null);
        }

        if(houseForm.getPhotos() == null || houseForm.getCover() == null){
            return ApiResponse.ofMessage(HttpStatus.BAD_REQUEST.value(),"必须上传图片");
        }

        Map<SupportAddress.Level, SupportAddressDTO> addressDTOMap = addressService.findCityAndRegion(houseForm.getCityEnName(), houseForm.getRegionEnName());
        if(addressDTOMap.keySet().size()!=2){
            //如果没有包含一个城市和一个地区，那么就是有问题的
            return ApiResponse.ofStatus(ApiResponse.Status.NOT_VALID_PARAM);
        }
        //调用保存方法
        ServiceResult<HouseDTO> result = houseService.save(houseForm);
        if(result.isSuccess()){
            return ApiResponse.ofSuccess(result.getResult());
        }

        return ApiResponse.ofStatus(ApiResponse.Status.NOT_VALID_PARAM);
    }

    /**
     * 删除图片
     */
    @DeleteMapping("/admin/house/photo")
    @ResponseBody
    public ApiResponse removeHousePhoto(@RequestParam(value="id") Long id){
        ServiceResult result = this.houseService.removePhoto(id);
        if(result.isSuccess()){
            return ApiResponse.ofStatus(ApiResponse.Status.SUCCESS);
        }else{
            return ApiResponse.ofMessage(HttpStatus.BAD_REQUEST.value(),result.getMessage());
        }
    }

    /**
     * 修改封面
     * @param coverId
     * @param targetId
     * @return
     */
    @PostMapping("/admin/house/cover")
    @ResponseBody
    public ApiResponse updateCover(@RequestParam(value="cover_id") Long coverId,
                                   @RequestParam(value="target_id") Long targetId){
        ServiceResult result = this.houseService.updateCover(coverId,targetId);
        if(result.isSuccess()){
            return ApiResponse.ofStatus(ApiResponse.Status.SUCCESS);
        } else {
            return ApiResponse.ofMessage(HttpStatus.BAD_REQUEST.value(),result.getMessage());
        }
    }


    /**
     * 新增标签接口
     * @param houseId
     * @param tag
     * @return
     */
    @PostMapping("/admin/house/tag")
    @ResponseBody
    public ApiResponse addHouseTag(@RequestParam(value="house_id") Long houseId,
                                   @RequestParam(value="tag") String tag){
        if(houseId<1 || Strings.isNullOrEmpty(tag)){
            return ApiResponse.ofStatus(ApiResponse.Status.BAD_REQUEST);
        }
        ServiceResult result = houseService.addTag(houseId,tag);
        if(result.isSuccess()){
            return ApiResponse.ofStatus(ApiResponse.Status.SUCCESS);
        }else{
            return ApiResponse.ofMessage(HttpStatus.BAD_REQUEST.value(),result.getMessage());
        }
    }

    /**
     * 移除标签
     * @param houseId
     * @param tag
     * @return
     */
    @DeleteMapping("/admin/house/tag")
    @ResponseBody
    public ApiResponse deleteHouseTag(@RequestParam(value="house_id") Long houseId,
                                        @RequestParam(value="tag") String tag){
        if(houseId<1 || Strings.isNullOrEmpty(tag)){
            return ApiResponse.ofStatus(ApiResponse.Status.BAD_REQUEST);
        }
        ServiceResult result = houseService.removeTag(houseId,tag);
        if(result.isSuccess()){
            return ApiResponse.ofStatus(ApiResponse.Status.SUCCESS);
        }else{
            return ApiResponse.ofMessage(HttpStatus.BAD_REQUEST.value(),result.getMessage());
        }
    }

    /**
     * 审核接口
     * @param id
     * @param operation
     * @return
     */
    @PutMapping("/admin/house/operate/{id}/{operation}")
    @ResponseBody
    public ApiResponse operateHouse(@PathVariable(value="id") Long id,
                                    @PathVariable(value="operation") int operation){
        if(id <= 0){
            return ApiResponse.ofStatus(ApiResponse.Status.NOT_VALID_PARAM);
        }

        ServiceResult result = null;
        switch(operation){
            case HouseOperation.PASS:
                result = this.houseService.updateStatus(id, HouseStatus.PASSES.getValue());
                break;
            case HouseOperation.PULL_OUT:
                result = this.houseService.updateStatus(id,HouseStatus.NOT_AUDITED.getValue());
                break;
            case HouseOperation.DELETE:
                result = this.houseService.updateStatus(id,HouseStatus.DELETED.getValue());
                break;
            case HouseOperation.RENT:
                result = this.houseService.updateStatus(id,HouseStatus.RENTED.getValue());
                break;
            default:
                return ApiResponse.ofStatus(ApiResponse.Status.BAD_REQUEST);
        }
        if(result.isSuccess()){
            return ApiResponse.ofSuccess(null);
        }
        return ApiResponse.ofMessage(HttpStatus.BAD_REQUEST.value(),result.getMessage());
    }

}
