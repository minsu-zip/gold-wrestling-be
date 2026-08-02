package com.goldwrestling.auth.kakao

import com.goldwrestling.common.error.ErrorCode
import com.goldwrestling.config.KakaoProperties
import com.goldwrestling.support.KakaoApiMockSupport
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.io.IOException

/**
 * [KakaoApiClient]의 HTTP 요청 형태·실패 번역을 실제 네트워크 없이 검증하는 순수 단위테스트다.
 * `MockRestServiceServer`가 `RestClient.Builder`에 바로 바인딩되므로 스프링 컨텍스트를 띄우지 않고도
 * 스프링 부트 테스트 애노테이션 없이도 검증할 수 있다.
 */
class KakaoApiClientTest {
    private val kakaoProperties =
        KakaoProperties(
            restApiKey = "test-rest-api-key",
            clientSecret = "test-client-secret-value",
            redirectUri = "https://example.test/oauth/callback",
            tokenUri = "https://kauth.kakao.test/oauth/token",
            userInfoUri = "https://kapi.kakao.test/v2/user/me",
        )

    private fun newClient(): Pair<KakaoApiClient, MockRestServiceServer> {
        val builder = RestClient.builder()
        val server = KakaoApiMockSupport.bindKakaoMock(builder)
        return KakaoApiClient(builder.build(), kakaoProperties) to server
    }

    @Test
    fun `인가 코드로 토큰 교환을 요청하면 카카오 oauth token 으로 form-urlencoded POST 가 나간다`() {
        val (client, server) = newClient()
        server
            .expect(requestTo(kakaoProperties.tokenUri))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andRespond(withSuccess(TOKEN_RESPONSE_BODY, MediaType.APPLICATION_JSON))

        val response = client.exchangeToken("auth-code-123")

        assertEquals("kakao-access-token-for-test", response.accessToken)
        server.verify()
    }

    @Test
    fun `요청 본문에 grant_type client_id redirect_uri code client_secret 이 포함된다`() {
        val (client, server) = newClient()
        server
            .expect(requestTo(kakaoProperties.tokenUri))
            .andExpect(method(HttpMethod.POST))
            .andExpect(
                content().formDataContains(
                    mapOf(
                        "grant_type" to "authorization_code",
                        "client_id" to kakaoProperties.restApiKey,
                        "redirect_uri" to kakaoProperties.redirectUri,
                        "code" to "auth-code-123",
                        "client_secret" to kakaoProperties.clientSecret,
                    ),
                ),
            ).andRespond(withSuccess(TOKEN_RESPONSE_BODY, MediaType.APPLICATION_JSON))

        client.exchangeToken("auth-code-123")

        server.verify()
    }

    @Test
    fun `redirect_uri 는 요청 파라미터가 아니라 KakaoProperties 값을 서버가 고정해서 보낸다`() {
        val (client, server) = newClient()
        server
            .expect(requestTo(kakaoProperties.tokenUri))
            .andExpect(content().formDataContains(mapOf("redirect_uri" to kakaoProperties.redirectUri)))
            .andRespond(withSuccess(TOKEN_RESPONSE_BODY, MediaType.APPLICATION_JSON))

        client.exchangeToken("auth-code-123")

        server.verify()
    }

    @Test
    fun `카카오 액세스 토큰으로 사용자 조회를 하면 Authorization Bearer 헤더가 붙어 GET 이 나간다`() {
        val (client, server) = newClient()
        server
            .expect(requestTo(kakaoProperties.userInfoUri))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer kakao-access-token-xyz"))
            .andRespond(withSuccess(USER_RESPONSE_BODY, MediaType.APPLICATION_JSON))

        client.fetchKakaoId("kakao-access-token-xyz")

        server.verify()
    }

    @Test
    fun `사용자 조회 응답에서 숫자 id 를 kakaoId 로 읽는다`() {
        val (client, server) = newClient()
        server
            .expect(requestTo(kakaoProperties.userInfoUri))
            .andRespond(withSuccess(USER_RESPONSE_BODY, MediaType.APPLICATION_JSON))

        val kakaoId = client.fetchKakaoId("kakao-access-token-xyz")

        assertEquals(9876543210L, kakaoId)
    }

    @Test
    fun `카카오가 400을 반환하면 KakaoAuthFailedException(401) 을 던진다`() {
        val (client, server) = newClient()
        KakaoApiMockSupport.expectTokenExchangeFailure(server, kakaoProperties.tokenUri, HttpStatus.BAD_REQUEST)

        val ex = assertThrows(KakaoAuthFailedException::class.java) { client.exchangeToken("bad-code") }

        assertEquals(ErrorCode.KAKAO_AUTH_FAILED, ex.errorCode)
        assertEquals(HttpStatus.UNAUTHORIZED, ex.status)
    }

    @Test
    fun `카카오가 401을 반환해도 KakaoAuthFailedException 을 던진다`() {
        val (client, server) = newClient()
        KakaoApiMockSupport.expectTokenExchangeFailure(server, kakaoProperties.tokenUri, HttpStatus.UNAUTHORIZED)

        assertThrows(KakaoAuthFailedException::class.java) { client.exchangeToken("bad-code") }
    }

    @Test
    fun `카카오가 500을 반환하면 KakaoUnavailableException(502) 을 던진다`() {
        val (client, server) = newClient()
        KakaoApiMockSupport.expectServerError(server, kakaoProperties.tokenUri)

        val ex = assertThrows(KakaoUnavailableException::class.java) { client.exchangeToken("code") }

        assertEquals(ErrorCode.KAKAO_UNAVAILABLE, ex.errorCode)
        assertEquals(HttpStatus.BAD_GATEWAY, ex.status)
    }

    @Test
    fun `사용자 조회에서도 카카오 5xx는 KakaoUnavailableException 으로 번역된다`() {
        val (client, server) = newClient()
        KakaoApiMockSupport.expectServerError(server, kakaoProperties.userInfoUri)

        assertThrows(KakaoUnavailableException::class.java) { client.fetchKakaoId("access-token") }
    }

    @Test
    fun `카카오 호출이 연결 실패 타임아웃으로 끝나면 KakaoUnavailableException 을 던진다`() {
        val (client, server) = newClient()
        server
            .expect(requestTo(kakaoProperties.tokenUri))
            .andRespond { throw IOException("connection refused (테스트로 재현한 연결 실패)") }

        val ex = assertThrows(KakaoUnavailableException::class.java) { client.exchangeToken("code") }

        assertEquals(ErrorCode.KAKAO_UNAVAILABLE, ex.errorCode)
    }

    @Test
    fun `인증 실패 예외 메시지에 client_secret 이나 인가 코드 값이 들어가지 않는다`() {
        val (client, server) = newClient()
        KakaoApiMockSupport.expectTokenExchangeFailure(server, kakaoProperties.tokenUri, HttpStatus.BAD_REQUEST)

        val ex = assertThrows(KakaoAuthFailedException::class.java) { client.exchangeToken("secret-code-abc") }

        assertThat(ex.message, not(containsString(kakaoProperties.clientSecret)))
        assertThat(ex.message, not(containsString("secret-code-abc")))
    }

    private companion object {
        const val TOKEN_RESPONSE_BODY =
            """{"token_type":"bearer","access_token":"kakao-access-token-for-test","expires_in":21599,""" +
                """"refresh_token":"kakao-refresh-token-for-test","scope":"account_email profile_nickname"}"""
        const val USER_RESPONSE_BODY = """{"id":9876543210,"connected_at":"2026-08-02T00:00:00Z"}"""
    }
}
