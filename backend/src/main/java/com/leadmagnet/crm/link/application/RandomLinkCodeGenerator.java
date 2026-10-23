package com.leadmagnet.crm.link.application;

import com.leadmagnet.crm.link.domain.LinkCode;
import com.leadmagnet.crm.link.domain.LinkCodeGenerator;
import org.springframework.stereotype.Component;

@Component
public class RandomLinkCodeGenerator implements LinkCodeGenerator {

    @Override
    public String next() {
        return LinkCode.generate();
    }
}
