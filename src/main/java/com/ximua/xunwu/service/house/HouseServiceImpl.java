package com.ximua.xunwu.service.house;

import com.google.common.collect.Maps;
import com.qiniu.http.Response;
import com.ximua.xunwu.base.HouseSort;
import com.ximua.xunwu.base.HouseStatus;
import com.ximua.xunwu.base.LoginUserUtil;
import com.ximua.xunwu.entity.*;
import com.ximua.xunwu.repository.*;
import com.ximua.xunwu.service.ServiceResult;
import com.ximua.xunwu.service.search.ISearchService;
import com.ximua.xunwu.service.search.MapSearch;
import com.ximua.xunwu.web.controller.user.ServiceMultiResult;
import com.ximua.xunwu.web.dto.HouseDTO;
import com.ximua.xunwu.web.dto.HouseDetailDTO;
import com.ximua.xunwu.web.dto.HousePictureDTO;
import com.ximua.xunwu.web.form.DatatableSearch;
import com.ximua.xunwu.web.form.HouseForm;
import com.ximua.xunwu.web.form.PhotoForm;
import com.ximua.xunwu.web.form.RentSearch;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.transaction.Transactional;
import java.util.*;

/**
 * 房屋服务实现
 * created by qiankeqin
 */
@Service
public class  HouseServiceImpl implements IHouseService {

    @Autowired
    private HouseRepository houseRepository;
    @Autowired
    private ModelMapper modelMapper;
    @Autowired
    private SubwayRepository subwayRepository;
    @Autowired
    private SubwayStationRepository subwayStationRepository;
    @Autowired
    private HouseDetailRepository houseDetailRepository;
    @Autowired
    private HousePictureRepository housePictureRepository;
    @Autowired
    private HouseTagRepository houseTagRepository;
    @Autowired
    private IQiNiuService qiNiuService;
    @Value("${qiniu.cdn.prefix}")
    private String cdnPrefix;

    @Autowired
    private ISearchService searchService;

    /**
     * 保存房源信息
     * @param houseForm
     * @return
     */
    @Override
    public ServiceResult<HouseDTO> save(HouseForm houseForm) {
        HouseDetail houseDetail = new HouseDetail();
        ServiceResult<HouseDTO> detailValidationResult = wrapperDetailInfo(houseDetail,houseForm);
        if(detailValidationResult!=null){
            return detailValidationResult;
        }

        House house = new House();
        //将HouseForm中的部分字段映射到House实体中
        modelMapper.map(houseForm,house);
        house.setCreateTime(new Date());
        house.setLastUpdateTime(new Date());
        house.setAdminId(LoginUserUtil.getLoginUserId());
        //保存房屋信息
        house = houseRepository.save(house);

        houseDetail.setHouseId(house.getId());
        //保存房屋详细信息
        houseDetail = houseDetailRepository.save(houseDetail);

        List<HousePicture> pictures = generatePictures(houseForm, house.getId());
        //保存房屋图片
        Iterable<HousePicture> housePictures = housePictureRepository.save(pictures);

        HouseDTO houseDTO = modelMapper.map(house,HouseDTO.class);
        HouseDetailDTO houseDetailDTO = modelMapper.map(houseDetail,HouseDetailDTO.class);
        houseDTO.setHouseDetail(houseDetailDTO);
        List<HousePictureDTO> housePictureDTOS = new ArrayList<>();
        housePictures.forEach(housePicture->{
            housePictureDTOS.add(modelMapper.map(housePicture,HousePictureDTO.class));
        });
        houseDTO.setPictures(housePictureDTOS);
        houseDTO.setCover(this.cdnPrefix+houseDTO.getCover());

        List<String> tags = houseForm.getTags();
        if(tags!=null || !tags.isEmpty()){
            List<HouseTag> houseTags = new ArrayList<>();
            for (String tag : tags) {
                houseTags.add(new HouseTag(house.getId(),tag));
            }
            //保存图片信息
            houseTagRepository.save(houseTags);
            houseDTO.setTags(tags);
        }

        return new ServiceResult<HouseDTO>(true,null,houseDTO);
    }

    /**
     * 更新
     * @param houseForm
     * @return
     */
    @Override
    @Transactional
    public ServiceResult update(HouseForm houseForm) {
        House house = this.houseRepository.findOne(houseForm.getId());
        if(house == null){
            return ServiceResult.notFound();
        }
        HouseDetail detail = houseDetailRepository.findAllByHouseId(house.getId());
        if(detail == null){
            return ServiceResult.notFound();
        }
        ServiceResult wrapperResult = wrapperDetailInfo(detail, houseForm);
        if(wrapperResult != null){
            return wrapperResult;
        }

        houseDetailRepository.save(detail);

        List<HousePicture> pictures = generatePictures(houseForm,houseForm.getId());
        housePictureRepository.save(pictures);

        if(houseForm.getCover() == null){
            houseForm.setCover(house.getCover());
        }
        modelMapper.map(houseForm,house);
        house.setLastUpdateTime(new Date());
        houseRepository.save(house);

        //如果是刚上架的状态，才建立索引
        if(house.getStatus() == HouseStatus.PASSES.getValue()){
            searchService.index(house.getId());
        }

        return ServiceResult.success();
    }

    /**
     * 根据查询条件返回House对象
     * @param searchBody
     * @return
     */
    @Override
    public ServiceMultiResult<HouseDTO> adminQuery(DatatableSearch searchBody) {
        List<HouseDTO> houseDTOS = new ArrayList<>();
        //排序方法需要用到的实体
        Sort sort = new Sort(Sort.Direction.fromString(searchBody.getDirection()),searchBody.getOrderBy());
        int page = searchBody.getStart()/searchBody.getLength();
        Pageable pageable = new PageRequest(page,searchBody.getLength(),sort);

        //获取查询条件
        Specification<House> specification = (root,criteria,criteriaBuild)->{
            Predicate predicate = criteriaBuild.equal(root.get("adminId"),LoginUserUtil.getLoginUserId());
            predicate = criteriaBuild.and(predicate,criteriaBuild.notEqual(root.get("status"), HouseStatus.DELETED.getValue()));//不包含逻辑删除的数据
            if(searchBody.getCity()!=null){
                predicate = criteriaBuild.and(predicate,criteriaBuild.equal(root.get("cityEnName"),searchBody.getCity()));
            }
            if(searchBody.getStatus()!=null){
                predicate = criteriaBuild.and(predicate,criteriaBuild.equal(root.get("status"),searchBody.getStatus()));
            }
            if(searchBody.getCreateTimeMin()!=null){
                predicate = criteriaBuild.and(predicate,criteriaBuild.greaterThanOrEqualTo(root.get("createTime"),searchBody.getCreateTimeMin()));
            }
            if(searchBody.getCreateTimeMax()!=null){
                predicate = criteriaBuild.and(predicate,criteriaBuild.lessThanOrEqualTo(root.get("createTime"),searchBody.getCreateTimeMax()));
            }
            if(searchBody.getTitle()!=null && !searchBody.getTitle().isEmpty()){
                predicate = criteriaBuild.and(predicate,criteriaBuild.like(root.get("title"),"%"+searchBody.getTitle()+"%"));
            }
            return predicate;
        };

        Page<House> houses = houseRepository.findAll(specification,pageable);

        //查询方式，PagingAndSortingRepository的findAll方法
        //Page<House> houses = houseRepository.findAll(pageable);

        //Iterable<House> houses = houseRepository.findAll();
        houses.forEach(house->{
            HouseDTO houseDTO = modelMapper.map(house,HouseDTO.class);
            houseDTO.setCover(this.cdnPrefix+house.getCover());
            houseDTOS.add(houseDTO);
        });

        return new ServiceMultiResult<>(houses.getTotalElements(),houseDTOS);
    }

    /**
     * 查询完整的房屋信息
     * @param id
     * @return
     */
    @Override
    public ServiceResult<HouseDTO> findCompleteOne(Long id) {
        House house = houseRepository.findOne(id);
        if(house == null){
            return ServiceResult.notFound();
        }

        //查询所有的房屋信息
        HouseDetail detail = houseDetailRepository.findAllByHouseId(id);
        List<HousePicture> pictures = housePictureRepository.findAllByHouseId(id);

        HouseDetailDTO houseDetailDTO = modelMapper.map(detail,HouseDetailDTO.class);
        List<HousePictureDTO> housePictureDTOS = new ArrayList<>();
        pictures.forEach(housePicture -> {
            housePictureDTOS.add(modelMapper.map(housePicture,HousePictureDTO.class));
        });

        List<HouseTag> tags = houseTagRepository.findAllByHouseId(id);
        List<String> tagList = new ArrayList<>();
        tags.forEach(tag -> {
            tagList.add(tag.getName());
        });
        HouseDTO result = modelMapper.map(house,HouseDTO.class);
        result.setTags(tagList);
        result.setPictures(housePictureDTOS);
        result.setHouseDetail(houseDetailDTO);

        ServiceResult<HouseDTO> serviceResult = ServiceResult.of(result);
        return serviceResult;
    }

    /**
     * 删除图片
     * @param id
     * @return
     */
    @Override
    public ServiceResult removePhoto(Long id) {
        HousePicture picture = housePictureRepository.findOne(id);
        if(picture == null){
            return ServiceResult.notFound();
        }
        //移除七牛云上的图片

        try{
            Response response = this.qiNiuService.delete(picture.getPath());
            if(response.isOK()){
                housePictureRepository.delete(id);
                return ServiceResult.success();
            }else{
                return new ServiceResult(false,response.error);
            }
        }catch(Exception e){
            e.printStackTrace();
            return new ServiceResult(false,e.getMessage());
        }
    }

    /**
     * 修改封面
     * @param coverId
     * @param targetId
     * @return
     */
    @Override
    @Transactional
    public ServiceResult updateCover(Long coverId, Long targetId) {
        HousePicture cover = housePictureRepository.findOne(coverId);
        if(cover==null){
            return ServiceResult.notFound();
        }
        //使用自定义的JPA接口
        houseRepository.updateCover(targetId,cover.getPath());
        return ServiceResult.success();
    }

    @Override
    @Transactional
    public ServiceResult addTag(Long houseId, String tag) {
        House house = houseRepository.findOne(houseId);
        if (house == null) {
            return ServiceResult.notFound();
        }

        HouseTag houseTag = houseTagRepository.findByNameAndHouseId(tag, houseId);
        if (houseTag != null) {
            return new ServiceResult(false, "标签已存在");
        }

        houseTagRepository.save(new HouseTag(houseId, tag));
        return ServiceResult.success();
    }

    @Override
    @Transactional
    public ServiceResult updateStatus(Long id, int status) {
        House house = houseRepository.findOne(id);
        if(house == null){
            return ServiceResult.notFound();
        }

        //判断那些状态可以进行改变
        if(house.getStatus()==status){
            return new ServiceResult(false,"状态并没有发生变化！");
        }
        if(house.getStatus() == HouseStatus.RENTED.getValue()){
            return new ServiceResult(false,"已出租的房屋不允许修改状态！");
        }
        if(house.getStatus()==HouseStatus.DELETED.getValue()){
            return new ServiceResult(false,"已删除的资源不允许修改状态！");
        }
        houseRepository.updateStatus(id,status);

        //只有在上架的状态时，才进行新增索引，其他状态时，删除索引
        if(status == HouseStatus.PASSES.getValue()){
            searchService.index(id);
        }else{
            searchService.remove(id);
        }
        return ServiceResult.success();
    }

    /**
     * 查询租房房源信息
     * @param rentSearch
     * @return
     */
    @Override
    public ServiceMultiResult<HouseDTO> query(RentSearch rentSearch) {
        if(rentSearch.getKeywords()!=null && !rentSearch.getKeywords().isEmpty()){
            //走SearchService到query进行查询
            ServiceMultiResult<Long> serviceResult = searchService.query(rentSearch);
            if(serviceResult.getTotal() == 0){
                return new ServiceMultiResult<>(0,new ArrayList<>());
            }
            return new ServiceMultiResult<>(serviceResult.getTotal(),wrapperHouseResult(serviceResult.getResult()));
        }
        return simpleQuery(rentSearch);
    }

    /**
     * 全地图搜索
     * @param mapSearch
     * @return
     */
    @Override
    public ServiceMultiResult<HouseDTO> wholeMapQuery(MapSearch mapSearch) {
        ServiceMultiResult<Long> serviceResult = searchService.mapQuery(mapSearch.getCityEnName(), mapSearch.getOrderBy(), mapSearch.getOrderDirection(), mapSearch.getStart(), mapSearch.getSize());
        if(serviceResult.getTotal() == 0){
            return new ServiceMultiResult<>(0,new ArrayList<>());
        }
        List<HouseDTO> houses = wrapperHouseResult(serviceResult.getResult());
        return new ServiceMultiResult<HouseDTO>(serviceResult.getTotal(),houses);
    }

    /**
     * 根据地图边界查找房源
     * @param mapSearch
     * @return
     */
    @Override
    public ServiceMultiResult<HouseDTO> boundMapQuery(MapSearch mapSearch) {
        ServiceMultiResult<Long> serviceMultiResult = searchService.mapQuery(mapSearch);
        if(serviceMultiResult.getTotal() == 0){
            return new ServiceMultiResult<>(0,new ArrayList<>());
        }

        List<HouseDTO> houses = wrapperHouseResult(serviceMultiResult.getResult());
        return new ServiceMultiResult<>(serviceMultiResult.getTotal(),houses);
    }

    /**
     * 普通查询（非ES查询）
     * @param rentSearch
     * @return
     */
    private ServiceMultiResult<HouseDTO> simpleQuery(RentSearch rentSearch) {

//        Sort sort = new Sort(Sort.Direction.DESC,"lastUpdateTime");//默认排序
        Sort sort = HouseSort.generateSort(rentSearch.getOrderBy(),rentSearch.getOrderDirection());
        int page = rentSearch.getStart()/rentSearch.getSize();
        Pageable pageable = new PageRequest(page,rentSearch.getSize(),sort);
        //自定义查询器，只显示审核通过的数，以及筛选的城市下的数据
        Specification<House> specification = (root,criteriaQuery,criteriaBuilder)->{
            Predicate predicate = criteriaBuilder.equal(root.get("status"),HouseStatus.PASSES.getValue());
            predicate = criteriaBuilder.and(predicate,criteriaBuilder.equal(root.get("cityEnName"),rentSearch.getCityEnName()));
            if(HouseSort.DISTANCE_TO_SUBWAY_KEY.equals(rentSearch.getOrderBy())){
                predicate = criteriaBuilder.and(predicate,criteriaBuilder.gt(root.get(HouseSort.DISTANCE_TO_SUBWAY_KEY),-1));
            }
            return predicate;
        };

        Page<House> houses = houseRepository.findAll(specification,pageable);
        List<HouseDTO> houseDTOS = new ArrayList<>();

        List<Long> houseIds = new ArrayList<>();
        Map<Long,HouseDTO> idToHouseMap = Maps.newHashMap();
        houses.forEach(house ->{
            HouseDTO houseDTO = modelMapper.map(house,HouseDTO.class);
            System.out.println(house);
            houseDTO.setCover(this.cdnPrefix + house.getCover());
//            HouseDetail houseDetail = houseDetailRepository.findAllByHouseId(house.getId());
//            HouseDetailDTO houseDetailDTO = modelMapper.map(houseDetail,HouseDetailDTO.class);
//            houseDTO.setHouseDetail(houseDetailDTO);
            houseDTOS.add(houseDTO);


            houseIds.add(house.getId());
            idToHouseMap.put(house.getId(),houseDTO);
        });
        System.out.println(houses);
        wrapperHouseList(houseIds,idToHouseMap);
        return new ServiceMultiResult<>(houses.getTotalElements(),houseDTOS);
    }

    /**
     * 根据houseId集合，包装成HouseDTO类
     * @param houseIds
     * @return
     */
    private List<HouseDTO> wrapperHouseResult(List<Long> houseIds){
        List<HouseDTO> houseDTOS = new ArrayList<>();
        Map<Long,HouseDTO> idToHouseMap = new HashMap<>();
        Iterable<House> houses = houseRepository.findAll(houseIds);
        houses.forEach(house -> {
            HouseDTO houseDTO = modelMapper.map(house,HouseDTO.class);
            houseDTO.setCover(this.cdnPrefix + house.getCover());
            idToHouseMap.put(house.getId(),houseDTO);
        });
        wrapperHouseList(houseIds,idToHouseMap);
        //矫正顺序，保证ES查出来的顺序和Mysql的是一致的
        for(Long houseId : houseIds){
            houseDTOS.add(idToHouseMap.get(houseId));
        }
        return houseDTOS;
    }

    /**
     * 包装House类，添加HouseDetail，HouseTag等信息
     * @param houseIds
     * @param idToHouseMap
     */
    private void wrapperHouseList(List<Long> houseIds,Map<Long,HouseDTO> idToHouseMap){
        List<HouseDetail> houseDetails = houseDetailRepository.findAllByHouseIdIn(houseIds);
        List<HouseDetailDTO> houseDetailDTOS = new ArrayList<>();
        houseDetails.forEach(
                houseDetail -> {
                    HouseDTO houseDTO = idToHouseMap.get(houseDetail.getHouseId());
                    HouseDetailDTO houseDetailDTO = modelMapper.map(houseDetail,HouseDetailDTO.class);
                    houseDTO.setHouseDetail(houseDetailDTO);
                }
        );

        //渲染标签
        List<HouseTag> houseTags = houseTagRepository.findAllByHouseIdIn(houseIds);
        houseTags.forEach(tag->{
            HouseDTO houseDTO = idToHouseMap.get(tag.getHouseId());
            houseDTO.getTags().add(tag.getName());
        });


    }

    @Override
    @Transactional
    public ServiceResult removeTag(Long houseId, String tag) {
        House house = houseRepository.findOne(houseId);
        if (house == null) {
            return ServiceResult.notFound();
        }

        HouseTag houseTag = houseTagRepository.findByNameAndHouseId(tag, houseId);
        if (houseTag == null) {
            return new ServiceResult(false, "标签不存在");
        }

        houseTagRepository.delete(houseTag.getId());
        return ServiceResult.success();
    }


    /**
     * 包装subway实体
     * @param houseDetail
     * @param houseForm
     */
    private ServiceResult<HouseDTO> wrapperDetailInfo(HouseDetail houseDetail,HouseForm houseForm){
        //校验地铁线路是否存在
        Subway subway = subwayRepository.findOne(houseForm.getSubwayLineId());
        if(subway == null){
            return new ServiceResult<>(false,"Not valid subway line!");
        }

        //校验地铁站是否存在,并判断地铁线路和地铁站是否匹配
        SubwayStation subwayStation = subwayStationRepository.findOne(houseForm.getSubwayStationId());
        if(subwayStation == null || subway.getId()!=subwayStation.getSubwayId()){
            return new ServiceResult<>(false,"Not valid subway station!");
        }

        //包装HouseDetail对象
        houseDetail.setSubwayLineId(subway.getId());
        houseDetail.setSubwayLineName(subway.getName());
        houseDetail.setSubwayStationId(subwayStation.getId());
        houseDetail.setSubwayStationName(subwayStation.getName());

        houseDetail.setDescription(houseForm.getDescription());
        houseDetail.setDetailAddress(houseForm.getDetailAddress());
        houseDetail.setLayoutDesc(houseForm.getLayoutDesc());
        houseDetail.setRentWay(houseForm.getRentWay());
        houseDetail.setRoundService(houseForm.getRoundService());
        houseDetail.setTraffic(houseForm.getTraffic());

        return null;
    }


    /**
     * 获取房屋图片资源
     * @return
     */
    private List<HousePicture> generatePictures(HouseForm houseForm,Long houseId){
        List<HousePicture> pictures = new ArrayList<>();
        if(houseForm.getPhotos()==null || houseForm.getPhotos().isEmpty()){
            return pictures;
        }

        for(PhotoForm photoForm:houseForm.getPhotos()){
            HousePicture housePicture = new HousePicture();
            housePicture.setHouseId(houseId);
            housePicture.setCdnPrefix(cdnPrefix);
            housePicture.setPath(photoForm.getPath());
            housePicture.setWidth(photoForm.getWidth());
            housePicture.setHeight(photoForm.getHeight());
            pictures.add(housePicture);
        }
        return pictures;
    }
}
