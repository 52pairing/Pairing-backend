package com.pairing.grade.application.usecase;

/**
 * 등급 산정. (정책 P01)
 *
 * <p>사용자가 등급을 바꾸는 API 는 없다. 이 유스케이스는 배치만 호출한다.
 */
public interface GradeCommandUseCase {

    /**
     * 전 회원 등급을 다시 매긴다. 매월 1일 배치가 부른다.
     *
     * @return 실제로 등급이 바뀐 회원 수
     */
    int recalculateAll();
}
