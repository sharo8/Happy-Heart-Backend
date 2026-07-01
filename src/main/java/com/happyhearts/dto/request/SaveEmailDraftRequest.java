package com.happyhearts.dto.request;

import lombok.Data;

@Data
public class SaveEmailDraftRequest {
    private java.util.List<java.util.UUID> toUserIds;
    private java.util.List<java.util.UUID> ccUserIds;
    private String subject;
    private String body;
    private java.util.UUID draftId;
}
