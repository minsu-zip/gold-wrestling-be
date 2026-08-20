package com.goldwrestling.support

import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

/**
 * 카카오 API(`kauth.kakao.com`, `kapi.kakao.com`) 호출을 실제 네트워크 없이 재현하는 테스트 헬퍼.
 *
 * `KakaoRestClientConfig.kakaoRestClient`가 만드는 `RestClient.Builder`에 `MockRestServiceServer`를
 * 바인딩해 응답을 목킹한다. 픽스처 JSON은 `src/test/resources/kakao/`에서 클래스패스로 읽는다.
 */
object KakaoApiMockSupport {
    /** [builder]에 목 서버를 바인딩한다. 이후 `builder.build()`로 만든 RestClient는 이 서버로만 응답받는다. */
    fun bindKakaoMock(builder: RestClient.Builder): MockRestServiceServer = MockRestServiceServer.bindTo(builder).build()

    /** 카카오 `/oauth/token` 성공 응답을 재현한다. */
    fun expectTokenExchange(
        server: MockRestServiceServer,
        tokenUri: String,
    ) {
        server
            .expect(requestTo(tokenUri))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(readFixture("token-response.json"), MediaType.APPLICATION_JSON))
    }

    /** 카카오 `/v2/user/me` 성공 응답을 재현한다. 픽스처의 더미 `id`를 [kakaoId]로 치환해 응답한다. */
    fun expectUserInfo(
        server: MockRestServiceServer,
        userInfoUri: String,
        kakaoId: Long,
    ) {
        val body = readFixture("user-response.json").replace("1234567890", kakaoId.toString())
        server
            .expect(requestTo(userInfoUri))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
    }

    /** 카카오가 잘못된 인가 코드 등으로 4xx를 반환하는 경우를 재현한다. */
    fun expectTokenExchangeFailure(
        server: MockRestServiceServer,
        tokenUri: String,
        status: HttpStatus,
    ) {
        server
            .expect(requestTo(tokenUri))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(status))
    }

    /** 카카오 5xx 장애 상황을 재현한다(`KAKAO_UNAVAILABLE` 경로 검증용). */
    fun expectServerError(
        server: MockRestServiceServer,
        uri: String,
    ) {
        server.expect(requestTo(uri)).andRespond(withServerError())
    }

    private fun readFixture(name: String): String = ClassPathResource("kakao/$name").inputStream.bufferedReader().use { it.readText() }
}
