package app.giaky.linking.application;

import app.giaky.lineage.application.PersonSummaryView;
import java.util.List;

/**
 * One candidate in the unlinked-node browser (B6.6, F6.2).
 *
 * <p>The node plus the two facts that let somebody recognise themselves in it: who their parents
 * were, and who their children are. Birth years would be the obvious discriminator and are usually
 * missing from the book; the family around a person is what the book always has.
 */
public record UnlinkedCandidateView(
        PersonSummaryView person, List<String> parentNames, List<String> childNames) {}
