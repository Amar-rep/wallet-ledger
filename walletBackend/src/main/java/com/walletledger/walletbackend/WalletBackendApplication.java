package com.walletledger.walletbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WalletBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(WalletBackendApplication.class, args);
    }
}
