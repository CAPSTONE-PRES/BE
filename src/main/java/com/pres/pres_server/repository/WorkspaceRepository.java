package com.pres.pres_server.repository;

import com.pres.pres_server.domain.User;
import com.pres.pres_server.domain.WorkSpace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WorkspaceRepository extends JpaRepository<WorkSpace, Long> {
        // 제목순 정렬
        List<WorkSpace> findAllByOrderByWorkspaceNameAsc();

        // 워크스페이스 검색 결과
        List<WorkSpace> findByWorkspaceNameContainingIgnoreCase(String keyword);

        // 사용자가 속한 모든 workspace (정렬 없음)
        @Query("SELECT DISTINCT w FROM WorkSpace w " +
                        "LEFT JOIN TeamMember tm ON tm.workspace = w " +
                        "WHERE w.ownerUserId = :user OR tm.user = :user")
        List<WorkSpace> findAllByUser(@Param("user") User user);

        // 사용자가 속한 workspace (제목순 정렬)
        @Query("SELECT DISTINCT w FROM WorkSpace w " +
                        "LEFT JOIN TeamMember tm ON tm.workspace = w " +
                        "WHERE w.ownerUserId = :user OR tm.user = :user " +
                        "ORDER BY w.workspaceName ASC")
        List<WorkSpace> findAllByUserOrderByWorkspaceName(@Param("user") User user);

        List<WorkSpace> findByOwnerUserId_Id(Long id);

        // owner User id로 워크스페이스 일괄 삭제
        void deleteAllByOwnerUserId_Id(Long ownerId);

        @Query("SELECT tm.workspace FROM TeamMember tm WHERE tm.user = :user")
        List<WorkSpace> findWorkspacesByTeamMember(@Param("user") User user);

}