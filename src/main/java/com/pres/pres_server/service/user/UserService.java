package com.pres.pres_server.service;

import com.pres.pres_server.domain.User;
import com.pres.pres_server.dto.SignupDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
//이미 가입된 사용자에 대한 CRUD
public interface UserService {

    void deleteUser(Long id);

    User getUser(Long id);

    List<User> listUsers();

    void updateUser(User user);
}
