package com.matcher.matchmate_backend.dto.response;

import com.matcher.matchmate_backend.model.enums.auth.VerificationDocumentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityVerificationResponse {

    private Boolean isIdentityVerified;
    private VerificationDocumentType verificationDocumentType;
    private String maskedDocumentId;
    private OffsetDateTime verifiedAt;
    private String message;
}
