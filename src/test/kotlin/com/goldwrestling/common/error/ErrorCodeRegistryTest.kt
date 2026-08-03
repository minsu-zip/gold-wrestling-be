package com.goldwrestling.common.error

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * `ErrorCode` enum과 `docs/error-codes.md` 표가 서로 어긋나지 않는지 빌드에서 강제한다 (T-02-02, D-028).
 *
 * 스프링 컨텍스트를 띄우지 않는 순수 단위테스트다. 테스트 작업 디렉터리는 프로젝트 루트이므로
 * `docs/error-codes.md`를 상대경로로 바로 읽을 수 있다.
 *
 * 양방향으로 검증한다:
 * - enum → 문서: [ErrorCode.entries] 전부가 문서 본문에 백틱으로 감싸여 등장해야 한다
 * - 문서 → enum: `## 공통 코드`·`## 인증·회원 코드` 표의 **첫 번째 열**에 있는 코드가 전부 enum에 있어야 한다
 *   (역방향은 표의 첫 열만 본다 — "발생 지점" 열의 클래스명도 백틱을 쓰므로 전체 텍스트를 정규식으로
 *   훑으면 `AdminAuthService` 같은 오탐이 섞인다)
 */
class ErrorCodeRegistryTest {
    private val docText = File("docs/error-codes.md").readText()

    @Test
    fun `모든 ErrorCode 는 docs 에러코드 표에 등재되어 있다`() {
        val missing = ErrorCode.entries.filter { code -> !docText.contains("`${code.name}`") }

        assertThat(missing).isEmpty()
    }

    @Test
    fun `docs 에러코드 표 첫 열에 있는 코드는 모두 ErrorCode 에 존재한다`() {
        val knownNames = ErrorCode.entries.map { it.name }.toSet()
        val tableCodeTokens = extractFirstColumnCodeTokens(docText)

        val unknown = tableCodeTokens.filterNot { it in knownNames }

        assertThat(unknown).isEmpty()
    }

    /**
     * `## 공통 코드`·`## 인증·회원 코드` 섹션의 표에서, `| \`CODE\` | ...` 형태 행의 첫 번째 열만 뽑는다.
     * `^[A-Z][A-Z_]{4,}$` 형태만 코드로 인정해 `TZ` 같은 짧은 오탐을 피한다.
     */
    private fun extractFirstColumnCodeTokens(text: String): List<String> {
        val targetSectionPrefixes = listOf("## 공통 코드", "## 인증·회원 코드")
        val codeTokenPattern = Regex("^[A-Z][A-Z_]{4,}$")

        var inTargetSection = false
        val tokens = mutableListOf<String>()

        for (line in text.lines()) {
            if (line.startsWith("## ")) {
                inTargetSection = targetSectionPrefixes.any { line.startsWith(it) }
                continue
            }

            if (!inTargetSection || !line.startsWith("| `")) continue

            val firstCell =
                line
                    .removePrefix("|")
                    .substringBefore("|")
                    .trim()
                    .trim('`')

            if (codeTokenPattern.matches(firstCell)) {
                tokens.add(firstCell)
            }
        }

        return tokens
    }
}
