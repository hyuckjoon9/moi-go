package com.mycom.myapp.study.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mycom.myapp.study.entity.StudyGroup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
class StudyGroupRepositoryTest {

    @Autowired private StudyGroupRepository repository;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void findsGroupByPostId() {
        StudyGroup saved = repository.saveAndFlush(StudyGroup.create(10L, "알고리즘 스터디"));

        assertThat(repository.findByPostId(10L)).contains(saved);
        assertThat(repository.existsByPostId(10L)).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void storesStatusAsString() {
        StudyGroup saved = repository.saveAndFlush(StudyGroup.create(10L, "알고리즘 스터디"));

        String storedStatus =
                jdbcTemplate.queryForObject(
                        "select status from study_groups where id = ?",
                        String.class,
                        saved.getId());
        assertThat(storedStatus).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsDuplicatePostId() {
        repository.saveAndFlush(StudyGroup.create(10L, "첫 번째 그룹"));

        assertThatThrownBy(() -> repository.saveAndFlush(StudyGroup.create(10L, "두 번째 그룹")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
