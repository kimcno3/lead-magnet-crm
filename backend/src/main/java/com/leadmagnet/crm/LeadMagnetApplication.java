package com.leadmagnet.crm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LeadMagnetApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeadMagnetApplication.class, args);
    }
}
