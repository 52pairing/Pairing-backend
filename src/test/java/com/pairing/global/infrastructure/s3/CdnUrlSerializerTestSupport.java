package com.pairing.global.infrastructure.s3;

/**
 * 테스트에서 CDN 루트를 직접 지정하기 위한 접근점.
 *
 * <p>{@link CdnUrlSerializer#configure(String)} 는 package-private 이다. 운영 코드에서는
 * {@link CdnUrlConfigurer} 가 기동 시 한 번만 부르면 되는 값이라 그게 맞다. 다른 패키지의 테스트가
 * 그 값을 정하려면 같은 패키지에 있는 이 클래스를 거쳐야 한다.
 *
 * <p><b>바꿨으면 되돌려 놓아야 한다.</b> 이 값은 {@code static} 이라 JVM 전체가 공유하는데,
 * {@link CdnUrlConfigurer} 는 스프링 컨텍스트가 <b>처음 뜰 때만</b> 값을 넣는다. 테스트 컨텍스트는
 * 캐시되어 재사용되므로, 한 번 덮어쓰고 두면 나중에 도는 테스트가 잘못된 CDN 루트를 그대로 물려받는다.
 * {@link #current()} 로 원래 값을 받아 두었다가 {@code @AfterEach} 에서 되돌리면 된다.
 */
public final class CdnUrlSerializerTestSupport {

    private CdnUrlSerializerTestSupport() {
    }

    public static void configure(String cdnBase) {
        CdnUrlSerializer.configure(cdnBase);
    }

    /** 지금 설정된 CDN 루트. 테스트가 끝나고 되돌리기 위해 미리 받아 둔다. */
    public static String current() {
        return CdnUrlSerializer.currentBase();
    }
}
