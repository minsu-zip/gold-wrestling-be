package com.goldwrestling.auth

import com.goldwrestling.common.error.DomainException
import com.goldwrestling.common.error.ErrorCode

/**
 * refresh 토큰이 없음/만료/폐기됨/재사용 감지됨 중 어느 사유든 동일한 메시지·에러코드로 응답한다.
 *
 * 사유를 응답에서 구분하지 않는 이유(T-02-11): 공격자가 제시한 토큰이 "DB에 존재했지만 만료됐다"와
 * "애초에 DB에 없다"를 구분해 응답하면, 그 응답만으로 어떤 토큰 문자열이 한때 유효했는지 유추할 수
 * 있는 정보를 흘리게 된다. 구분은 [TokenService]의 `logger.warn` 서버 로그로만 남긴다(토큰 값 자체는
 * 로그에도 남기지 않는다).
 */
class RefreshTokenInvalidException(
    message: String = "다시 로그인해 주세요.",
) : DomainException(ErrorCode.REFRESH_TOKEN_INVALID, message)
