package app.giaky.identity.api.dto;

import app.giaky.identity.application.Registration;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Doc 05 §4.1: {@code 201 { userId, status }}. No tokens — registration does not sign anyone in. */
@Schema(description = "The newly created account.")
public record RegisterResponse(UUID userId, String status) {

    public static RegisterResponse of(Registration registration) {
        return new RegisterResponse(
                registration.userId().value(), registration.status().name());
    }
}
