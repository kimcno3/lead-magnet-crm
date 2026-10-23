package com.leadmagnet.crm.common.web;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

/**
 * 신뢰 프록시 목록(CIDR 또는 주소 하나)과 X-Forwarded-For 해석 (ADR 0021). 값은 {@code TRUSTED_PROXIES}
 * ({@code app.trusted-proxies}, 쉼표 구분)에서 오고, 형식이 틀리면 기동이 실패한다. 비어 있으면 아무도 신뢰하지 않는다.
 *
 * <p>해석 규칙(Tomcat RemoteIpValve와 같은 방향): 직전 피어가 신뢰 프록시일 때만 XFF를 오른쪽부터 읽고, 신뢰 프록시인 값은
 * 건너뛰며, 신뢰 프록시가 아닌 첫 값을 클라이언트 주소로 쓴다. 그보다 왼쪽 값은 클라이언트가 마음대로 넣을 수 있어 보지 않는다.
 * IP 형식이 아닌 값을 만나면 거기서 멈추고 직전까지 확인한 주소를 쓴다(호스트 이름은 DNS 조회를 부르므로 해석하지 않는다).
 */
public final class TrustedProxies {

    private static final Pattern IPV4 = Pattern.compile(
        "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

    private final List<IpAddressMatcher> matchers;

    private TrustedProxies(List<IpAddressMatcher> matchers) {
        this.matchers = matchers;
    }

    public static TrustedProxies parse(List<String> values) {
        List<IpAddressMatcher> matchers = new ArrayList<>();
        for (String raw : values == null ? List.<String>of() : values) {
            String value = raw == null ? "" : raw.trim();
            if (value.isEmpty()) {
                continue;
            }
            matchers.add(matcher(value));
        }
        return new TrustedProxies(List.copyOf(matchers));
    }

    private static IpAddressMatcher matcher(String value) {
        int slash = value.indexOf('/');
        String address = slash < 0 ? value : value.substring(0, slash);
        boolean valid = isIpLiteral(address);
        if (valid && slash >= 0) {
            String bits = value.substring(slash + 1);
            int max = address.contains(":") ? 128 : 32;
            valid = bits.matches("\\d{1,3}") && Integer.parseInt(bits) <= max;
        }
        if (!valid) {
            throw new IllegalArgumentException(
                "TRUSTED_PROXIES(app.trusted-proxies)는 IP 주소나 CIDR이어야 합니다(호스트 이름 불가): " + value);
        }
        return new IpAddressMatcher(value);
    }

    /** IPv4 점 표기나 IPv6 표기인지. 호스트 이름이면 false(DNS 조회를 하지 않는다). */
    static boolean isIpLiteral(String value) {
        if (value == null) {
            return false;
        }
        if (IPV4.matcher(value).matches()) {
            return true;
        }
        if (!value.contains(":") || !IPV6.matcher(value).matches()) {
            return false;
        }
        try {
            // ':'가 있는 리터럴은 InetAddress가 이름 조회 없이 IPv6로 해석한다.
            java.net.InetAddress.getByName(value);
            return true;
        } catch (java.net.UnknownHostException e) {
            return false;
        }
    }

    public boolean isEmpty() {
        return matchers.isEmpty();
    }

    public boolean contains(String address) {
        if (!isIpLiteral(address)) {
            return false;
        }
        for (IpAddressMatcher matcher : matchers) {
            if (matcher.matches(address)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 클라이언트 주소. peer가 신뢰 프록시가 아니면 peer 그대로다.
     *
     * @param forwardedFor X-Forwarded-For 헤더 값들(여러 줄이면 순서대로 이어 붙여 본다)
     */
    public String resolve(String peer, List<String> forwardedFor) {
        if (!contains(peer)) {
            return peer;
        }
        List<String> hops = new ArrayList<>();
        for (String header : forwardedFor) {
            for (String hop : header.split(",")) {
                if (!hop.isBlank()) {
                    hops.add(hop.trim());
                }
            }
        }
        String current = peer;
        for (int i = hops.size() - 1; i >= 0; i--) {
            String hop = hops.get(i);
            if (!isIpLiteral(hop)) {
                break;
            }
            current = hop;
            if (!contains(hop)) {
                break;
            }
        }
        return current;
    }
}
