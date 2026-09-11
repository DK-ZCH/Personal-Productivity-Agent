package com.dkzch.personal_productivity_agent.repository;

import com.dkzch.personal_productivity_agent.model.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    //只查询只属于某用户的任务
    List<Task> findByUserId(Long userId);

    //限定用户的关键字搜索：把 userId 嵌进 WHERE
    @Query("""
            select t from Task t
            where t.userId = :userId
              and (lower(t.title) like lower(concat('%', :keyword, '%'))
                or lower(t.description) like lower(concat('%', :keyword, '%')))
            """)

    List<Task> searchByUserIdAndKeyword(@Param("userId") Long userId,@Param("keyword") String keyword);
}
