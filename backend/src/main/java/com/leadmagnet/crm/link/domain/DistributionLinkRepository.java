package com.leadmagnet.crm.link.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DistributionLinkRepository extends JpaRepository<DistributionLink, Long> {

    Optional<DistributionLink> findByCode(String code);

    boolean existsByFormIdAndChannelAndCreatorIdIsNull(Long formId, Channel channel);

    boolean existsByFormIdAndChannelAndCreatorId(Long formId, Channel channel, Long creatorId);

    List<DistributionLink> findAllByFormId(Long formId);
}
