package com.happyhearts.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(max = 100, message = "{validation.name.max}")
    private String firstName;

    @Size(max = 100, message = "{validation.name.max}")
    private String lastName;

    /** HTTPS URL or data:image/...;base64; omit or empty to clear. */
    @Size(max = 1_200_000, message = "{validation.profile.photo.max}")
    private String profilePhotoUrl;
}
