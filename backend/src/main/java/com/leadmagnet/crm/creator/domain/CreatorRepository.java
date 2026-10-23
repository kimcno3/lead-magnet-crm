package com.leadmagnet.crm.creator.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreatorRepository extends JpaRepository<Creator, Long> {

    List<Creator> findAllByOrderByCreatedAtDescIdDesc();
}
