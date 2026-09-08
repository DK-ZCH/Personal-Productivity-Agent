package com.dkzch.personal_productivity_agent.repository;

import com.dkzch.personal_productivity_agent.model.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    /**
     * 按关键字模糊搜索：匹配 title 或 description（不区分大小写）。
     */
    @Query("""
            select t from Task t
            where lower(t.title) like lower(concat('%', :keyword, '%'))
               or lower(t.description) like lower(concat('%', :keyword, '%'))
            """)
    List<Task> searchByKeyword(@Param("keyword") String keyword);
}
