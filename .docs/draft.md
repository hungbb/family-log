```markdown
# Draft Specification: Gia Ký (Gia Phả Digital Platform)

## 1. Project Overview
**Gia Ký** is a modern web platform designed for families and clans (*gia tộc*) to build, manage, preserve, and collaborate on their lineage and family tree (*gia phả*). The system enables authorized users to record ancestral lines, map living members to historical tree nodes, manage multi-clan memberships, support hierarchical branches/sub-clans (*chi / nhánh gia tộc*), and enforce fine-grained, permission-based collaboration.

---

## 2. Core Features & Business Intent

### 2.1 Family Tree & Person Nodes
- **Person Node**: The core entity within a family tree representing an individual (living or deceased).
- **Key Attributes**:
  - `id`: Unique identifier (UUID).
  - `fullName`: Full name of the individual.
  - `gender`: Gender classification (Male, Female, Other).
  - `dateOfBirth` / `yearOfBirth`: Flexible birth date tracking (supports partial dates/years).
  - `dateOfDeath` / `yearOfDeath`: Optional death date/year tracking.
  - `status`: Alive, Deceased, Unknown.
  - `avatarUrl`: Profile image URL or uploaded photo.
  - `notes`: Rich text notes, biography, burial location, or historical records.
- **Relationships**:
  - **Parent-Child**: Direct lineage linking children to father and mother nodes.
  - **Spouse / Union**: Relationship links between partners supporting primary, secondary, or sequential marriages.

### 2.2 Clan (Gia Tộc) & Branch (Chi / Nhánh) Management
- **Clan Entity**: Represents a distinct lineage group with a unique, immutable `clanId`.
- **Hierarchical Branches & Sub-Clans (Nhánh Gia Tộc)**:
  - Clans support hierarchical structures: A Root Clan can contain multiple Sub-Clans or Branches (*Chi / Nhánh*).
  - A Branch can be defined with a specific `branchRootPersonId` (e.g., Uncle C's branch starting at Uncle C as the root person of generation 14).
- **Multi-Clan / Multi-Branch Membership**:
  - A single `User` account can belong to multiple clans or branches simultaneously.
- **Joining Mechanism**:
  - Users can join a clan or specific branch using the unique `clanId` or `branchId`.
  - Membership requests (`ClanJoinRequest`) are routed to authorized managers of that specific clan/branch.

### 2.3 Fine-Grained Authorization & Scoped Permission Model
- **Permission-Based Access Control (Authz)**:
  - Clan management and tree editing are **not** restricted to a single Patriarch / Head of Clan (*Trưởng tộc*).
  - Editing, managing, and approval rights can be assigned to multiple users via explicit permissions.
- **Granular & Scoped Permissions**:
  - Permissions can be granted globally for the entire Clan or **scoped to a specific Branch/Sub-Tree (`scopedBranchId` / `scopedNodeId`)**.
  - Permission Types:
    - `CLAN_VIEW`: Access and view the clan's family tree.
    - `TREE_EDIT`: Create, modify, or soft-delete Person nodes and lineage relationships. (Can be scoped to a specific branch/subtree).
    - `MEMBER_APPROVE`: Approve or reject incoming user join requests.
    - `NODE_CLAIM_APPROVE`: Review and approve requests from users claiming tree nodes within their manageable scope.
    - `ROLE_MANAGE`: Grant or revoke permissions for other members in the clan/branch.
    - `CLAN_ADMIN`: Full administrative management over clan settings.
- **Branch Editing Isolation & Integration (Phân Quyền Nhánh)**:
  - **Isolated Management**: Uncle C with `TREE_EDIT` scoped to Branch C can only manage nodes under Uncle C's subtree. Uncle C cannot edit Uncle D's branch or the root ancestors.
  - **Integrated Master View**: When querying or viewing the Root Clan's tree, the system dynamically aggregates all branch subtrees into one complete, seamless family tree visualizer.

### 2.4 Decoupled Person Nodes & Node-User Mapping (Claiming)
- **Decoupled Tree Building**:
  - Constructing a family tree does NOT require individual nodes to be linked to active user accounts.
  - Branch/Clan Editors can build arbitrary historical or current nodes.
- **User Node Claiming Workflow**:
  - Active users joining a clan can select an unlinked `Person` node and request to claim that position as their profile.
  - Requests are queued for approval by authorized branch/clan editors.
- **Direct Assignment**:
  - Authorized branch editors can directly assign/link an existing clan member's user account to any unlinked `Person` node within their branch.

---

## 3. Technology Stack & Architectural Constraints

### 3.1 Backend Architecture (`api`)
- **Framework**: Java 21 (Latest LTS) with **Spring Boot 3.x**.
- **Database**: **PostgreSQL**.
- **Database Migration**: **Flyway**.
- **Architecture**: **Domain-Driven Design (DDD)** layered structure.
- **Protocols**: REST API JSON standards, OpenAPI/Swagger documentation, JWT authentication.

### 3.2 Frontend Architecture (`web`)
- **Framework**: **ReactJS** (Latest version) with **TypeScript**.
- **UI Design System**: **Ant Design (`antd`)** latest version.
- **Styling Solution**: **`styled-components`**.
- **Linting & Code Quality**: **ESLint** & Prettier for strict code formatting.
- **HTTP Client**: **Native `fetch` API** with custom service wrapper.
- **Strict Constraints**:
  - **NEVER use Tailwind CSS.**
  - **DO NOT use Axios.**
  - All custom styles MUST be encapsulated inside `styled-components`.

