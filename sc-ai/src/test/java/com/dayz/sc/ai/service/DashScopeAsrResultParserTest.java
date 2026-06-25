package com.dayz.sc.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DashScopeAsrResultParserTest {

    private final DashScopeAsrResultParser parser = new DashScopeAsrResultParser(new ObjectMapper());

    @Test
    void parseShouldReturnFinalAndPartialSentencesFromResultGeneratedEvent() {
        String message = """
                {
                  "header": {"event": "result-generated"},
                  "payload": {
                    "output": {
                      "sentence": {
                        "text": "第一句已经结束",
                        "sentence_end": true,
                        "begin_time": 0,
                        "end_time": 1200
                      },
                      "sentences": [
                        {
                          "sentence_text": "第二句还在识别",
                          "sentenceEnd": false,
                          "beginTime": 1300,
                          "endTime": 2100
                        }
                      ]
                    }
                  }
                }
                """;

        List<DashScopeAsrClient.AsrTranscript> transcripts = parser.parse(message);

        assertThat(transcripts).hasSize(2);
        assertThat(transcripts.getFirst())
                .extracting(
                        DashScopeAsrClient.AsrTranscript::text,
                        DashScopeAsrClient.AsrTranscript::sentenceEnd,
                        DashScopeAsrClient.AsrTranscript::beginTimeMs,
                        DashScopeAsrClient.AsrTranscript::endTimeMs)
                .containsExactly("第一句已经结束", true, 0, 1200);
        assertThat(transcripts.get(1))
                .extracting(
                        DashScopeAsrClient.AsrTranscript::text,
                        DashScopeAsrClient.AsrTranscript::sentenceEnd,
                        DashScopeAsrClient.AsrTranscript::beginTimeMs,
                        DashScopeAsrClient.AsrTranscript::endTimeMs)
                .containsExactly("第二句还在识别", false, 1300, 2100);
    }

    @Test
    void extractEventAndErrorMessageShouldHandleTaskEvents() {
        String message = """
                {
                  "header": {
                    "event": "task-failed",
                    "error_message": "invalid sample rate"
                  }
                }
                """;

        assertThat(parser.extractEvent(message)).isEqualTo("task-failed");
        assertThat(parser.extractErrorMessage(message)).isEqualTo("invalid sample rate");
    }
}
