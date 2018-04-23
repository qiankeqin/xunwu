package com.ximua.xunwu.repository;

import com.ximua.xunwu.entity.House;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

/**
 * PagingAndSortingRepository 排序分页接口
 * JpaSpecificationExecutor 指定字段查询接口
 */
public interface HouseRepository extends PagingAndSortingRepository<House,Long>,JpaSpecificationExecutor<House> {

    //自定义的JPA接口
    @Modifying
    @Query("update House as house set house.cover=:cover where house.id = :id")
    void updateCover(@Param(value="id") Long id, @Param(value="cover") String cover);

    @Modifying
    @Query("update House as house set house.status=:status where house.id=:id")
    void updateStatus(@Param(value="id") Long id, @Param(value="status") int status);
}
