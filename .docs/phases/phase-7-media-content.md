# Phase 7 — Media, Content & Localization

**Goal:** the tree stops being a database and starts being a family record — photos, biographies, notifications, and a fully Vietnamese product (M5).

**Estimate:** ~1.5 weeks · **Depends on:** [Phase 6](phase-6-claiming-linking.md) · **Next:** [Phase 8](phase-8-hardening-release.md)

**Requirements:** FR-M1 … FR-M3, FR-X2, NFR-8

---

## 1. Scope

**In scope** — image upload for person avatars, user avatars, and clan covers; server-side re-encoding, EXIF stripping, and thumbnailing; the `MediaStorage` port with a local filesystem adapter storing files on the backend server (no third-party object storage yet); a rich-text editor for biographies with server-side sanitization; the in-app notification centre; complete `vi`/`en` translation and locale-aware formatting.

**Out of scope** — photo galleries per person (post-v1; the schema allows it, the UI ships one avatar), document/scan attachments (post-v1), video, email delivery (Phase 8), OCR of scanned family books.

## 2. Backend Tasks

| # | Task | Detail |
|---|------|--------|
| B7.1 | `MediaStorage` port + `LocalFsMediaStorage` storing files under a configured server directory (dev and prod) | Object-storage (S3-compatible) adapter deferred post-v1, behind the same port |
| B7.2 | `MediaService`: validate → re-encode → strip EXIF → generate variants (thumb 96px, card 256px, full ≤ 1600px) → persist `media_objects` | JPEG/PNG/WebP, ≤ 5 MB, content type sniffed server-side, never trusted from the client |
| B7.3 | Upload endpoints: `/me/avatar`, `/persons/{id}/avatar`, `/clans/{id}/cover` | Permission: `TREE_EDIT` on the node, or the linked user via the self-service exception |
| B7.4 | Media serving | A streaming endpoint serves image files from local storage; access requires clan membership. (Signed, time-limited URLs arrive with the post-v1 object-storage adapter) |
| B7.5 | Orphan cleanup job | Media unreferenced for > 7 days is deleted from storage |
| B7.6 | Rich-text sanitization hardening | OWASP Java HTML Sanitizer allow-list: headings, bold/italic, lists, links (`http(s)` only, `rel="noopener noreferrer"`), blockquote. No scripts, styles, iframes, or remote images |
| B7.7 | Plain-text projection of notes for search and for the list view | |
| B7.8 | `NotificationService` finalized | Types: join request received/decided, claim received/decided, permission granted/revoked, node linked. Paged list, mark-read, mark-all-read, unread count |
| B7.9 | Notification preferences per category (in-app on/off) | The schema also carries the email flag that Phase 8 activates |
| B7.10 | Locale-aware server responses | `Accept-Language` respected for validation messages and notification payload text |

## 3. Frontend Tasks

| # | Task | Detail |
|---|------|--------|
| F7.1 | `ImageUpload` component | Drag-and-drop, client-side crop to square, preview, progress, clear error messages for size/type |
| F7.2 | Avatars everywhere: tree nodes, detail drawer, member list, top bar | Initials fallback with a deterministic colour derived from the name |
| F7.3 | Clan cover image on the clan header and clan list cards | |
| F7.4 | `RichTextEditor` (TipTap or a small `contenteditable` wrapper) styled with styled-components | Toolbar limited to exactly what the sanitizer allows, so nothing a user writes is silently stripped |
| F7.5 | Biography rendering in the detail drawer with sanitized HTML | |
| F7.6 | Notification centre: dropdown with unread badge, full page, mark-read, deep links to the relevant request or node | |
| F7.7 | Notification preferences on `/me` | |
| F7.8 | **Translation completeness pass** | Every string in `vi` and `en`; the ESLint literal-string rule promoted from warn to error |
| F7.9 | Locale-aware formatting | dayjs locale, Vietnamese name ordering (family name first), đời labels, number formatting |
| F7.10 | Vietnamese typography check | Diacritics render correctly at every weight and size; the chosen font (Be Vietnam Pro) is subset and self-hosted |

## 4. Migrations

| Version | Contents |
|---------|----------|
| `V13b__add_notification_preferences.sql` | Per-user, per-category notification flags |
| `V13c__add_media_variants.sql` | `media_objects.variants JSONB` if not already present from `V3` |

## 5. Tests

**Unit** — sanitizer allow-list: `<script>`, `onerror`, `javascript:` hrefs, remote `<img>`, and `<iframe>` are all stripped, while permitted formatting survives round-trip; image validation rejects an oversized file, a wrong type, and a file whose real content type disagrees with its extension.

**Integration** — upload → variants generated → EXIF absent from the stored file (asserted by reading metadata back); an unreferenced media object is cleaned up after the retention window; a non-member requesting a media URL is refused; notifications are created for each event type and only for the correct recipients.

**Frontend (manual)** — the editor's toolbar can only produce markup the sanitizer keeps (checked by hand against the allow-list); upload errors are surfaced legibly; the notification badge updates on poll.

**i18n** — a lightweight locale-completeness script (run via `npm run`, not a test framework) asserts no missing keys in either locale and no untranslated fallbacks in `vi`; a long Vietnamese string is eyeballed for layout breakage.

**Manual walkthrough** — upload an ancestor's photo, write a biography with formatting, see both on the tree and in the drawer; switch to English and back with no missing strings.

## 6. Demo Script

1. Upload a scanned portrait for the founding ancestor; it appears on the node, in the drawer, and in search results.
2. Write his biography with headings and a list; it renders formatted, and a pasted `<script>` tag is silently discarded.
3. A member's claim is approved; the notification badge increments and the notification deep-links to their node.
4. Switch the interface to English and back to Vietnamese — every screen is fully translated.
5. Turn off "permission changed" notifications in preferences and confirm they stop arriving.

## 7. Exit Criteria

- [ ] FR-M1…M3 and FR-X2 complete end to end.
- [ ] Sanitizer proven against a standard XSS payload list.
- [ ] EXIF stripping verified on a real photo carrying GPS data (NFR-9 — family photos routinely leak home locations).
- [ ] Both locales complete; the literal-string lint rule is an error.
- [ ] All six PRD acceptance scenarios pass (M5 — feature complete).

## 8. Notes & Risks

- **Rich text is the classic XSS vector.** Sanitize on write *and* keep the editor's capabilities aligned with the allow-list, so users never lose formatting they were allowed to enter.
- **EXIF GPS is a real privacy risk** for a product full of family photos taken at home. Strip unconditionally; never make it opt-in.
- **Storage costs and orphans.** Every replaced avatar leaves a file behind — the cleanup job is not optional.
- **Vietnamese typography deserves attention.** Stacked diacritics clip with careless line-height and font choices; check at small sizes on the tree nodes, where text is densest.
