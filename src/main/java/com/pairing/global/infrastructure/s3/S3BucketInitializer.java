package com.pairing.global.infrastructure.s3;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * 기동 시 설정된 버킷에 접근할 수 있는지 확인한다.
 *
 * <p>버킷을 자동으로 만들지는 않는다. 운영 버킷은 권한·수명주기·퍼블릭 액세스 정책을 함께 정해야 하므로
 * 애플리케이션이 만들 대상이 아니다. 여기서는 "설정이 맞는지"만 미리 알려준다.
 *
 * <p>확인에 실패해도 기동을 막지는 않는다. 파일 업로드를 아직 쓰지 않는 단계에서
 * S3 설정 때문에 서버가 못 뜨는 상황을 만들지 않기 위한 선택이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class S3BucketInitializer {

    private final S3Client s3Client;
    private final S3Settings s3Settings;

    @PostConstruct
    public void verifyBucketAccess() {
        String bucketName = s3Settings.getBucket();

        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            log.info("[S3] '{}' 버킷 접근 확인 완료.", bucketName);

        } catch (S3Exception e) {
            switch (e.statusCode()) {
                case 404 -> log.warn("[S3] '{}' 버킷이 존재하지 않습니다. 버킷명(S3_BUCKET)을 확인하세요.", bucketName);
                case 403 -> log.warn("[S3] '{}' 버킷에 접근 권한이 없습니다. IAM 정책(s3:ListBucket, s3:PutObject)을 확인하세요.", bucketName);
                case 301 -> log.warn("[S3] '{}' 버킷이 다른 리전에 있습니다. AWS_REGION을 확인하세요. (현재 {})", bucketName, s3Settings.getRegion());
                default -> log.warn("[S3] '{}' 버킷 확인 중 오류({}): {}", bucketName, e.statusCode(), e.getMessage());
            }

        } catch (Exception e) {
            // 자격증명 미설정, 네트워크 차단 등
            log.warn("[S3] 버킷 확인 실패: {}", e.getMessage());
        }
    }
}
