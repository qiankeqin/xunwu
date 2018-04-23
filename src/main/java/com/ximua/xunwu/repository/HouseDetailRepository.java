package com.ximua.xunwu.repository;

import com.ximua.xunwu.entity.HouseDetail;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface HouseDetailRepository extends CrudRepository<HouseDetail,Long> {
    HouseDetail findAllByHouseId(Long id);
    List<HouseDetail> findAllByHouseIdIn(List<Long> houseIds);
}
