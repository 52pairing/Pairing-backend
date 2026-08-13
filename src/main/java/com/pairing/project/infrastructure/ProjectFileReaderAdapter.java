package com.pairing.project.infrastructure;

import com.pairing.file.application.result.FileResult;
import com.pairing.file.application.usecase.FileQueryUseCase;
import com.pairing.project.application.port.ProjectFileReaderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** ProjectFileReaderPort 구현. file 도메인의 인바운드 포트를 호출한다. */
@Component
@RequiredArgsConstructor
public class ProjectFileReaderAdapter implements ProjectFileReaderPort {

    private final FileQueryUseCase fileQueryUseCase;

    @Override
    public List<ProjectFileView> getAllByIds(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        // 첨부는 최대 10건이라 건별 조회로 충분하다. 늘어나면 file 쪽에 findAllByIds 를 요청한다.
        return fileIds.stream().map(this::toView).toList();
    }

    @Override
    public Optional<byte[]> readContent(Long fileId) {
        return fileQueryUseCase.readContent(fileId);
    }

    private ProjectFileView toView(Long fileId) {
        FileResult file = fileQueryUseCase.getById(fileId);
        return new ProjectFileView(file.fileId(), file.originalName(), file.sizeBytes(), file.objectKey());
    }
}
