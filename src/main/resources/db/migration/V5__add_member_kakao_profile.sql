-- V5: 카카오 프로필(닉네임·프로필 이미지 URL) 보관 컬럼 추가 (D-083).
-- 이 두 값은 "내 정보" 화면 표시용 보조 정보다. 체육관 운영 기준 신원은 여전히 온보딩에서 회원이 직접
-- 입력한 실명(name)·전화번호(phone_number)이고(D-025), 카카오 값이 그것을 대체하지 않는다.
--
-- 둘 다 NULL 허용이다 (NOT NULL을 붙이지 않는다). 카카오 개발자 콘솔 동의항목(profile_nickname·
-- profile_image)이 아직 없거나, 회원이 동의를 거부·사후 철회하면 카카오가 값을 아예 주지 않는다 —
-- 값이 없는 상태가 오류가 아니라 정상 상태다.
--
-- 길이 근거: 카카오 닉네임은 정책상 20자 내외라 VARCHAR(100)이면 충분한 여유가 있고,
-- 프로필 이미지 URL은 k.kakaocdn.net 기준 100자 내외라 VARCHAR(500)이면 여유가 있다.
-- 인덱스·제약은 추가하지 않는다 — 두 컬럼 모두 조회 조건이나 유일성 판정에 쓰이지 않는다.

ALTER TABLE member
    ADD COLUMN kakao_nickname VARCHAR(100),
    ADD COLUMN kakao_profile_image_url VARCHAR(500);
