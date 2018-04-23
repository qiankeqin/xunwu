package com.ximua.xunwu.base;

import com.ximua.xunwu.entity.User;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 登录用户工具类
 */
public class LoginUserUtil {

    /*
    * 加载当前登录用户
     */
    public static User load(){
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if(principal != null && principal instanceof User){
            return (User) principal;
        }
        return null;
    }

    /**
     * 获取用户Id
     * @return
     */
    public static Long getLoginUserId(){
        User user = load();
        if(user != null){
            return user.getId();
        }
        return -1L;
    }
}
