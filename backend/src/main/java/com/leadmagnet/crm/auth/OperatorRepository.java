package com.leadmagnet.crm.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperatorRepository extends JpaRepository<Operator, Long> {

    Optional<Operator> findByEmail(String email);

    boolean existsByEmail(String email);
}
