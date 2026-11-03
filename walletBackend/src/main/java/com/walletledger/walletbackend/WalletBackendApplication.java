package com.walletledger.walletbackend;

import com.walletledger.walletbackend.config.FlexProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(FlexProperties.class)
public class WalletBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(WalletBackendApplication.class, args);
    }
}
