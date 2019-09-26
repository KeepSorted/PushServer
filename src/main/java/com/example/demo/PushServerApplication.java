package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
public class PushServerApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(PushServerApplication.class, args);

        new Thread(new ClientsCheck()).start();  // 客户端检查

        try {
            new NettyServer(12345).start();
        }catch(Exception e) {
            Utils.log("NettyServerError:"+e.getMessage());
        }

    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder springApplicationBuilder) {
        return springApplicationBuilder.sources(this.getClass());
    }

}
