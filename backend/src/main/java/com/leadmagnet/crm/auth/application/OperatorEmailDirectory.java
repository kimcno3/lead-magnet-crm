package com.leadmagnet.crm.auth.application;

import com.leadmagnet.crm.audit.application.OperatorDirectory;
import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** {@link OperatorDirectory} 구현. 운영자 이메일만 읽는다. */
@Component
class OperatorEmailDirectory implements OperatorDirectory {

    private final OperatorRepository operators;

    OperatorEmailDirectory(OperatorRepository operators) {
        this.operators = operators;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> emailsOf(Collection<Long> operatorIds) {
        if (operatorIds.isEmpty()) {
            return Map.of();
        }
        return operators.findAllById(operatorIds).stream()
            .collect(Collectors.toMap(Operator::getId, Operator::getEmail));
    }
}
