package com.kosscchthon.Icelink.survey;

import com.kosscchthon.Icelink.common.auth.CurrentUser;
import com.kosscchthon.Icelink.common.config.WebMvcConfig;
import com.kosscchthon.Icelink.survey.dto.SurveyResponse;
import com.kosscchthon.Icelink.survey.dto.SurveySubmitRequest;
import com.kosscchthon.Icelink.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 참가자 설문. 문항 텍스트는 프론트 하드코딩, 서버는 번호·점수·카테고리만 받는다. */
@RestController
@RequestMapping(WebMvcConfig.API_BASE + "/rooms/{code}/me/survey")
@RequiredArgsConstructor
@Tag(name = "Survey", description = "성격 6문항(1~5점) + 관심사 카테고리 1개")
public class SurveyController {

    private final SurveyService surveyService;

    @PutMapping
    @Operation(summary = "설문 제출",
            description = "personality(6문항)·interestCategory 중 하나 이상. 보낸 블록은 전체 덮어쓰기. "
                    + "둘 다 완료되면 status 가 SURVEY_DONE 이 된다. 방 WAITING, 참가자 팀 배정 전에만 가능.")
    @ApiResponses({
            @ApiResponse(responseCode = "200"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR — 문항 수/번호/점수 범위, 둘 다 누락"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN — 이 방의 참가자가 아님"),
            @ApiResponse(responseCode = "409", description = "INVALID_STATE_TRANSITION — 방이 WAITING 아님 / 이미 팀 배정")
    })
    public SurveyResponse submit(@PathVariable String code, @CurrentUser User user,
                                 @Valid @RequestBody SurveySubmitRequest request) {
        return surveyService.submit(code, user, request);
    }

    @GetMapping
    @Operation(summary = "내 설문 조회", description = "문항별 답, 합산 점수, 구간, 카테고리, 완료 여부")
    public SurveyResponse get(@PathVariable String code, @CurrentUser User user) {
        return surveyService.get(code, user);
    }
}
