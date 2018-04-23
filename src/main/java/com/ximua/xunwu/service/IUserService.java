package com.ximua.xunwu.service;

import com.ximua.xunwu.entity.User;
import com.ximua.xunwu.web.dto.UserDTO;

public interface IUserService {
    User findUserByName(String userName);

    ServiceResult<UserDTO> findById(Long loginUserId);
}
