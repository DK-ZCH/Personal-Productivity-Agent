package com.dkzch.personal_productivity_agent.common;

import com.dkzch.personal_productivity_agent.model.entity.User;
import com.dkzch.personal_productivity_agent.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;

//V1 默认实现：当前用户 id 来自配置（app.current-user.id），默认 1
@Component
@Primary
public class DefaultCurrentUserProvider implements CurrentUserProvider {

    private static final Logger log = LoggerFactory.getLogger(DefaultCurrentUserProvider.class);

    private static final String DEFAULT_USER_NAME = "demo";

    //当前用户 id；开发期可通过配置/环境变量切换，用于多用户隔离验证。
    @Value("${app.current-user.id:1}")
    private Long currentUserId;

    private final UserRepository userRepository;

    public DefaultCurrentUserProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Long getCurrentUserId() {
        return currentUserId;
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
