-- moigo_schema.sql
-- MySQL 8.0+
-- ERD 기반 스키마 + 관계형 테스트 데이터 (확장판)
-- 원본 스키마(DDL)는 그대로 유지하고, 테스트 데이터만 여러 시나리오가 섞이도록 채웠습니다.
USE moigo;

-- 재실행 가능하도록 자식 테이블부터 삭제
DROP TABLE IF EXISTS activity_reviews;
DROP TABLE IF EXISTS activity_records;
DROP TABLE IF EXISTS attendance_records;
DROP TABLE IF EXISTS attendance_responses;
DROP TABLE IF EXISTS study_schedules;
DROP TABLE IF EXISTS group_members;
DROP TABLE IF EXISTS study_groups;
DROP TABLE IF EXISTS join_applications;
DROP TABLE IF EXISTS recruitment_posts;
DROP TABLE IF EXISTS refresh_tokens;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    email             VARCHAR(255) NOT NULL,
    password          VARCHAR(255) NOT NULL,
    nickname          VARCHAR(50) NOT NULL,
    bio               TEXT NULL,
    interests         VARCHAR(255) NULL,
    profile_image_url VARCHAR(500) NULL,
    role              VARCHAR(20) NOT NULL DEFAULT 'USER',
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_nickname UNIQUE (nickname),
    CONSTRAINT chk_users_role
        CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT chk_users_status
        CHECK (status IN ('ACTIVE', 'WITHDRAWN'))
) ENGINE = InnoDB;

CREATE TABLE refresh_tokens (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    token      VARCHAR(500) NOT NULL,
    expires_at DATETIME NOT NULL,

    CONSTRAINT uk_refresh_tokens_token UNIQUE (token),
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE = InnoDB;

CREATE INDEX idx_refresh_tokens_user_id
    ON refresh_tokens (user_id);

CREATE TABLE recruitment_posts (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    leader_id            BIGINT NOT NULL,
    title                VARCHAR(100) NOT NULL,
    category             VARCHAR(50) NOT NULL,
    description          TEXT NOT NULL,
    goal                 TEXT NULL,
    method               TEXT NULL,
    meeting_type         VARCHAR(20) NOT NULL,
    location             VARCHAR(255) NULL,
    online_link          VARCHAR(500) NULL,
    meeting_day          VARCHAR(50) NULL,
    capacity             INT NOT NULL,
    recruitment_deadline DATE NOT NULL,
    expected_duration    VARCHAR(50) NULL,
    conditions           TEXT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'RECRUITING',
    created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                           ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_recruitment_posts_leader
        FOREIGN KEY (leader_id) REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT chk_recruitment_posts_meeting_type
        CHECK (meeting_type IN ('ONLINE', 'OFFLINE', 'HYBRID')),
    CONSTRAINT chk_recruitment_posts_capacity
        CHECK (capacity > 0),
    CONSTRAINT chk_recruitment_posts_status
        CHECK (status IN ('RECRUITING', 'CLOSED', 'ACTIVE', 'ENDED'))
) ENGINE = InnoDB;

CREATE INDEX idx_recruitment_posts_status_category
    ON recruitment_posts (status, category);

CREATE INDEX idx_recruitment_posts_deadline
    ON recruitment_posts (recruitment_deadline);

CREATE INDEX idx_recruitment_posts_leader_id
    ON recruitment_posts (leader_id);

CREATE TABLE join_applications (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id        BIGINT NOT NULL,
    applicant_id   BIGINT NOT NULL,
    motivation     TEXT NOT NULL,
    experience     TEXT NULL,
    available_time VARCHAR(100) NULL,
    desired_role   VARCHAR(50) NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    applied_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_join_applications_post_applicant
        UNIQUE (post_id, applicant_id),
    CONSTRAINT fk_join_applications_post
        FOREIGN KEY (post_id) REFERENCES recruitment_posts(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT fk_join_applications_applicant
        FOREIGN KEY (applicant_id) REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT chk_join_applications_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))
) ENGINE = InnoDB;

CREATE INDEX idx_join_applications_post_status
    ON join_applications (post_id, status);

CREATE INDEX idx_join_applications_applicant_id
    ON join_applications (applicant_id);

CREATE TABLE study_groups (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id    BIGINT NOT NULL,
    name       VARCHAR(100) NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_study_groups_post_id UNIQUE (post_id),
    CONSTRAINT fk_study_groups_post
        FOREIGN KEY (post_id) REFERENCES recruitment_posts(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT chk_study_groups_status
        CHECK (status IN ('ACTIVE', 'ENDED'))
) ENGINE = InnoDB;

CREATE TABLE group_members (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id  BIGINT NOT NULL,
    user_id   BIGINT NOT NULL,
    role      VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    status    VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_group_members_group_user
        UNIQUE (group_id, user_id),
    CONSTRAINT fk_group_members_group
        FOREIGN KEY (group_id) REFERENCES study_groups(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT fk_group_members_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT chk_group_members_role
        CHECK (role IN ('LEADER', 'MANAGER', 'MEMBER')),
    CONSTRAINT chk_group_members_status
        CHECK (status IN ('ACTIVE', 'WITHDRAWN'))
) ENGINE = InnoDB;

CREATE INDEX idx_group_members_user_status
    ON group_members (user_id, status);

CREATE TABLE study_schedules (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id          BIGINT NOT NULL,
    creator_id        BIGINT NOT NULL,
    title             VARCHAR(100) NOT NULL,
    scheduled_at      DATETIME NOT NULL,
    location          VARCHAR(255) NULL,
    online_link       VARCHAR(500) NULL,
    content           TEXT NULL,
    materials         TEXT NULL,
    response_deadline DATETIME NULL,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_study_schedules_group
        FOREIGN KEY (group_id) REFERENCES study_groups(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT fk_study_schedules_creator
        FOREIGN KEY (creator_id) REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT chk_study_schedules_response_deadline
        CHECK (
            response_deadline IS NULL
            OR response_deadline <= scheduled_at
        )
) ENGINE = InnoDB;

CREATE INDEX idx_study_schedules_group_scheduled_at
    ON study_schedules (group_id, scheduled_at);

CREATE INDEX idx_study_schedules_creator_id
    ON study_schedules (creator_id);

CREATE TABLE attendance_responses (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    schedule_id  BIGINT NOT NULL,
    user_id      BIGINT NOT NULL,
    response     VARCHAR(20) NOT NULL DEFAULT 'UNDECIDED',
    responded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_attendance_responses_schedule_user
        UNIQUE (schedule_id, user_id),
    CONSTRAINT fk_attendance_responses_schedule
        FOREIGN KEY (schedule_id) REFERENCES study_schedules(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT fk_attendance_responses_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT chk_attendance_responses_response
        CHECK (response IN ('ATTEND', 'ABSENT', 'UNDECIDED'))
) ENGINE = InnoDB;

CREATE INDEX idx_attendance_responses_user_id
    ON attendance_responses (user_id);

CREATE TABLE attendance_records (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    schedule_id BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    status      VARCHAR(20) NOT NULL,
    checked_by  BIGINT NOT NULL,
    checked_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_attendance_records_schedule_user
        UNIQUE (schedule_id, user_id),
    CONSTRAINT fk_attendance_records_schedule
        FOREIGN KEY (schedule_id) REFERENCES study_schedules(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT fk_attendance_records_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT fk_attendance_records_checker
        FOREIGN KEY (checked_by) REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT chk_attendance_records_status
        CHECK (status IN ('PRESENT', 'LATE', 'ABSENT', 'EXCUSED'))
) ENGINE = InnoDB;

CREATE INDEX idx_attendance_records_user_id
    ON attendance_records (user_id);

CREATE INDEX idx_attendance_records_checked_by
    ON attendance_records (checked_by);

CREATE TABLE activity_records (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    schedule_id      BIGINT NOT NULL,
    author_id        BIGINT NOT NULL,
    topic            VARCHAR(100) NOT NULL,
    content          TEXT NOT NULL,
    assignment       TEXT NULL,
    next_preparation TEXT NULL,
    reference_links  TEXT NULL,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                     ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_activity_records_schedule_id UNIQUE (schedule_id),
    CONSTRAINT fk_activity_records_schedule
        FOREIGN KEY (schedule_id) REFERENCES study_schedules(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,
    CONSTRAINT fk_activity_records_author
        FOREIGN KEY (author_id) REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
) ENGINE = InnoDB;

CREATE INDEX idx_activity_records_author_id
    ON activity_records (author_id);

CREATE TABLE activity_reviews (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_record_id BIGINT NOT NULL,
    user_id            BIGINT NOT NULL,
    comment            VARCHAR(300) NOT NULL,
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_activity_reviews_record_user
        UNIQUE (activity_record_id, user_id),
    CONSTRAINT fk_activity_reviews_record
        FOREIGN KEY (activity_record_id) REFERENCES activity_records(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,
    CONSTRAINT fk_activity_reviews_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
) ENGINE = InnoDB;

CREATE INDEX idx_activity_reviews_user_id
    ON activity_reviews (user_id);


-- =========================================================
-- 테스트 데이터
-- 12명의 사용자, 4개의 모집글(RECRUITING/CLOSED/ACTIVE/ENDED 상태를 골고루 포함),
-- 지원 상태(PENDING/APPROVED/REJECTED/CANCELLED)와 그룹원 상태(ACTIVE/WITHDRAWN),
-- 종료된 그룹 이력까지 포함해 백오피스·프론트 테스트 시나리오를 커버합니다.
--
-- 로그인 테스트용 평문 비밀번호:
--   일반 사용자(1~11번) 전부       -> Password1!
--   admin@moigo.test (12번, ADMIN) -> Admin1234!
-- 아래 해시는 BCrypt($2b$, cost=10) 형식으로 실제로 로그인 가능하도록 생성한 값입니다.
-- (Spring Security의 BCryptPasswordEncoder는 $2a$/$2b$/$2y$ 접두사를 모두 지원합니다.)
-- 로그인이 안 되면 비밀번호 해시 알고리즘 버전 차이일 수 있으니, 그 경우엔
-- /api/auth/signup으로 admin 계정을 새로 만들고 DB에서 role만 'ADMIN'으로 UPDATE 하는 방법으로 대체하세요.
-- =========================================================

INSERT INTO users (
    email, password, nickname, bio, interests,
    profile_image_url, role, status
) VALUES
(
    'leader@moigo.test',
    '$2b$10$2F8aYODZS/vYu524a8Vuqe7IEfrtnsQKOwpxXa0PRfFsZ9Ydp9u7y',
    '모이고리더',
    '백엔드 스터디를 운영하는 테스트 사용자',
    'Java,Spring,MySQL',
    NULL,
    'USER',
    'ACTIVE'
),
(
    'member@moigo.test',
    '$2b$10$2F8aYODZS/vYu524a8Vuqe7IEfrtnsQKOwpxXa0PRfFsZ9Ydp9u7y',
    '모이고멤버',
    '스터디에 참가하는 테스트 사용자',
    'Java,Algorithm',
    NULL,
    'USER',
    'ACTIVE'
),
(
    'sooyeon@moigo.test',
    '$2b$10$2F8aYODZS/vYu524a8Vuqe7IEfrtnsQKOwpxXa0PRfFsZ9Ydp9u7y',
    '김수연',
    '프론트엔드 스터디를 운영합니다.',
    'React,TypeScript',
    NULL,
    'USER',
    'ACTIVE'
),
(
    'minjun@moigo.test',
    '$2b$10$2F8aYODZS/vYu524a8Vuqe7IEfrtnsQKOwpxXa0PRfFsZ9Ydp9u7y',
    '이민준',
    '백엔드 스터디 지원자(대기중) 테스트 사용자',
    'Java',
    NULL,
    'USER',
    'ACTIVE'
),
(
    'hyejin@moigo.test',
    '$2b$10$2F8aYODZS/vYu524a8Vuqe7IEfrtnsQKOwpxXa0PRfFsZ9Ydp9u7y',
    '박혜진',
    '거절된 지원 케이스 테스트 사용자',
    'Java,Spring',
    NULL,
    'USER',
    'ACTIVE'
),
(
    'jiho@moigo.test',
    '$2b$10$2F8aYODZS/vYu524a8Vuqe7IEfrtnsQKOwpxXa0PRfFsZ9Ydp9u7y',
    '최지호',
    '프론트엔드 스터디 승인 멤버 테스트 사용자',
    'React,CSS',
    NULL,
    'USER',
    'ACTIVE'
),
(
    'yuna@moigo.test',
    '$2b$10$2F8aYODZS/vYu524a8Vuqe7IEfrtnsQKOwpxXa0PRfFsZ9Ydp9u7y',
    '정유나',
    '지원 취소(CANCELLED) 케이스 테스트 사용자',
    'React',
    NULL,
    'USER',
    'ACTIVE'
),
(
    'taehyun@moigo.test',
    '$2b$10$2F8aYODZS/vYu524a8Vuqe7IEfrtnsQKOwpxXa0PRfFsZ9Ydp9u7y',
    '강태현',
    '알고리즘 스터디를 운영합니다(모집 마감).',
    'Algorithm,C++',
    NULL,
    'USER',
    'ACTIVE'
),
(
    'dohyun@moigo.test',
    '$2b$10$2F8aYODZS/vYu524a8Vuqe7IEfrtnsQKOwpxXa0PRfFsZ9Ydp9u7y',
    '한도현',
    '그룹 탈퇴(WITHDRAWN) 케이스 테스트 사용자',
    'Algorithm',
    NULL,
    'USER',
    'ACTIVE'
),
(
    'eunji@moigo.test',
    '$2b$10$2F8aYODZS/vYu524a8Vuqe7IEfrtnsQKOwpxXa0PRfFsZ9Ydp9u7y',
    '오은지',
    '종료된 스터디를 운영했던 테스트 사용자',
    '커리어,면접',
    NULL,
    'USER',
    'ACTIVE'
),
(
    'withdrawn@moigo.test',
    '$2b$10$2F8aYODZS/vYu524a8Vuqe7IEfrtnsQKOwpxXa0PRfFsZ9Ydp9u7y',
    '탈퇴한회원',
    '회원 탈퇴(WITHDRAWN) 상태 테스트 사용자',
    NULL,
    NULL,
    'USER',
    'WITHDRAWN'
),
(
    'admin@moigo.test',
    '$2b$10$8viIC4GEP3ooXSccotvHm.i0lklfvYFdMcrEvqVFUAdntzkvLweb.',
    '운영자',
    '백오피스 로그인용 관리자 계정',
    NULL,
    NULL,
    'ADMIN',
    'ACTIVE'
);

INSERT INTO refresh_tokens (
    user_id, token, expires_at
) VALUES
(1, 'test-refresh-token-leader-001', '2026-08-31 23:59:59'),
(12, 'test-refresh-token-admin-001', '2026-08-31 23:59:59');

INSERT INTO recruitment_posts (
    leader_id, title, category, description, goal, method,
    meeting_type, location, online_link, meeting_day,
    capacity, recruitment_deadline, expected_duration,
    conditions, status
) VALUES
(
    1,
    'Spring Boot 백엔드 스터디',
    '개발',
    'Spring Boot 기반 백엔드 개발을 함께 학습하는 스터디입니다.',
    'REST API와 인증 기능을 포함한 미니 프로젝트 완성',
    '주 1회 오프라인 모임 및 GitHub 코드 리뷰',
    'HYBRID',
    '서울 강남구',
    'https://meet.example.com/moigo-spring',
    '토요일',
    4,
    '2026-08-10',
    '8주',
    'Java 기본 문법을 이해하고 있는 사람',
    'ACTIVE'
),
(
    3,
    'React 프론트엔드 스터디',
    '개발',
    'React와 TypeScript로 실전 프로젝트를 만드는 스터디입니다. 아직 모집 중입니다.',
    '실무형 컴포넌트 설계 능력 향상',
    '주 1회 온라인 모임',
    'ONLINE',
    NULL,
    'https://meet.example.com/moigo-react',
    '수요일',
    5,
    '2026-08-20',
    '6주',
    'HTML/CSS 기본 지식',
    'RECRUITING'
),
(
    8,
    '알고리즘 스터디',
    '개발',
    '코딩 테스트 대비 알고리즘 문제풀이 스터디입니다. 모집은 마감되었습니다.',
    '코딩 테스트 상위권 통과',
    '주 2회 오프라인 문제풀이',
    'OFFLINE',
    '서울 관악구',
    NULL,
    '화/목요일',
    6,
    '2026-07-15',
    '10주',
    '자료구조 기초 지식 필요',
    'CLOSED'
),
(
    10,
    '취업 준비 스터디',
    '커리어',
    '이력서·포트폴리오 첨삭과 모의 면접을 함께 진행했던 스터디입니다. 이미 종료되었습니다.',
    '서류 통과율 향상 및 면접 실전 감각 확보',
    '주 1회 모의 면접 및 피드백',
    'ONLINE',
    NULL,
    'https://meet.example.com/moigo-career',
    '월요일',
    5,
    '2026-06-01',
    '4주',
    '취업 준비생',
    'ENDED'
);

INSERT INTO join_applications (
    post_id, applicant_id, motivation, experience,
    available_time, desired_role, status
) VALUES
(
    1, 2,
    'Spring Security와 JPA를 프로젝트로 익히고 싶습니다.',
    'Java와 Spring Boot 기초 프로젝트 경험이 있습니다.',
    '매주 토요일 14:00 이후',
    'MEMBER',
    'APPROVED'
),
(
    1, 4,
    '백엔드 실무 감각을 키우고 싶어 지원합니다. 아직 승인 대기 중입니다.',
    '개인 토이 프로젝트 경험',
    '매주 토요일 오후',
    'MEMBER',
    'PENDING'
),
(
    1, 5,
    '함께 공부하고 싶습니다. (거절된 지원 예시)',
    NULL,
    '평일 저녁',
    'MEMBER',
    'REJECTED'
),
(
    2, 6,
    'React 실전 프로젝트 경험을 쌓고 싶습니다.',
    '개인 포트폴리오 사이트 제작 경험',
    '평일 저녁',
    'MEMBER',
    'APPROVED'
),
(
    2, 7,
    '참여하고 싶었지만 일정이 맞지 않아 지원을 취소했습니다.',
    NULL,
    '주말만 가능',
    'MEMBER',
    'CANCELLED'
),
(
    3, 9,
    '코딩 테스트 준비를 위해 지원합니다.',
    '백준 골드 티어',
    '화/목요일 저녁',
    'MEMBER',
    'APPROVED'
),
(
    4, 2,
    '취업 준비 스터디에 지원합니다. (종료된 스터디의 과거 지원 이력)',
    '신입 개발자 취업 준비 중',
    '평일 저녁',
    'MEMBER',
    'APPROVED'
);

INSERT INTO study_groups (
    post_id, name, status
) VALUES
(1, 'Spring Boot 백엔드 스터디', 'ACTIVE'),
(2, 'React 프론트엔드 스터디', 'ACTIVE'),
(3, '알고리즘 스터디', 'ACTIVE'),
(4, '취업 준비 스터디', 'ENDED');

INSERT INTO group_members (
    group_id, user_id, role, status
) VALUES
(1, 1, 'LEADER', 'ACTIVE'),
(1, 2, 'MEMBER', 'ACTIVE'),
(2, 3, 'LEADER', 'ACTIVE'),
(2, 6, 'MEMBER', 'ACTIVE'),
(3, 8, 'LEADER', 'ACTIVE'),
(3, 9, 'MEMBER', 'WITHDRAWN'),
(4, 10, 'LEADER', 'ACTIVE'),
(4, 2, 'MEMBER', 'ACTIVE');

INSERT INTO study_schedules (
    group_id, creator_id, title, scheduled_at,
    location, online_link, content, materials,
    response_deadline
) VALUES
(
    1, 1,
    '1주차 OT 및 프로젝트 주제 선정',
    '2026-08-15 14:00:00',
    '서울 강남구 스터디룸',
    NULL,
    '팀 규칙을 정하고 프로젝트 주제를 선정합니다.',
    '노트북, GitHub 계정',
    '2026-08-14 18:00:00'
),
(
    1, 1,
    '2주차 API 설계 리뷰',
    '2026-08-22 14:00:00',
    '서울 강남구 스터디룸',
    NULL,
    '각자 설계한 API 명세를 리뷰합니다.',
    '노트북',
    '2026-08-21 18:00:00'
),
(
    2, 3,
    'OT 및 컴포넌트 설계',
    '2026-08-25 19:00:00',
    NULL,
    'https://meet.example.com/moigo-react-w1',
    '공통 컴포넌트 구조를 함께 설계합니다.',
    NULL,
    '2026-08-24 20:00:00'
),
(
    3, 8,
    '3주차 알고리즘 문제풀이',
    '2026-07-20 19:00:00',
    '서울 관악구 스터디룸',
    NULL,
    'DP 문제 5개를 함께 풀이합니다.',
    '문제집, 노트북',
    '2026-07-19 20:00:00'
),
(
    4, 10,
    '마지막 모임 (취업 준비 마무리)',
    '2026-05-30 15:00:00',
    NULL,
    'https://meet.example.com/moigo-career-last',
    '스터디 회고와 취업 결과 공유.',
    NULL,
    '2026-05-29 20:00:00'
);

INSERT INTO attendance_responses (
    schedule_id, user_id, response
) VALUES
(1, 2, 'ATTEND'),
(4, 9, 'ATTEND'),
(5, 2, 'ATTEND');

INSERT INTO attendance_records (
    schedule_id, user_id, status, checked_by
) VALUES
(4, 9, 'PRESENT', 8),
(5, 2, 'PRESENT', 10);

INSERT INTO activity_records (
    schedule_id, author_id, topic, content,
    assignment, next_preparation, reference_links
) VALUES
(
    4, 8,
    '3주차 알고리즘 문제풀이',
    'DP 문제 5개를 함께 풀고 풀이 전략을 정리했습니다.',
    '오늘 풀이한 문제 복습 및 유사 문제 2개 추가로 풀어오기',
    '다음 주는 그래프 탐색 문제 예정',
    'https://github.com/example/moigo-algo'
),
(
    5, 10,
    '취업 스터디 마무리 회고',
    '4주간의 이력서·모의면접 준비 과정을 회고하고 각자의 취업 결과를 공유했습니다.',
    NULL,
    NULL,
    NULL
);

INSERT INTO activity_reviews (
    activity_record_id, user_id, comment
) VALUES
(1, 9, '문제풀이 설명이 자세해서 좋았습니다.'),
(2, 2, '다들 고생 많으셨습니다! 좋은 스터디였어요.');
