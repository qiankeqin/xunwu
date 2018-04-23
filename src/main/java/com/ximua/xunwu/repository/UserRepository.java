package com.ximua.xunwu.repository;

import com.ximua.xunwu.entity.User;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * CrudRepository：参数1：entity，参数2：主键类型
 */
public interface UserRepository extends CrudRepository<User,Long> {
    User findByName(String userName);
}
