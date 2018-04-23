package com.ximua.xunwu.repository;

import com.ximua.xunwu.entity.Role;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 角色数据dao
 */
public interface RoleRepository extends CrudRepository<Role,Long>{
    List<Role> findRoleByUserId(Long userId);
}
