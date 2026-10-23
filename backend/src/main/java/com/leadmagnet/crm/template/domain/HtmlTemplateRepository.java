package com.leadmagnet.crm.template.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface HtmlTemplateRepository extends JpaRepository<HtmlTemplate, Long> {

    List<HtmlTemplate> findAllByOrderByCreatedAtDescIdDesc();

    /** 게시·새 버전 업로드가 같은 템플릿을 동시에 바꾸지 못하게 행을 잠그고 읽는다 (ADR 0018). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from HtmlTemplate t where t.id = :id")
    Optional<HtmlTemplate> findByIdForUpdate(Long id);
}
