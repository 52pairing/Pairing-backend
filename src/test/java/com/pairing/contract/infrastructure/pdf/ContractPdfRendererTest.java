package com.pairing.contract.infrastructure.pdf;

import com.pairing.contract.application.result.ContractPdfView;
import com.pairing.contract.domain.model.ContractClause;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 계약서 PDF 렌더링. Spring 없이 템플릿 엔진만 직접 만들어 붙인다.
 *
 * <p>여기서 잡고 싶은 것은 <b>조용한 실패</b>다. 폰트가 빠지거나 템플릿 변수 이름이 어긋나도
 * 예외가 안 나고 빈 PDF 가 나오는데, 그건 사용자가 파일을 열어 봐야 알게 된다.
 */
class ContractPdfRendererTest {

    private static ContractPdfRenderer renderer;

    @BeforeAll
    static void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");

        // 운영에서 주입되는 것과 같은 엔진을 쓴다. 순수 TemplateEngine 은 OGNL 을 요구해
        // 클래스패스에 없고, 표현식 평가 방식도 달라 여기서 통과해도 운영과 다를 수 있다.
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        renderer = new ContractPdfRenderer(engine);
    }

    private ContractPdfView view(List<ContractPdfView.Signature> signatures) {
        return new ContractPdfView(
                "CT-2026-000001",
                "2026년 9월 1일",
                new ContractPdfView.Party("주식회사 페어링", "1234567890", "홍길동",
                        "경기도 성남시 분당구 판교역로 235", "0212345678", "주식회사 페어링"),
                new ContractPdfView.Party(null, null, null, null, "01098765432", "김민준"),
                "백엔드 개발자",
                "카카오뱅크 3333012345678 (예금주: 김민준)",
                List.of(
                        new ContractClause(1, "목적", "본 계약은 갑이 의뢰하는 「페어링 웹 리뉴얼」 프로젝트의 "
                                + "수행에 관하여 갑과 을의 권리와 의무를 정함을 목적으로 한다."),
                        new ContractClause(4, "계약 금액", "① 월 용역대금: 5,000,000원 (부가세 별도)"
                                + System.lineSeparator() + "② 총 계약 금액: 20,000,000원"),
                        new ContractClause(6, "플랫폼 이용 수수료", "① 갑과 을은 수수료를 각자 부담한다."),
                        new ContractClause(15, "특약사항", "별도의 특약사항 없음")),
                signatures);
    }

    @Test
    @DisplayName("PDF 가 만들어지고 파일 형식이 맞다")
    void rendersPdf() {
        byte[] pdf = renderer.render(view(List.of(
                new ContractPdfView.Signature("갑 (클라이언트)", "주식회사 페어링", null, "2026년 9월 1일"),
                new ContractPdfView.Signature("을 (프리랜서)", "김민준", null, null))));

        // PDF 는 %PDF- 로 시작한다. 빈 배열이나 HTML 이 그대로 나오는 것을 잡는다.
        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("한글 폰트가 PDF 에 임베드된다")
    void embedsKoreanFont() {
        // openhtmltopdf 는 시스템 폰트를 안 쓴다. 등록이 빠지면 오류 없이 한글만 비어서 나온다.
        // 임베드된 폰트 이름이 파일 안에 남으므로 그것으로 확인한다.
        byte[] pdf = renderer.render(view(List.of(
                new ContractPdfView.Signature("갑 (클라이언트)", "주식회사 페어링", null, "2026년 9월 1일"))));

        assertThat(new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1))
                .contains("NotoSansKR");
    }

    @Test
    @DisplayName("굵기 두 벌이 함께 임베드된다")
    void embedsBothWeights() {
        // 한 벌만 등록하면 제목이 본문과 같은 굵기로 나온다. 가변 폰트(VF)를 쓰던 때는 PDFBox 가
        // wght 축을 못 읽어 전체가 가늘게 나왔고, 그래서 글자색을 검정으로 해도 회색으로 보였다.
        byte[] pdf = renderer.render(view(List.of(
                new ContractPdfView.Signature("갑 (클라이언트)", "주식회사 페어링", null, "2026년 9월 1일"))));

        String raw = new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertThat(raw).contains("NotoSansKR-Regular");
        assertThat(raw).contains("NotoSansKR-Bold");
    }

    @Test
    @DisplayName("서명 그림이 없어도 렌더링이 깨지지 않는다")
    void rendersWithoutSignatureImage() {
        // 동의 클릭만으로 서명한 경우다. 그림 자리는 이름과 시각으로만 채운다.
        byte[] pdf = renderer.render(view(List.of(
                new ContractPdfView.Signature("을 (프리랜서)", "김민준", null, null))));

        assertThat(pdf).isNotEmpty();
    }
}
