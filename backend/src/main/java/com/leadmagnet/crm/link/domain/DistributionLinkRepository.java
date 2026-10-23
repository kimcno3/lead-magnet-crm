package com.leadmagnet.crm.link.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DistributionLinkRepository extends JpaRepository<DistributionLink, Long> {

    Optional<DistributionLink> findByCode(String code);

    boolean existsByFormIdAndChannel(Long formId, Channel channel);

    List<DistributionLink> findAllByFormId(Long formId);
}
