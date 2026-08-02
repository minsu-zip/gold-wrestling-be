package com.goldwrestling.auth.kakao

import com.goldwrestling.config.KakaoProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/**
 * 카카오 REST API(`/oauth/token`, `/v2/user/me`) 호출 전담 컴포넌트.
 *
 * **트랜잭션 애노테이션을 붙이지 않는다** — 이 클래스는 DB를 만지지 않고, 호출부
 * ([com.goldwrestling.auth.KakaoAuthService])도 이 두 메서드를 트랜잭션 밖에서 부른다
 * (conventions §7 "트랜잭션 안에서 외부 API 호출 금지"). 카카오가 느려도 DB 커넥션을 붙잡지 않는다.
 *
 * 실패는 두 갈래로만 번역한다:
 * - 카카오 4xx → [KakaoAuthFailedException] (인가 코드·redirect_uri 문제 — 사용자가 다시 시도해야 함)
 * - 카카오 5xx·타임아웃·연결 실패 → [KakaoUnavailableException] (카카오 측 장애, T-02-23)
 *
 * 로그에는 실패 사실과 HTTP 상태만 남긴다. **인가 코드·client_secret·카카오 액세스 토큰·응답 본문은
 * 로그에 남기지 않는다**(T-02-19) — 카카오 에러 응답 본문에 요청 파라미터가 그대로 반사될 수 있고,
 * 이 값들 자체가 곧 자격증명이기 때문이다.
 */
@Component
class KakaoApiClient(
    @Qualifier("kakaoRestClient") private val kakaoRestClient: RestClient,
    private val kakaoProperties: KakaoProperties,
) {
    /**
     * 인가 코드를 카카오 액세스 토큰으로 교환한다.
     * `redirect_uri`는 요청 파라미터가 아니라 [KakaoProperties.redirectUri]에서 읽는다(D-046) —
     * 클라이언트가 임의의 redirect_uri를 보내 이 검증을 우회하지 못하게 서버가 값을 고정한다.
     */
    fun exchangeToken(code: String): KakaoTokenResponse {
        val body =
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "authorization_code")
                add("client_id", kakaoProperties.restApiKey)
                add("redirect_uri", kakaoProperties.redirectUri)
                add("code", code)
                add("client_secret", kakaoProperties.clientSecret)
            }
        return callKakao("토큰 교환") {
            kakaoRestClient
                .post()
                .uri(kakaoProperties.tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .withKakaoErrorHandling()
                .requiredBody(KakaoTokenResponse::class.java)
        }
    }

    /** 카카오 액세스 토큰으로 사용자 정보를 조회해 카카오 회원번호([KakaoUserResponse.id])만 반환한다. */
    fun fetchKakaoId(kakaoAccessToken: String): Long {
        val user =
            callKakao("사용자 조회") {
                kakaoRestClient
                    .get()
                    .uri(kakaoProperties.userInfoUri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $kakaoAccessToken")
                    .retrieve()
                    .withKakaoErrorHandling()
                    .requiredBody(KakaoUserResponse::class.java)
            }
        return user.id
    }

    /** 4xx/5xx는 [RestClient.ResponseSpec.onStatus]가 이미 도메인 예외로 바꿔 던진다 — 여기서는
     *  연결 실패·타임아웃 등 [RestClientException]만 [KakaoUnavailableException]으로 번역한다. */
    private fun <T> callKakao(
        operation: String,
        call: () -> T,
    ): T =
        try {
            call()
        } catch (e: KakaoAuthFailedException) {
            throw e
        } catch (e: KakaoUnavailableException) {
            throw e
        } catch (e: RestClientException) {
            logger.warn("카카오 API 호출 실패($operation) — 연결 실패/타임아웃: {}", e.javaClass.simpleName)
            throw KakaoUnavailableException()
        }

    private fun RestClient.ResponseSpec.withKakaoErrorHandling(): RestClient.ResponseSpec =
        this
            .onStatus({ status: HttpStatusCode -> status.is4xxClientError }) { _, response ->
                logger.warn("카카오 API가 인증 실패를 반환함 — status={}", response.statusCode)
                throw KakaoAuthFailedException()
            }.onStatus({ status: HttpStatusCode -> status.is5xxServerError }) { _, response ->
                logger.warn("카카오 API가 서버 오류를 반환함 — status={}", response.statusCode)
                throw KakaoUnavailableException()
            }

    companion object {
        private val logger = LoggerFactory.getLogger(KakaoApiClient::class.java)
    }
}
