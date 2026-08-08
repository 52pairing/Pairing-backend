package com.pairing.project.application.usecase;

import com.pairing.project.application.command.CreateProjectCommand;

/** 프로젝트 상태를 바꾸는 인바운드 포트. */
public interface ProjectCommandUseCase {

    /** 등록. 상태는 REGISTERED 로 시작하며 착수금 결제 후 모집이 시작된다. */
    Long create(CreateProjectCommand command);
}