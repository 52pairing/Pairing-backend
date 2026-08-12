package com.pairing.contract.infrastructure.pdf;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.pairing.contract.application.port.ContractPdfPort;
import com.pairing.contract.application.result.ContractPdfView;
import com.pairing.contract.exception.ContractErrorCode;
import com.pairing.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 계약서 PDF 렌더링. Thymeleaf 로 HTML 을 만들고 openhtmltopdf 로 굽는다.
 *
 * <p>외부 전자서명 서비스를 쓰지 않기로 해서 문서 생성도 직접 한다. 계약은 5년 보관 대상이라
 * 언제 다시 뽑아도 같은 문서가 나와야 하고, 그러려면 본문이 서버에 있어야 한다.
 *
 * <p><b>폰트는 반드시 임베드해야 한다.</b> openhtmltopdf 는 시스템 폰트를 쓰지 않는다.
 * 등록하지 않으면 한글이 전부 빈칸으로 나온다 — 오류 없이 조용히 비어서 더 위험하다.
 *
 * <p><b>가변 폰트(VF)를 쓰면 안 된다.</b> PDFBox 가 {@code wght} 축을 읽지 못해 축 기본값 인스턴스로
 * 그리는데, 그게 얇은 쪽이면 글자가 가늘게 나온다. 가는 획은 작은 크기에서 회색으로 보여
 * 글자색을 검정으로 바꿔도 옅어 보인다. 그래서 굵기별 정적 파일을 따로 등록한다.
 */
@Slf4j
@Component
public class ContractPdfRenderer implements ContractPdfPort {

    private static final String TEMPLATE = "contract/contract-pdf";
    private static final String FONT_FAMILY = "NotoSansKR";
    private static final String REGULAR_PATH = "fonts/NotoSansKR-Regular.ttf";
    private static final String BOLD_PATH = "fonts/NotoSansKR-Bold.ttf";
    private static final int REGULAR_WEIGHT = 400;
    private static final int BOLD_WEIGHT = 700;

    private final TemplateEngine templateEngine;

    /**
     * 폰트를 임시 파일로 풀어 둔다.
     *
     * <p>openhtmltopdf 의 폰트 등록이 {@code File} 을 요구하는데, 배포하면 폰트가 jar 안에 들어가
     * 파일 경로로 잡히지 않는다. 기동 때 한 번만 풀고 이후 렌더링은 그 파일을 재사용한다.
     */
    private final Path regularFont;
    private final Path boldFont;

    public ContractPdfRenderer(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
        this.regularFont = extractFont(REGULAR_PATH);
        this.boldFont = extractFont(BOLD_PATH);
    }

    @Override
    public byte[] render(ContractPdfView view) {
        Context context = new Context();
        context.setVariables(view.toModel());

        String html = templateEngine.process(TEMPLATE, context);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);

            // 굵기를 명시해 등록해야 제목(h1·h2)이 실제로 굵어진다. 하나만 등록하면 PDFBox 가
            // 없는 굵기를 합성해 획이 뭉갠다.
            if (regularFont != null) {
                builder.useFont(regularFont.toFile(), FONT_FAMILY, REGULAR_WEIGHT,
                        BaseRendererBuilder.FontStyle.NORMAL, true);
            }
            if (boldFont != null) {
                builder.useFont(boldFont.toFile(), FONT_FAMILY, BOLD_WEIGHT,
                        BaseRendererBuilder.FontStyle.NORMAL, true);
            }
            builder.run();
            return out.toByteArray();

        } catch (Exception e) {
            log.error("계약서 PDF 생성 실패. contractNo={}", view.contractNo(), e);
            throw new BusinessException(ContractErrorCode.PDF_RENDER_FAILED);
        }
    }

    /**
     * 폰트가 없으면 null 을 돌려주고 렌더링은 계속한다.
     *
     * <p>Regular 가 없으면 한글이 통째로 비고, Bold 만 없으면 제목 굵기만 빠진다. 둘 다 렌더링을
     * 막을 정도는 아니라 로그만 남긴다. 계약서를 못 뽑는 것보다는 낫다.
     */
    private Path extractFont(String fontPath) {
        ClassPathResource resource = new ClassPathResource(fontPath);
        if (!resource.exists()) {
            log.error("[계약서 PDF] 폰트가 없습니다: {}. 한글이 비거나 굵기가 빠집니다.", fontPath);
            return null;
        }

        try (InputStream in = resource.getInputStream()) {
            Path temp = Files.createTempFile("pairing-contract-font", ".ttf");
            temp.toFile().deleteOnExit();
            Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return temp;

        } catch (IOException e) {
            log.error("[계약서 PDF] 폰트를 풀지 못했습니다: {}", fontPath, e);
            return null;
        }
    }
}
