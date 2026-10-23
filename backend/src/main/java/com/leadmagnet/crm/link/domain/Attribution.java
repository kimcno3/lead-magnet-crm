package com.leadmagnet.crm.link.domain;

/** 방문·신청에 기록할 귀속 결과. 링크가 없으면 linkId는 null이고 채널은 direct다 (DB CHECK: link_id null ⇔ direct). */
public record Attribution(Long linkId, AttributionChannel channel) {

    public static Attribution direct() {
        return new Attribution(null, AttributionChannel.direct());
    }

    public static Attribution of(DistributionLink link) {
        return new Attribution(link.getId(), AttributionChannel.of(link.getChannel()));
    }
}
