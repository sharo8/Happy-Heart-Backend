package com.happyhearts.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.happyhearts.config.GroqProperties;
import com.happyhearts.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are an HR attendance assistant for Happy Hearts. Current attendance data: %s.

            LANGUAGE (critical):
            - Reply in the SAME language as the user's latest message.
            - English message → English reply. French message → French reply. Kinyarwanda message → Kinyarwanda reply.
            - For greetings or small talk, match the user's language (e.g. "Good evening" → English greeting and offer to help).

            Business rules:
            - LATE means PRESENT (employee came to work, but after the scheduled time). Never treat late as absent.
            - daysPresent / presentRate: physical presence days (includes late and grace periods).
            - lateDays: subset of present days with late arrival.
            - absentDays: no entry scan that day.
            - For "most absent": sort by absentDays descending.
            - For "most present": sort by daysPresent descending (late counts as present).
            - For "who is late": use lateDays (> 0).

            Be concise and professional. Do not invent data — use only the provided context.
            If the answer includes a ranking, present it as a markdown table.
            """;

    private final GroqProperties groqProperties;
    private final AttendanceAiContextService attendanceAiContextService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public Map<String, Object> query(String question, String contextKey) {
        if (!StringUtils.hasText(groqProperties.getKey())) {
            throw new BusinessException("error.ai.groq.not.configured");
        }
        if (!"attendance".equalsIgnoreCase(contextKey)) {
            throw new BusinessException("error.ai.context.unsupported");
        }

        Map<String, Object> contextData = attendanceAiContextService.buildAttendanceContext();
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(contextData);
        } catch (Exception e) {
            throw new BusinessException("error.ai.context.serialize");
        }

        String systemPrompt = SYSTEM_PROMPT_TEMPLATE.formatted(contextJson);
        String answer = callGroq(systemPrompt, question);
        return Map.of(
                "answer", answer,
                "context", contextData
        );
    }

    @SuppressWarnings("unchecked")
    private String callGroq(String systemPrompt, String userQuestion) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqProperties.getKey());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", groqProperties.getModel());
        body.put("temperature", 0.3);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userQuestion)
        ));

        String url = groqProperties.getBaseUrl().replaceAll("/$", "") + "/chat/completions";
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url,
                    new HttpEntity<>(body, headers),
                    Map.class
            );
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                throw new BusinessException("error.ai.empty.response");
            }
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new BusinessException("error.ai.empty.response");
            }
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return String.valueOf(message.get("content"));
        } catch (HttpStatusCodeException ex) {
            log.error("Groq API error {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new BusinessException("error.ai.groq.failed");
        } catch (RestClientException ex) {
            log.error("Groq API call failed", ex);
            throw new BusinessException("error.ai.groq.failed");
        }
    }
}
