package com.pairing.support.infrastructure.persistence;

import com.pairing.support.domain.model.Inquiry;
import com.pairing.support.domain.model.InquiryStatus;
import com.pairing.support.domain.repository.InquiryRepository;
import com.pairing.support.infrastructure.mapper.InquiryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Repository
@RequiredArgsConstructor
public class InquiryRepositoryAdapter implements InquiryRepository {

    /** 화면에 보이는 문의번호 형식("QNA-20260805-0012")에서 id 부분만 뽑아낸다. */
    private static final Pattern INQUIRY_NO_PATTERN = Pattern.compile("QNA-\\d{8}-0*(\\d+)",
            Pattern.CASE_INSENSITIVE);

    private final SpringDataInquiryRepository springDataRepository;
    private final InquiryMapper inquiryMapper;

    @Override
    public Inquiry save(Inquiry inquiry) {
        return inquiryMapper.toDomain(springDataRepository.save(inquiryMapper.toJpaEntity(inquiry)));
    }

    @Override
    public Optional<Inquiry> findById(Long id) {
        return springDataRepository.findById(id).map(inquiryMapper::toDomain);
    }

    @Override
    public Page<Inquiry> findByWriterAccountId(Long writerAccountId, Pageable pageable) {
        return springDataRepository.findByWriterAccountId(writerAccountId, pageable).map(inquiryMapper::toDomain);
    }

    @Override
    public Page<Inquiry> findByWriterAccountIdAndStatus(Long writerAccountId, InquiryStatus status,
                                                        Pageable pageable) {
        return springDataRepository.findByWriterAccountIdAndStatus(writerAccountId, status, pageable)
                .map(inquiryMapper::toDomain);
    }

    /**
     * 화면에 표시된 문의번호를 그대로 붙여넣은 경우("QNA-20260805-0012")엔 id 부분만 뽑고,
     * 그게 아니면 입력값의 숫자만 모아서 id 검색에 쓴다(예: "12" 만 입력해도 매칭).
     */
    private String extractDigits(String value) {
        Matcher matcher = INQUIRY_NO_PATTERN.matcher(value);
        if (matcher.find()) {
            return matcher.group(1);
        }
        String digits = value.replaceAll("\\D", "");
        return digits.isBlank() ? null : digits;
    }

}
