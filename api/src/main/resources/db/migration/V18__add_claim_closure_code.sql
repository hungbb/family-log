-- V18 — why the system declined a claim, as a code the client translates (phase 6 follow-up).
--
-- A link answers the claims it makes impossible: other claims on the same node, and the linked
-- member's claims on other nodes. Those are declined by the system, and their explanation used to
-- be English text in decision_reason, which a Vietnamese member then read in English. A code lets
-- the client word it in the reader's language. decision_reason stays for what an approver types.

ALTER TABLE node_claim_requests
    ADD COLUMN closure_code VARCHAR(30);

-- The one sentence the system wrote before this migration, re-expressed as its code.
UPDATE node_claim_requests
SET closure_code    = 'NODE_TAKEN',
    decision_reason = NULL
WHERE status = 'REJECTED'
  AND decision_reason = 'Another claim on this person was approved.';

ALTER TABLE node_claim_requests
    ADD CONSTRAINT ck_claim_closure_code CHECK (
        closure_code IS NULL
            OR (closure_code IN ('NODE_TAKEN', 'REQUESTER_PLACED')
            AND status = 'REJECTED'
            AND decision_reason IS NULL)
        );

COMMENT ON COLUMN node_claim_requests.closure_code IS
    'Set when a link, not an approver, declined the claim. The client translates it; '
        'decision_reason is null on these rows.';
