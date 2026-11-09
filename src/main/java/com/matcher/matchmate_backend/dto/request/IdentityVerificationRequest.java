package com.matcher.matchmate_backend.dto.request;

import com.matcher.matchmate_backend.model.enums.auth.VerificationDocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityVerificationRequest {

    @NotNull(message = "documentType is required")
    private VerificationDocumentType documentType;

    @NotBlank(message = "documentNumber is required")
    private String documentNumber;

    @NotBlank(message = "nameOnDocument is required")
    private String nameOnDocument;
}
