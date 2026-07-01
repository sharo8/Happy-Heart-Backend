package com.happyhearts.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MessageReactionResponse {
    String emoji;
    int count;
    boolean reactedByMe;
}
