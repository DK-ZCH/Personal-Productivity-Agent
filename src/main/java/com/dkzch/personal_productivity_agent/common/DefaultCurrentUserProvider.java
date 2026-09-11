package com.dkzch.personal_productivity_agent.common;

import com.dkzch.personal_productivity_agent.model.entity.User;
import com.dkzch.personal_productivity_agent.repository.UserRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;

//V1 默认实现：返回固定测试用户 id=1。
@Component
@Primary
public class DefaultCurrentUserProvider implements CurrentUserProvider {

    private static final Logger log = LoggerFactory.getLogger(DefaultCurrentUserProvider.class);

    private static final Long DEFAULT_USER_ID = 1L;
    private static final String DEFAULT_USER_NAME = "demo";

    private final UserRepository userRepository;

    public DefaultCurrentUserProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Long getCurrentUserId() {
        return DEFAULT_USER_ID;
    }

    //应用启动完成后确保默认用户存在。
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void ensureDefaultUser() {

        if (userRepository.findByUsername(DEFAULT_USER_NAME).isPresent()) {
            return;
        }

        User user = new User();

        user.setUsername(DEFAULT_USER_NAME);
        user.setCreatedAt(LocalDateTime.now());

        User saved = userRepository.save(user);
        log.info("默认用户已创建: id={}, username={}", saved.getId(), saved.getUsername());
    }
}
