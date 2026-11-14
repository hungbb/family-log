# 07 — Frontend Architecture (`web`)

> React 19 · TypeScript (strict) · Ant Design 5 · styled-components 6 · native `fetch`.
>
> **Hard constraints from the draft — non-negotiable:**
> - ❌ **NEVER Tailwind CSS.** No utility-class frameworks, no `@apply`, no CSS utility libraries.
> - ❌ **NO Axios.** All HTTP goes through the native `fetch` wrapper in §4.
> - ✅ **All custom styling lives inside `styled-components`.** No `.css` files beyond one global reset/token sheet, no inline `style` objects except for dynamic transform values on the tree canvas (documented exception, §7.3).

---

## 1. Stack

| Concern | Choice | Notes |
|---------|--------|-------|
| Build tool | **Vite 5** | Fast dev server, native ESM, first-class TS. |
| Language | **TypeScript**, `strict: true` | `noUncheckedIndexedAccess`, `exactOptionalPropertyTypes` on. |
| UI kit | **Ant Design 5** | Components, layout, forms, tables, modals. Themed via `ConfigProvider` tokens. |
| Styling | **styled-components 6** | All custom CSS. Theme object bridges Ant Design tokens (§3). |
| Routing | **React Router 7** (data router) | Route-level code splitting and loaders. |
| Server state | **TanStack Query 5** | Caching, invalidation, optimistic updates. Uses the `fetch` client; brings no HTTP library of its own. |
| Client state | **Zustand** | Small stores: auth session, active clan, tree viewport. |
| Forms | **Ant Design Form** + **Zod** | AntD handles UX, Zod validates and types payloads. |
| Tree layout | **d3-hierarchy** (layout maths only) | Rendering is our own SVG components; no chart library, no styling from d3. |
| i18n | **react-i18next** | `vi` default, `en` second (NFR-8). |
| Dates | **dayjs** | Already an AntD peer dependency. |
| Quality | **ESLint** (flat config) + **Prettier** + **typescript-eslint** | Custom rules enforce the constraints (§9). |

> **Unit tests only on the frontend in v1.** Pure modules — formatting, validation, the tree model — are covered by **Vitest** unit tests in the `node` environment, run by `npm run verify` ([ADR 0005](adr/0005-frontend-unit-tests-with-vitest.md)). There is still no Testing Library, MSW, or Playwright: component and end-to-end behaviour rest on TypeScript `strict`, the ESLint constraint rules, and manual verification against each phase's demo script. Backend keeps its full test suite (doc 09 §4).

## 2. Project Structure

Feature-first, mirroring the backend's bounded contexts so a change usually touches one folder on each side.

```
web/
  index.html
  vite.config.ts
  eslint.config.js
  src/
    main.tsx
    App.tsx                       # providers: QueryClient, ConfigProvider, ThemeProvider, Router, i18n
    router/
      routes.tsx                  # route tree, lazy imports
      guards.tsx                  # RequireAuth, RequireClan, RequirePermission
    api/
      client.ts                   # fetch wrapper (§4)
      errors.ts                   # ApiError, ProblemDetails typing
      endpoints/                  # one file per backend context
        auth.ts  clans.ts  branches.ts  members.ts
        permissions.ts  persons.ts  relationships.ts
        tree.ts  claims.ts  me.ts
      types.ts                    # generated from OpenAPI (§4.4)
    features/
      auth/         { pages/, components/, hooks/, model/ }
      clan/         # clan list, create, settings, switcher
      branch/       # branch tree management
      members/      # member list, join requests inbox
      permissions/  # grant management UI
      tree/         # the visualizer — the centrepiece
        components/ { TreeCanvas, PersonNode, UnionLink, ParentEdge, MiniMap, Toolbar }
        layout/     { useTreeLayout.ts, buildGraph.ts }
        hooks/      { useTreeData.ts, useViewport.ts, useSelection.ts }
      person/       # detail drawer, create/edit forms
      claims/       # claim submission + approval inbox
      notifications/
    components/                   # shared, presentational, app-agnostic
      layout/ { AppShell, Sidebar, TopBar, ClanSwitcher }
      feedback/ { EmptyState, ErrorState, LoadingBlock, PermissionGate }
      form/    { PartialDateInput, PersonPicker, RichTextEditor }
    theme/
      tokens.ts                   # single source of design tokens
      antdTheme.ts                # tokens → AntD ConfigProvider theme
      styled.d.ts                 # DefaultTheme augmentation
      GlobalStyle.ts              # createGlobalStyle: reset + font + CSS vars
    hooks/                        # useAuth, useSessionBootstrap, useActiveClan, usePermissions
    stores/                       # Zustand stores (§6). Shared, because the app shell reads the
                                  # session and components/ may not import from features/
    lib/                          # pure utils: date formatting, name formatting, guards
    i18n/
      index.ts
      locales/{vi,en}/*.json
```

**Rules.** `features/*` may import from `components/`, `hooks/`, `lib/`, `stores/`, `api/`, `theme/` — never from another feature's internals (a feature exports through its own `index.ts`). The shared layers never import from `features/`; ESLint enforces it for `components/`, `hooks/`, `lib/`, `stores/` and `theme/`.

## 3. Styling System

### 3.1 Tokens

`theme/tokens.ts` is the one place colours, spacing, radii, typography, and shadows are defined. Both Ant Design and styled-components consume it, so the two never drift.

```ts
export const tokens = {
  color: {
    primary: '#111827',        // near-black ink — quiet, modern primary, not the AntD default blue
    primaryHover: '#000000',
    accent: '#C0392B',         // one restrained accent, used sparingly for emphasis
    ink: '#111827',
    inkMuted: '#6B7280',
    surface: '#FFFFFF',
    surfaceAlt: '#F7F8FA',
    border: '#ECEEF1',
    success: '#1F8A54', warning: '#B7791F', danger: '#D14343',
    male: '#3A6FB0', female: '#B0567F', deceased: '#9CA3AF',
  },
  space: (n: number) => `${n * 4}px`,
  radius: { sm: '6px', md: '10px', lg: '16px' },
  font: { body: "'Be Vietnam Pro', system-ui, sans-serif" },
  shadow: { card: '0 1px 2px rgba(17,24,39,.05)', float: '0 8px 24px rgba(17,24,39,.10)' },
  z: { canvas: 1, drawer: 100, modal: 1000 },
} as const;
```

`theme/antdTheme.ts` maps these onto AntD's `ConfigProvider` token API; `theme/styled.d.ts` augments `DefaultTheme` so `styled` components get typed `props.theme`.

### 3.2 Rules

1. Every custom style is a `styled` component or a `css` helper — no `.css` imports beyond `GlobalStyle` and AntD's own reset.
2. No hard-coded hex values or magic pixel numbers inside components; read from `props.theme`.
3. Style AntD components by wrapping them: `const Card = styled(AntCard)\`…\`` rather than by overriding global class names.
4. Component files export their styled parts from a colocated `styles.ts` when a file exceeds ~150 lines.
5. Dark mode is out of scope for v1, but tokens are structured so a second token set can be swapped through `ThemeProvider` later.

### 3.3 Visual direction

The UI is **modern and minimalist**: a clean white canvas, generous whitespace, hairline borders, and minimal elevation. A single clean sans-serif (Be Vietnam Pro, strong Vietnamese diacritic support) carries the whole interface — hierarchy comes from weight and size, not ornament. Colour is used sparingly: near-black ink for primary actions and a single restrained accent for emphasis and highlights. Tree nodes are flat cards with a hairline border, a background tinted by gender (blue for male, pink for female), and a gray card for deceased persons. The result should feel calm, contemporary, and uncluttered rather than decorative.

## 4. HTTP Layer (native `fetch`)

### 4.1 The client

`api/client.ts` exposes a small typed wrapper — **no Axios, no ky, no third-party HTTP client**:

```ts
type RequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  query?: Record<string, string | number | boolean | undefined>;
  signal?: AbortSignal;
  headers?: Record<string, string>;
  skipAuth?: boolean;
};

export async function request<T>(path: string, options?: RequestOptions): Promise<T>;
export const api = {
  get:   <T>(path: string, o?: Omit<RequestOptions,'method'|'body'>) => request<T>(path, { ...o, method: 'GET' }),
  post:  <T>(path: string, body?: unknown, o?: RequestOptions) => request<T>(path, { ...o, method: 'POST', body }),
  patch: <T>(...) , put: <T>(...) , del:  <T>(...),
  upload: <T>(path: string, file: File, field?: string) => /* FormData, no JSON content-type */,
};
```

Responsibilities:
- Prefix `import.meta.env.VITE_API_BASE_URL` + `/api/v1`; serialize `query` safely.
- Attach `Authorization: Bearer` unless `skipAuth`.
- `Content-Type: application/json` for JSON bodies; omitted for `FormData`.
- Parse RFC 7807 Problem Details into a typed `ApiError { status, code, detail, errors[], correlationId }`.
- Return `undefined` for `204`.
- Propagate `AbortSignal` (TanStack Query passes one for every query).
- Attach `X-Correlation-Id` when retrying so client and server logs line up.

### 4.2 Token refresh

A single-flight refresh interceptor inside the wrapper: on `401 TOKEN_EXPIRED`, the first failing request triggers `POST /auth/refresh`; concurrent requests await the same promise, then all retry once. A failed refresh clears the session store and redirects to login. Recursion is prevented by marking the refresh call itself `skipAuth`.

### 4.3 Endpoint modules

`api/endpoints/*.ts` hold one thin function per backend endpoint with fully typed inputs and outputs. Components never call `request()` directly — they call endpoint functions through TanStack Query hooks.

### 4.4 Types from OpenAPI

`openapi-typescript` generates `api/types.ts` from the backend spec via `npm run gen:api`. Generated types are committed; regenerating must produce no diff (enforced in CI once the CI/CD workstream lands) — the API contract can never silently drift from the client.

## 5. Server State (TanStack Query)

**Query key convention:** `['clan', clanId, 'tree', { rootPersonId, depth }]`, `['clan', clanId, 'persons', personId]`, `['clan', clanId, 'joinRequests', filters]`.

| Data | Strategy |
|------|----------|
| Tree | `staleTime` 30 s, `ETag`-backed; invalidated by every lineage mutation. |
| Person detail | `staleTime` 60 s; optimistic update on edit, rollback on error. |
| Permissions (`/clans/{id}/my-permissions`) | `staleTime` 5 min; refetched on clan switch and after any grant change. |
| Inboxes (join requests, claims) | `refetchOnWindowFocus`, 30 s stale time. |
| Notifications | Polled every 60 s in v1 (WebSockets are post-v1). |

Mutations declare the query keys they invalidate. Version conflicts (`409 VERSION_CONFLICT`) surface a "this node changed elsewhere — reload" dialog rather than silently overwriting.

## 6. Client State (Zustand)

| Store | Holds |
|-------|-------|
| `useSessionStore` | Access token (memory), current user, hydration status. |
| `useActiveClanStore` | Active `clanId`, persisted to `localStorage`, validated against membership on boot. |
| `useTreeViewStore` | Zoom, pan offset, collapsed node ids, selected person, layout orientation. |
| `useUiStore` | Drawer/modal open state that must survive route changes. |

Anything the server owns lives in TanStack Query, never in Zustand.

## 7. The Tree Visualizer

The most demanding component; it gets its own design.

### 7.1 Data flow

```
GET /tree (flat: persons, parentages, unions, branches)
  → buildGraph()      normalize into Maps, resolve unions, order siblings
  → useTreeLayout()   d3-hierarchy computes x/y; unions collapse partners into a couple-node
  → <TreeCanvas>      SVG <g> transformed by zoom/pan; renders only nodes in viewport
```

### 7.2 Rendering choices

- **SVG**, not canvas: nodes stay real DOM elements, so text selection, keyboard focus, and accessibility work. Canvas is the fallback only if profiling on the 5,000-node fixture demands it.
- **Virtualization**: nodes outside the viewport bounding box (plus a margin) are not rendered.
- **Couple nodes**: partners in a union render as an adjacent pair sharing a connector; children hang from the union's midpoint — the conventional *gia phả* reading.
- **Generation rails**: horizontal guide lines labelled *Đời 1, Đời 2…*.
- **Interactions**: wheel/pinch zoom, drag pan, click to select (opens the detail drawer), double-click to focus, collapse/expand, "find me" jumps to the caller's linked node.
- **Editing affordances**: nodes with `canEdit === false` render muted with no hover actions; scoped users therefore *see* the whole tree but can only act inside their branch (doc 04 §5.5).

### 7.3 Styling exception

Zoom/pan uses a dynamic `transform` on one `<g>` element. It is set as the SVG `transform` **attribute**, not the `style` prop, so no inline-style exception or ESLint disable is needed. The same applies to per-zoom connector widths (a `stroke-width` attribute on the connector layer) and to the per-frame offsets of the layout glide. All other tree styling (node cards, edges, labels, rails) is styled-components.

### 7.4 Accessibility (NFR-7)

The canvas is `role="tree"` with keyboard navigation (arrows move between siblings/generations, Enter opens detail). A "List view" toggle renders the same data as an AntD `Table` grouped by generation — the accessible, printable, screen-reader-friendly alternative.

### 7.5 Palette, motion, print and the visual vocabulary (#61, ADR 0006)

- **Palette.** The canvas carries its own light and dark sets (`tokens.tree`), published as `--tree-*` custom properties on the canvas frame; every canvas part reads those. The app chrome stays light. Print and exports always use the light set.
- **Connectors** are `non-scaling-stroke`, with a width that follows the zoom but never drops below ~1.25px (`model/visual.ts` `screenStroke`), so the structure stays visible at the 0.08 zoom floor.
- **Đời labels** are drawn in screen space, pinned to the frame's leading edge, at a fixed size.
- **Motion** (`model/motion.ts`): collapse/expand glides (~260 ms), jump/fit tween the camera (~300 ms); pan and wheel are never eased; all of it is off under `prefers-reduced-motion`.
- **Print/export**: `PrintDialog` → `planPrint` re-lays the tree out in both orientations and picks the arrangement with the fewest pages at a legible scale (or fits one page); pages are vector SVG sized in mm. "Save as PDF" in the print dialog is the PDF export; SVG and PNG download directly.
- **Visual vocabulary** — one definition (`model/visual.ts`), explained by the on-canvas legend and used by the canvas, minimap, list view and print. Colour is never the only carrier:

| Category | Colour | Second channel |
|---|---|---|
| Male / female / other / unknown | rail + avatar outline hue | avatar shape: square / circle / diamond / dashed circle (pedigree convention) |
| Deceased | muted name and avatar | mourning band across the card's top-left corner |
| You | accent ring | "Bạn / You" tag above the card; accent dot on the minimap |
| Linked to an account | green ring | ring present at all |
| Adopted / step / foster | — | dashed connector |
| Secondary marriage / remarriage | lighter | thinner, dashed bar |

## 8. Routing & Guards

```
/login  /register  /forgot-password  /reset-password
/                      → clan list / onboarding (create or join)
/clans/new
/join                  → enter clanCode
/clans/:clanId         → AppShell
   /tree               (default) — visualizer
   /tree?person={id}   — detail drawer over the tree, centred on that person
   /search             — person search; a result deep-links into the tree
   /members            — member list + join-request inbox
   /branches           — branch structure management
   /permissions        — grant management
   /claims             — claim inbox / my claims
   /settings           — clan settings (CLAN_ADMIN)
/me                    → profile, notifications, sessions
```

The open person is a **query parameter on the tree route**, not the nested `/persons/:personId` this
section first sketched (#48). The drawer is not a page — it is the tree with one card open over it —
and a nested route would re-mount the canvas underneath it on every open; `?person=` keeps the tree
one route, composes with any later view parameters, and is built in one place (`router/paths.ts`).
It is what FR-T5 means by results that "deep-link into the tree": the URL survives a reload, can be
sent to a relative, and because opening a card pushes a history entry, Back closes the drawer
instead of leaving the tree.

`RequireAuth` redirects unauthenticated users to `/login` with a return path. `RequireClan` validates membership in the route's `clanId` (a `404` from the API renders "clan not found or you are not a member"). `<PermissionGate permission="ROLE_MANAGE">` hides UI a user cannot use — **UI gating is a convenience, never the security boundary**; the server decides (doc 04 §5.1).

## 9. Code Quality Enforcement

`eslint.config.js` (flat config) extends `typescript-eslint` strict, `react`, `react-hooks`, `jsx-a11y`, and `prettier`, plus project rules that encode the draft's constraints:

| Rule | Purpose |
|------|---------|
| `no-restricted-imports: ['axios', 'ky', 'superagent', 'tailwindcss', ...]` | Blocks Axios and Tailwind at the import level. |
| `no-restricted-syntax` on `ImportDeclaration[source.value=/\.css$/]` | Only `GlobalStyle` and the AntD reset may bring in CSS (allow-listed paths). |
| `react/forbid-dom-props: ['style']` | Enforces the styled-components rule; the single `TreeCanvas` exception is an explicit inline disable. |
| `no-restricted-syntax` on a `theme` JSX prop (except `ConfigProvider` / `ThemeProvider`) | styled-components owns the prop name `theme`: one on a styled component replaces the token context, so an AntD `theme="light"` makes every `props.theme.*` undefined and crashes it at render. |
| `no-restricted-properties` on `window.fetch` outside `api/client.ts` | All HTTP goes through the wrapper. |
| `i18next/no-literal-string` (warn) | Catches hard-coded user-facing strings (NFR-8). |
| `import/no-cycle`, feature-boundary `no-restricted-imports` zones | Keeps the feature slices independent. |

Prettier config: 100 print width, single quotes, trailing commas, 2-space indent. A pre-commit hook (husky + lint-staged) runs ESLint, Prettier, and `tsc --noEmit` on staged files, with warnings treated as errors (`--max-warnings=0`). Running these in CI is deferred with the rest of the CI/CD workstream (see the roadmap).

## 10. Performance Budget

| Metric | Budget |
|--------|--------|
| Initial JS (gzipped) | ≤ 250 KB — AntD imported per-component, route-level code splitting |
| Time to interactive (mid-range laptop, cable) | ≤ 2.5 s |
| Tree render, 2,000 nodes | ≤ 1 s from data to painted frame |
| Pan/zoom | 60 fps at 2,000 nodes (virtualized) |
| Lighthouse (Performance / A11y) | ≥ 90 / ≥ 95 |

Budgets are checked locally with `vite-bundle-visualizer` output and a Lighthouse run on the main routes; automating this in CI is deferred with the CI/CD workstream.
