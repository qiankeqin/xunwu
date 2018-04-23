package com.ximua.xunwu.service.house;

import com.ximua.xunwu.service.ServiceResult;
import com.ximua.xunwu.service.search.MapSearch;
import com.ximua.xunwu.web.controller.user.ServiceMultiResult;
import com.ximua.xunwu.web.dto.HouseDTO;
import com.ximua.xunwu.web.form.DatatableSearch;
import com.ximua.xunwu.web.form.HouseForm;
import com.ximua.xunwu.web.form.RentSearch;

/**
 * 房屋管理接口
 * created by qiankeqin
 */
public interface IHouseService {
    //新增房源信息
    ServiceResult<HouseDTO> save(HouseForm houseForm);
    //修改房源信息
    ServiceResult update(HouseForm houseForm);
    //查询房源信息
    ServiceMultiResult<HouseDTO> adminQuery(DatatableSearch searchBody);
    //查询房屋信息根据id
    ServiceResult<HouseDTO> findCompleteOne(Long id);

    //删除图片
    ServiceResult removePhoto(Long id);
    //修改封面
    ServiceResult updateCover(Long coverId, Long targetId);

    //移除标签
    ServiceResult removeTag(Long houseId, String tag);

    //新增标签
    ServiceResult addTag(Long houseId, String tag);

    //更新房源状态
    ServiceResult updateStatus(Long id,int status);

    //查询租房信息
    ServiceMultiResult<HouseDTO> query(RentSearch rentSearch);

    /**
     * 全地图搜索
     * @param mapSearch
     * @return
     */
    ServiceMultiResult<HouseDTO> wholeMapQuery(MapSearch mapSearch);
}
