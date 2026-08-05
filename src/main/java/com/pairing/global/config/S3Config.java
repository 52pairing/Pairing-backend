package com.pairing.global.config;

import com.pairing.global.infrastructure.s3.S3Settings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Slf4j
@Configuration
public class S3Config {

    @Value("${cloud.aws.credentials.access-key:}")
    private String accessKey;

    @Value("${cloud.aws.credentials.secret-key:}")
    private String secretKey;

    @Bean
    public S3Client s3Client(S3Settings s3Settings) {
        // 엔드포인트는 지정하지 않는다. SDK가 리전에 맞는 S3 엔드포인트를 자동으로 선택한다.
        return S3Client.builder()
                .region(Region.of(s3Settings.getRegion()))
                .credentialsProvider(credentialsProvider())
                .build();
    }

    /**
     * 액세스 키를 명시하면 그것을 쓰고, 비어 있으면 AWS 기본 자격증명 체인을 사용한다.
     *
     * <p>EC2/ECS에 배포할 때는 키를 주입하지 말고 인스턴스·태스크 IAM 역할을 쓰는 편이 안전하다.
     * 키를 환경변수로 심으면 유출 경로가 늘어나고 교체(rotation)도 사람이 해야 한다.
     */
    private AwsCredentialsProvider credentialsProvider() {
        if (StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey)) {
            log.info("[S3Config] 정적 자격증명(액세스 키)을 사용합니다.");
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey.trim(), secretKey.trim()));
        }

        log.info("[S3Config] 기본 자격증명 체인을 사용합니다. (환경변수 / ~/.aws / IAM 역할)");
        return DefaultCredentialsProvider.create();
    }
}
