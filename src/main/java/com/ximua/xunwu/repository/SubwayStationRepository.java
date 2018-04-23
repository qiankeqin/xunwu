package com.ximua.xunwu.repository;

import com.ximua.xunwu.entity.SubwayStation;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface SubwayStationRepository extends CrudRepository<SubwayStation,Long>{
    List<SubwayStation> findAllBySubwayId(Long subwayId);
}
