package app.giaky.platform.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Where to send the test message. */
@Schema(description = "Send a test message through the stored mail settings.")
public record SendTestMailRequest(@NotBlank @Email String to) {}
