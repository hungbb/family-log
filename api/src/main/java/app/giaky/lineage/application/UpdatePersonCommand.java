package app.giaky.lineage.application;

import app.giaky.common.types.PartialDate;
import app.giaky.lineage.domain.Gender;
import app.giaky.lineage.domain.LifeStatus;

/**
 * Edit a person (doc 05 §4.7).
 *
 * <p>One command, two meanings, chosen by the method that receives it. {@code PersonService.replace}
 * treats a null field as "clear this" — the {@code PUT} contract — and {@code PersonService.patch}
 * treats it as "leave this alone". Two records would have identical fields and different Javadoc,
 * which is a distinction the compiler cannot keep and a reader would stop trusting.
 *
 * <p>Nothing structural is in here. Branch, parents, spouses and the linked account each have their
 * own operation with its own permission check, because a single method that could change all of them
 * is one whose check cannot be stated in a sentence.
 *
 * @param expectedVersion the version the client last read; a mismatch is {@code 409
 *     VERSION_CONFLICT} (doc 05 §1)
 */
public record UpdatePersonCommand(
        String fullName,
        String familyName,
        String givenName,
        String alias,
        String posthumousName,
        Gender gender,
        LifeStatus lifeStatus,
        PartialDate birth,
        PartialDate death,
        String burialPlace,
        Integer birthOrder,
        String notesHtml,
        long expectedVersion) {}
