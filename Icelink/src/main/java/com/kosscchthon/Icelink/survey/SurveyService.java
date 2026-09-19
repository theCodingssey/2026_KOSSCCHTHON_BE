package com.kosscchthon.Icelink.survey;

import com.kosscchthon.Icelink.common.error.ErrorCode;
import com.kosscchthon.Icelink.common.error.IcelinkException;
import com.kosscchthon.Icelink.participant.Participant;
import com.kosscchthon.Icelink.participant.ParticipantAccessChecker;
import com.kosscchthon.Icelink.participant.ParticipantRepository;
import com.kosscchthon.Icelink.participant.ParticipantStatus;
import com.kosscchthon.Icelink.realtime.RoomEvent;
import com.kosscchthon.Icelink.realtime.RoomEventPublisher;
import com.kosscchthon.Icelink.realtime.RoomEventType;
import com.kosscchthon.Icelink.room.Room;
import com.kosscchthon.Icelink.room.RoomAccessChecker;
import com.kosscchthon.Icelink.room.RoomStatus;
import com.kosscchthon.Icelink.survey.dto.SurveyResponse;
import com.kosscchthon.Icelink.survey.dto.SurveySubmitRequest;
import com.kosscchthon.Icelink.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SurveyService {

    private final RoomAccessChecker roomAccessChecker;
    private final ParticipantAccessChecker participantAccessChecker;
    private final ParticipantRepository participantRepository;
    private final RoomEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * PUT /rooms/{code}/me/survey — 설문 제출 (S-02~S-06).
     * 방 WAITING, 참가자 JOINED/SURVEY_DONE 에서만. 보낸 블록은 전체 덮어쓰기.
     * 이번 호출로 SURVEY_DONE 이 되면 PARTICIPANT_SURVEY_DONE 이벤트를 발행한다.
     */
    @Transactional
    public SurveyResponse submit(String code, User user, SurveySubmitRequest request) {
        if (request == null || request.isEmpty()) {
            throw new IcelinkException(ErrorCode.VALIDATION_ERROR, "요청 값이 올바르지 않습니다.",
                    Map.of("errors", List.of(Map.of("field", "personality",
                            "message", "personality 또는 interestCategory 중 하나 이상을 보내야 합니다"))));
        }

        Room room = roomAccessChecker.getByCode(code);
        Participant me = participantAccessChecker.requireParticipant(room, user.getUserKey());
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new IcelinkException(ErrorCode.INVALID_STATE_TRANSITION,
                    "설문은 방이 대기 중(WAITING)일 때만 제출할 수 있습니다. (현재 상태: " + room.getStatus() + ")");
        }

        boolean wasDone = me.isSurveyDone();

        if (request.personality() != null) {
            me.submitPersonality(toAnswerMap(request.personality()));
        }
        if (request.interestCategory() != null) {
            me.selectCategory(request.interestCategory());
        }

        if (!wasDone && me.isSurveyDone()) {
            publishSurveyDone(room, me);
        }
        return SurveyResponse.from(me);
    }

    /** GET /rooms/{code}/me/survey */
    @Transactional(readOnly = true)
    public SurveyResponse get(String code, User user) {
        Participant me = participantAccessChecker.requireParticipant(code, user.getUserKey());
        return SurveyResponse.from(me);
    }

    // ---- 내부 ----

    /** Bean Validation 은 개별 값 범위만 보므로, 번호 중복·누락은 여기서 잡는다. */
    private static Map<Integer, Integer> toAnswerMap(List<SurveySubmitRequest.PersonalityAnswer> answers) {
        Map<Integer, Integer> map = new HashMap<>();
        for (SurveySubmitRequest.PersonalityAnswer a : answers) {
            if (map.put(a.no(), a.score()) != null) {
                throw validation("문항 번호 " + a.no() + " 이(가) 중복되었습니다");
            }
        }
        try {
            PersonalitySurvey.validateAndSum(map);
        } catch (IllegalArgumentException e) {
            throw validation(e.getMessage());
        }
        return map;
    }

    private static IcelinkException validation(String message) {
        return new IcelinkException(ErrorCode.VALIDATION_ERROR, "요청 값이 올바르지 않습니다.",
                Map.of("errors", List.of(Map.of("field", "personality", "message", message))));
    }

    private void publishSurveyDone(Room room, Participant me) {
        // 상태 변경 후 count 쿼리 → auto-flush 로 이번 참가자가 포함된 값이 나온다.
        int surveyDone = participantRepository.countByRoom_IdAndStatus(room.getId(), ParticipantStatus.SURVEY_DONE);
        int total = participantRepository.countByRoom_IdAndStatusNot(room.getId(), ParticipantStatus.LEFT);
        eventPublisher.publish(RoomEvent.room(room.getId(), RoomEventType.PARTICIPANT_SURVEY_DONE, Map.of(
                "participantId", me.getId(),
                "surveyDoneCount", surveyDone,
                "participantCount", total
        ), Instant.now(clock)));
    }
}
