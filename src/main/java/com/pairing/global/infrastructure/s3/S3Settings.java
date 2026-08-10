package com.pairing.global.infrastructure.s3;

import com.pairing.global.exception.RequiredPropertyMissingException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AWS S3 스토리지 설정.
 *
 * <p>단일 버킷을 사용하며, 도메인 구분은 버킷이 아니라 object key의 prefix로 표현한다.
 * 엔드포인트는 SDK가 리전에서 자동으로 결정하므로 직접 지정하지 않는다.
 *
 * <p>DB에는 object key(상대경로)만 저장하고, 응답 시 {@link #getCdnBase()}를 앞에 붙여 절대 URL을 만든다.
 * CDN(CloudFront 등)을 쓰면 {@code S3_CDN_URL}로 응답 URL 루트만 바꿔주면 되고 저장된 데이터는 건드릴 필요가 없다.
 */
@Slf4j
@Getter
@Component
public class S3Settings {

    /** 단일 버킷명. 필수값이다. */
    private final String bucket;

    /** AWS 리전. 클라이언트 리전과 URL 조합에 함께 쓰인다. */
    private final String region;

    /** 응답 URL 조합용 루트. key 앞에 붙는다. (trailing slash 제거) */
    private final String cdnBase;

    /**
     * 환경 구분용 object key prefix. 같은 버킷을 로컬·배포가 함께 쓸 때 폴더를 나눈다.
     * (예: {@code local} -> {@code local/profile/{uuid}.png}) 비우면 prefix 없이 저장한다.
     */
    private final String keyPrefix;

    public S3Settings(
            @Value("${cloud.aws.s3.bucket:}") String bucket,
            @Value("${cloud.aws.region.static:ap-northeast-2}") String region,
            @Value("${cloud.aws.s3.cdn-url:}") String cdnOverride,
            @Value("${cloud.aws.s3.key-prefix:}") String keyPrefix
    ) {
        // 환경변수에 실수로 붙은 앞뒤 공백/개행이 버킷명·리전에 섞이면
        // AWS가 잘못된 버킷/호스트로 인식해 업로드가 500으로 실패하므로 방어적으로 trim 한다.
        this.bucket = bucket == null ? "" : bucket.trim();
        this.region = region == null ? "" : region.trim();

        if (this.bucket.isBlank()) {
            throw new RequiredPropertyMissingException(
                    "cloud.aws.s3.bucket",
                    "S3_BUCKET",
                    "S3 버킷명이 비어 있습니다.",
                    """
                    아래 중 하나를 선택하세요.

                      1) 파일 업로드를 사용한다면 버킷명을 주입합니다.
                         export S3_BUCKET=your-bucket-name
                         (IntelliJ는 Run/Debug Configurations > Environment variables 에 추가)

                      2) 아직 업로드 기능이 필요 없다면 임시 값으로 넘길 수 있습니다.
                         export S3_BUCKET=local-dev-placeholder
                         버킷 접근 확인에 실패해도 기동은 되며 [S3] 경고 로그만 남습니다.

                      3) 업로드 기능을 아예 쓰지 않는다면 아래를 삭제하세요.
                         global/infrastructure/s3, global/port/out,
                         global/config/S3Config.java, global/config/CdnJacksonConfig.java,
                         global/type/FileType.java, global/util/FileTypeDetector.java,
                         example/settings/ExampleStorageSettings.java,
                         application.yaml의 cloud.aws 블록""");
        }

        this.cdnBase = resolveCdnBase(cdnOverride);
        // 앞뒤 슬래시는 떼어 둔다. key 를 만들 때 하나만 붙이므로 "//" 가 생기면 S3 에서 빈 폴더가 된다.
        this.keyPrefix = keyPrefix == null ? "" : keyPrefix.trim().replaceAll("^/+|/+$", "");

        log.info("[S3Settings] bucket={}, region={}, cdnBase={}, keyPrefix={}",
                this.bucket, this.region, this.cdnBase, this.keyPrefix.isBlank() ? "(없음)" : this.keyPrefix);
    }

    /**
     * prefix 를 붙인 최종 object key 를 만든다. prefix 가 비어 있으면 원래 key 를 그대로 돌려준다.
     *
     * <p>이 값이 그대로 DB 에 저장되므로, 삭제와 URL 조합은 저장된 key 를 쓰면 되고 별도 처리가 필요 없다.
     */
    public String withPrefix(String key) {
        return keyPrefix.isBlank() ? key : keyPrefix + "/" + key;
    }

    private String resolveCdnBase(String cdnOverride) {
        String base = (cdnOverride != null && !cdnOverride.isBlank())
                // 직접 지정한 CDN 루트를 최우선으로 사용 (예: CloudFront 도메인)
                ? cdnOverride.trim()
                // 기본은 virtual-hosted 형식: https://{bucket}.s3.{region}.amazonaws.com
                : "https://" + bucket + ".s3." + region + ".amazonaws.com";

        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
