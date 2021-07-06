package com.firmiana.domain.nettyserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages={"com.firmiana.domain.nettyserver.config", "com.firmiana.domain.nettyserver.controller"})
public class NettyserverApplication {

    public static void main(String[] args) {
        SpringApplication.run(NettyserverApplication.class, args);
    }

}
