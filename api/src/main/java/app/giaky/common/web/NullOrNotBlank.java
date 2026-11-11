package app.giaky.common.web;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A field that may be left out, but may not be sent empty.
 *
 * <p>The shape every {@code PATCH} body has for a name: absent means "leave it alone", and that has
 * to stay legal, so {@code @NotBlank} is the wrong constraint — it refuses null. But a name that
 * <i>is</i> sent and consists of spaces is not a request to leave it alone; it is a request the
 * aggregate will refuse, and its refusal is an {@code IllegalArgumentException} that reaches the
 * client as {@code 500 INTERNAL_ERROR} with nothing to say which field was wrong.
 *
 * <p>Blank is {@link String#isBlank()}, the same test the domain constructors apply, rather than a
 * regular expression: {@code \s} and {@code Character.isWhitespace} disagree about U+3000, and a
 * constraint that passed what the aggregate then rejects would only move the 500 to rarer input.
 */
@Documented
@Constraint(validatedBy = NullOrNotBlank.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface NullOrNotBlank {

    String message() default "must not be blank";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Null is valid; anything else has to contain a character that is not whitespace. */
    final class Validator implements ConstraintValidator<NullOrNotBlank, CharSequence> {

        @Override
        public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
            return value == null || !value.toString().isBlank();
        }
    }
}
