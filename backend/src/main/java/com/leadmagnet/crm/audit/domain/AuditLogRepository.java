package com.leadmagnet.crm.audit.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** 쓰기는 {@code AuditService}만 한다. 수정·삭제 API는 없다 (ADR 0016). */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
}
