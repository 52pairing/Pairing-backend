package com.pairing.matching.application.result;

import java.util.List;

/**
 * 프리랜서 임베딩용 이력서 요약. freelancer 도메인 소유 데이터.
 *
 * <p>임베딩에 실제로 들어가는 것만 담는다. 회사명·부서/직급은 <b>일부러 뺐다</b> — 대조 대상인
 * 포지션 쪽에 회사명에 대응하는 말이 없어서 유사도에 기여하지 못하고, 유명 회사 이름이 무관한
 * 프로젝트와 걸리는 잡음만 만든다. 여기에 필드를 되살리기 전에
 * {@code .ai/STATE.md} "[2][3] 임베딩 25 + 조건점수 75 / 텍스트 임베딩 재설계"를 먼저 볼 것.
 *
 * @param majors             학력의 학과. 학력이 여러 개면 전부 담는다(석사 경영학 + 학부 컴공처럼
 *                           최종학력만 보면 전공이 맞는 사람을 놓친다). 학과 미입력 건은 빠진다.
 * @param careerDescriptions 경력의 담당업무 서술만. 건별 순서는 이력서 입력 순서를 따른다.
 */
public record FreelancerResumeSummary(
        String selfIntroduction,
        List<String> majors,
        List<String> careerDescriptions
) {
}
