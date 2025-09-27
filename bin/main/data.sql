

--테스트용 유저
INSERT INTO users (id, email, password, username, email_verified, push_enabled) VALUES (1, 'test@example.com', 'password', 'testuser', true, true);
--테스트용 워크스페이스
INSERT INTO workspaces (workspace_id, workspace_name, owner_user_id) VALUES (1, '테스트 워크스페이스', 1);
--테스트용 프로젝트
INSERT INTO projects (project_id, workspace_id, title, is_bookmarked) VALUES (1, 1, '테스트 프로젝트', true);
