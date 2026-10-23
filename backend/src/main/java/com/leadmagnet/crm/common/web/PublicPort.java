package com.leadmagnet.crm.common.web;

import com.leadmagnet.crm.common.PortsProperties;
import org.apache.catalina.connector.Connector;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

/**
 * public 출처 커넥터 (ADR 0010). 기본 커넥터(admin 포트)와 같은 앱에 Tomcat 커넥터를 하나 더 붙이고,
 * 실제로 열린 포트를 알려 준다. app.ports.public-port가 0이면(테스트) 임의 포트로 뜨므로 설정값 대신
 * 커넥터의 로컬 포트로 요청이 어느 출처로 들어왔는지 판단한다.
 */
@Component
public class PublicPort implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    private final Connector connector;

    public PublicPort(PortsProperties ports) {
        this.connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        this.connector.setPort(ports.publicPort());
    }

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.addAdditionalTomcatConnectors(connector);
    }

    /** public 커넥터가 실제로 듣고 있는 포트. 서버가 뜨기 전에는 -1이다. */
    public int get() {
        return connector.getLocalPort();
    }

    /** 요청이 public 커넥터로 들어왔는지. 뜨기 전(-1)에는 어떤 요청도 public이 아니다. */
    public boolean matches(int localPort) {
        int port = get();
        return port > 0 && port == localPort;
    }
}
