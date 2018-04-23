package com.ximua.xunwu.repository;

import com.ximua.xunwu.entity.SupportAddress;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

/**
 * created by qiankeqin
 */
public interface SupportAddressRepository extends CrudRepository<SupportAddress, Long> {
    /**
     * 根据级别获取所有对应行政级别的信息
     */
    List<SupportAddress> findAllByLevel(String level);

    /**
     * 获取所有新政级别信息
     * @param level
     * @param belongTo
     * @return
     */
    List<SupportAddress> findAllByLevelAndBelongTo(String level,String belongTo);

    SupportAddress findByEnNameAndLevel(String enName,String level);

    SupportAddress findByEnNameAndBelongTo(String enName,String belongTo);

}
