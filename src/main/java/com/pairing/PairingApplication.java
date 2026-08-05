package com.pairing;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PairingApplication {

    public static void main(String[] args) {
        SpringApplication.run(PairingApplication.class, args);
    }

    // 컨테이너 환경은 시간대가 UTC로 고정된 경우가 많으므로,
    // 애플리케이션 기동 시 JVM 기본 타임존을 한국 시간(KST)으로 지정한다.
    // 이후 new Date(), LocalDateTime.now(), 로그/JPA Auditing 타임스탬프가 모두 KST 기준으로 동작한다.
    @PostConstruct
    public void setDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }
}
