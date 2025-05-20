package com.abit8.financebot.service;

import com.abit8.financebot.entity.User;
import com.abit8.financebot.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> findByRemindersEnabledTrue() {
        return userRepository.findByRemindersEnabledTrue();
    }

    public User save(User user) {
        return userRepository.save(user);
    }
}