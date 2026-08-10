package com.pairing.contract.infrastructure.pdf;

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
 */
@Slf4j
@Component
public class ContractPdfRenderer implements ContractPdfPort {

    private static final String TEMPLATE = "contract/contract-pdf";
    private static final String FONT_PATH = "fonts/NotoSansKR-VF.ttf";
    private static final String FONT_FAMILY = "NotoSansKR";

    private final TemplateEngine templateEngine;

    /**
     * 폰트를 임시 파일로 풀어 둔다.
     *
     * <p>openhtmltopdf 의 폰트 등록이 {@code File} 을 요구하는데, 배포하면 폰트가 jar 안에 들어가
     * 파일 경로로 잡히지 않는다. 기동 때 한 번만 풀고 이후 렌더링은 그 파일을 재사용한다.
     */
    private final Path fontFile;

    public ContractPdfRenderer(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
        this.fontFile = extractFont();
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

            if (fontFile != null) {
                builder.useFont(fontFile.toFile(), FONT_FAMILY);
            }
            builder.run();
            return out.toByteArray();

        } catch (Exception e) {
            log.error("계약서 PDF 생성 실패. contractNo={}", view.contractNo(), e);
            throw new BusinessException(ContractErrorCode.PDF_RENDER_FAILED);
        }
    }

    /** 폰트가 없으면 null 을 돌려주고 렌더링은 계속한다. 그 경우 한글이 비므로 경고를 크게 남긴다. */
    private Path extractFont() {
        ClassPathResource resource = new ClassPathResource(FONT_PATH);
        if (!resource.exists()) {
            log.error("[계약서 PDF] 폰트가 없습니다: {}. PDF 의 한글이 비어 나옵니다.", FONT_PATH);
            return null;
        }

        try (InputStream in = resource.getInputStream()) {
            Path temp = Files.createTempFile("pairing-contract-font", ".ttf");
            temp.toFile().deleteOnExit();
            Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return temp;

        } catch (IOException e) {
            log.error("[계약서 PDF] 폰트를 풀지 못했습니다. PDF 의 한글이 비어 나옵니다.", e);
            return null;
        }
    }
}
