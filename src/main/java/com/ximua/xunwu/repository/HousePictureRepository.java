package com.ximua.xunwu.repository;

import com.ximua.xunwu.entity.HousePicture;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface HousePictureRepository extends CrudRepository<HousePicture,Long> {
    List<HousePicture> findAllByHouseId(Long id);
}
