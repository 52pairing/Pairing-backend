package com.pairing.client.application.usecase;

import com.pairing.client.application.result.ClientMyPageResult;

public interface ClientQueryUseCase {

    ClientMyPageResult findMyPage(Long accountId);
}
