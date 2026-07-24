package com.mycom.myapp.study.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.mycom.myapp.study.entity.GroupRole;
import com.mycom.myapp.study.repository.GroupMemberRepository;
import com.mycom.myapp.study.repository.StudyGroupRepository;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:group_creation;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=create-drop"
        })
class StudyGroupCreationIntegrationTest {

    @Autowired private StudyGroupCreationService service;
    @Autowired private StudyGroupRepository studyGroupRepository;
    @MockitoSpyBean private GroupMemberRepository groupMemberRepository;

    @BeforeEach
    void cleanUp() {
        groupMemberRepository.deleteAll();
        studyGroupRepository.deleteAll();
    }

    @Test
    void createsGroupWithNormalizedInitialMembers() {
        CreateStudyGroupCommand command =
                new CreateStudyGroupCommand(10L, "알고리즘 스터디", 1L, List.of(20L, 1L, 20L, 30L));

        Long groupId = service.create(command);

        assertThat(studyGroupRepository.findByPostId(10L))
                .get()
                .extracting(group -> group.getId(), group -> group.getName())
                .containsExactly(groupId, "알고리즘 스터디");
        assertThat(groupMemberRepository.findAllByStudyGroupId(groupId))
                .extracting(member -> member.getUserId(), member -> member.getRole())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, GroupRole.LEADER),
                        org.assertj.core.groups.Tuple.tuple(20L, GroupRole.MEMBER),
                        org.assertj.core.groups.Tuple.tuple(30L, GroupRole.MEMBER));
    }

    @Test
    void repeatedRequestReturnsSameGroupWithoutAddingMembers() {
        CreateStudyGroupCommand first = new CreateStudyGroupCommand(10L, "최초 이름", 1L, List.of(20L));
        CreateStudyGroupCommand retry =
                new CreateStudyGroupCommand(10L, "변경된 이름", 1L, List.of(20L, 30L));

        Long firstGroupId = service.create(first);
        Long retriedGroupId = service.create(retry);

        assertThat(retriedGroupId).isEqualTo(firstGroupId);
        assertThat(studyGroupRepository.count()).isEqualTo(1);
        assertThat(groupMemberRepository.findAllByStudyGroupId(firstGroupId))
                .extracting(member -> member.getUserId())
                .containsExactly(1L, 20L);
    }

    @Test
    void rollsBackGroupWhenMemberPersistenceFails() {
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("member persistence failed");
        doThrow(failure).when(groupMemberRepository).flush();

        try {
            assertThatThrownBy(
                            () ->
                                    service.create(
                                            new CreateStudyGroupCommand(
                                                    10L, "알고리즘 스터디", 1L, List.of(20L))))
                    .isSameAs(failure);
        } finally {
            reset(groupMemberRepository);
        }

        assertThat(studyGroupRepository.findByPostId(10L)).isEmpty();
    }

    @Test
    void concurrentRequestsReturnSameGroupWithoutDuplicates() throws Exception {
        CreateStudyGroupCommand command =
                new CreateStudyGroupCommand(10L, "알고리즘 스터디", 1L, List.of(20L, 30L));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Long> creation =
                () -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    return service.create(command);
                };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Long> first = executor.submit(creation);
            Future<Long> second = executor.submit(creation);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            Long firstGroupId = first.get(10, TimeUnit.SECONDS);
            Long secondGroupId = second.get(10, TimeUnit.SECONDS);

            assertThat(secondGroupId).isEqualTo(firstGroupId);
            assertThat(studyGroupRepository.count()).isEqualTo(1);
            assertThat(groupMemberRepository.findAllByStudyGroupId(firstGroupId))
                    .extracting(member -> member.getUserId())
                    .containsExactly(1L, 20L, 30L);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
