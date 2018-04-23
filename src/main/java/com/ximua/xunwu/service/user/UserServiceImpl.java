package com.ximua.xunwu.service.user;

import com.ximua.xunwu.entity.Role;
import com.ximua.xunwu.entity.User;
import com.ximua.xunwu.repository.RoleRepository;
import com.ximua.xunwu.repository.UserRepository;
import com.ximua.xunwu.service.IUserService;
import com.ximua.xunwu.service.ServiceResult;
import com.ximua.xunwu.web.dto.UserDTO;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserServiceImpl implements IUserService
{
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private ModelMapper modelMapper;
    /**
     * 获取用户时，需要获取到对应到角色信息
     * @param userName
     * @return
     */
    @Override
    public User findUserByName(String userName) {
        User user = userRepository.findByName(userName);
        if(user == null){
            return null;
        }
        List<Role> roles = roleRepository.findRoleByUserId(user.getId());
        if(roles==null && roles.size()==0){
            throw new DisabledException("权限非法");
        }
        List<GrantedAuthority> authorities = new ArrayList<>();
        roles.forEach(role->authorities.add(new SimpleGrantedAuthority("ROLE_"+role.getName())));
        user.setAuthorityList(authorities);
        return user;
    }

    /**
     * 根据userId获取用户
     * @param loginUserId
     * @return
     */
    @Override
    public ServiceResult<UserDTO> findById(Long loginUserId) {
        User user = userRepository.findOne(loginUserId);
        if(user == null){
            return ServiceResult.notFound();
        }
        UserDTO userDTO = modelMapper.map(user,UserDTO.class);
        return ServiceResult.of(userDTO);
    }
}
