package com.leadmagnet.crm.template.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HtmlTemplateRepository extends JpaRepository<HtmlTemplate, Long> {

    List<HtmlTemplate> findAllByOrderByCreatedAtDescIdDesc();
}
