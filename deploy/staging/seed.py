#!/usr/bin/env python3
"""Seed staging with a demo clan, through the public API.

Goes through the API rather than SQL, so the ancestry closure, generations, permissions and audit
trail are produced by the same code paths a real user exercises.

    python3 deploy/staging/seed.py                       # http://192.168.1.100:8081
    python3 deploy/staging/seed.py --base-url http://localhost:8081

What it creates:
  - four accounts (password for all: see PASSWORD below)
      admin@giaky.test   clan founder, CLAN_ADMIN
      editor@giaky.test  member of Chi Giáp, with TREE_EDIT on that branch only
      viewer@giaky.test  member, read-only
      pending@giaky.test has an open join request, for the approval screen
  - the clan "Họ Nguyễn làng Đông Ngạc", with branches Chi Giáp and Chi Ất under the root
  - about 80 people over six generations: a founding ancestor, a second (SECONDARY) marriage, an
    adopted child, daughters, partial and lunar dates, living and deceased people

It refuses to run twice against the same database: if the admin already has a clan of that name
it stops, so it never makes a second copy of the tree.
"""

from __future__ import annotations

import argparse
import json
import random
import sys
import time
import urllib.error
import urllib.request

PASSWORD = "GiaKy-Staging-2026"
CLAN_NAME = "Họ Nguyễn làng Đông Ngạc"
MAX_GENERATION = 6

USERS = {
    "admin": ("admin@giaky.test", "Nguyễn Quản Trị"),
    "editor": ("editor@giaky.test", "Nguyễn Biên Tập"),
    "viewer": ("viewer@giaky.test", "Nguyễn Người Xem"),
    "pending": ("pending@giaky.test", "Nguyễn Chờ Duyệt"),
}

# Generation names (chữ lót): the middle name shared by the men of one đời.
MIDDLE = {1: "Văn", 2: "Đức", 3: "Hữu", 4: "Văn", 5: "Minh", 6: "Gia"}
MALE_GIVEN = ["An", "Bình", "Cường", "Dũng", "Hải", "Hùng", "Khoa", "Long", "Nam", "Phúc",
              "Quang", "Sơn", "Tài", "Thắng", "Thịnh", "Toàn", "Trung", "Tuấn", "Việt", "Vinh"]
FEMALE_GIVEN = ["Anh", "Châu", "Dung", "Hà", "Hạnh", "Hoa", "Hương", "Lan", "Linh", "Mai",
                "Ngọc", "Nhung", "Oanh", "Phương", "Quyên", "Thảo", "Thu", "Trang", "Vân", "Yến"]
WIFE_SURNAMES = ["Trần", "Lê", "Phạm", "Hoàng", "Vũ", "Đỗ", "Bùi", "Đặng", "Ngô", "Dương"]
LUNAR_MONTHS = ["Giêng", "Hai", "Ba", "Tư", "Năm", "Sáu", "Bảy", "Tám", "Chín", "Mười", "Một", "Chạp"]


class ApiError(Exception):
    pass


class Api:
    def __init__(self, base_url: str):
        self.base = base_url.rstrip("/") + "/api/v1"
        self.calls = 0

    def request(self, method: str, path: str, body=None, token: str | None = None, ok=(200, 201, 204)):
        data = None if body is None else json.dumps(body).encode()
        headers = {"Accept": "application/json"}
        if data is not None:
            headers["Content-Type"] = "application/json"
        if token:
            headers["Authorization"] = f"Bearer {token}"
        for attempt in range(6):
            req = urllib.request.Request(self.base + path, data=data, method=method, headers=headers)
            self.calls += 1
            try:
                with urllib.request.urlopen(req, timeout=30) as resp:
                    raw = resp.read()
                    return json.loads(raw) if raw else None
            except urllib.error.HTTPError as err:
                raw = err.read().decode(errors="replace")
                if err.code == 429 and attempt < 5:  # rate limited: back off and retry
                    time.sleep(int(err.headers.get("Retry-After") or 2 ** attempt))
                    continue
                if err.code in ok:
                    return json.loads(raw) if raw else None
                raise ApiError(f"{method} {path} -> {err.code}: {raw}") from None
        raise ApiError(f"{method} {path}: still rate limited")


def sign_in(api: Api, key: str) -> tuple[str, str]:
    """Registers the account if it does not exist yet, then logs in. Returns (token, user id)."""
    email, name = USERS[key]
    try:
        api.request("POST", "/auth/register",
                    {"email": email, "password": PASSWORD, "displayName": name, "locale": "vi-VN"})
    except ApiError as err:
        if "409" not in str(err):  # already registered is fine; anything else is not
            raise
    token = api.request("POST", "/auth/login", {"email": email, "password": PASSWORD})["accessToken"]
    me = api.request("GET", "/me", token=token)
    return token, me["id"]


def year_date(year: int, circa: bool = False) -> dict:
    return {"year": year, "precision": "YEAR", "circa": circa}


def exact_date(rng: random.Random, year: int) -> dict:
    return {"year": year, "month": rng.randint(1, 12), "day": rng.randint(1, 28), "precision": "EXACT"}


class TreeBuilder:
    def __init__(self, api: Api, token: str, clan_id: str, rng: random.Random):
        self.api, self.token, self.clan, self.rng = api, token, clan_id, rng
        self.count = 0
        self.used_names: set[str] = set()

    # --- API wrappers --------------------------------------------------------------------------
    def create(self, body: dict) -> dict:
        person = self.api.request("POST", f"/clans/{self.clan}/persons", body, self.token)
        self.count += 1
        return person

    def attach_to_union(self, union_id: str, person_id: str):
        self.api.request("POST", f"/clans/{self.clan}/unions/{union_id}/children",
                         {"personId": person_id}, self.token)

    # --- People --------------------------------------------------------------------------------
    def name(self, gender: str, generation: int, surname: str = "Nguyễn") -> tuple[str, str, str]:
        pool = MALE_GIVEN if gender == "MALE" else FEMALE_GIVEN
        middle = MIDDLE.get(generation, "Văn") if gender == "MALE" and surname == "Nguyễn" else "Thị"
        for _ in range(50):
            given = self.rng.choice(pool)
            full = f"{surname} {middle} {given}"
            if full not in self.used_names:
                break
        self.used_names.add(full)
        return full, surname, given

    def life(self, birth_year: int) -> dict:
        """Lifespan fields consistent with the birth year."""
        rng = self.rng
        if birth_year < 1935 or (birth_year < 1955 and rng.random() < 0.4):
            death = min(birth_year + rng.randint(45, 88), 2025)
            fields = {"lifeStatus": "DECEASED"}
            if birth_year < 1900:
                fields["birth"] = year_date(birth_year, circa=rng.random() < 0.5)
                fields["death"] = {**year_date(death),
                                   "lunarText": f"ngày {rng.randint(1, 29)} tháng {rng.choice(LUNAR_MONTHS)}"}
            else:
                fields["birth"] = exact_date(rng, birth_year) if rng.random() < 0.6 else year_date(birth_year)
                fields["death"] = exact_date(rng, death)
            if rng.random() < 0.5:
                fields["burialPlace"] = rng.choice(["Nghĩa trang làng Đông Ngạc", "Lăng họ Nguyễn, Đông Ngạc",
                                                    "Nghĩa trang Văn Điển"])
            return fields
        return {"lifeStatus": "ALIVE", "birth": exact_date(rng, birth_year)}

    def add_spouse(self, husband: dict, generation: int, birth_year: int, union_type: str = "PRIMARY") -> str:
        """Creates a wife for `husband` and returns the union id."""
        surname = self.rng.choice(WIFE_SURNAMES)
        full, family, given = self.name("FEMALE", generation, surname)
        body = {"fullName": full, "familyName": family, "givenName": given, "gender": "FEMALE",
                **self.life(birth_year + self.rng.randint(-1, 4))}
        if union_type == "PRIMARY":
            wife = self.create({**body, "placement": {"type": "SPOUSE_OF", "personId": husband["id"]}})
            links = [u for u in wife["unions"] if u["partner"]["id"] == husband["id"]]
            return links[0]["union"]["id"]
        # A second wife: created on her own, then the SECONDARY union is recorded (PersonPlacement doc).
        wife = self.create({**body, "placement": {"type": "UNPLACED"}})
        union = self.api.request("POST", f"/clans/{self.clan}/unions", {
            "partnerAId": husband["id"], "partnerBId": wife["id"],
            "unionType": "SECONDARY", "status": "ACTIVE"}, self.token)
        return union["id"]

    def child_body(self, gender: str, generation: int, birth_year: int, order: int) -> dict:
        full, family, given = self.name(gender, generation)
        return {"fullName": full, "familyName": family, "givenName": given, "gender": gender,
                "birthOrder": order, **self.life(birth_year)}

    def descend(self, father: dict, generation: int, birth_year: int, union_id: str, budget: int):
        """Children of one marriage, then their families, depth first."""
        if generation > MAX_GENERATION:
            return
        rng = self.rng
        n_children = rng.randint(1, 3) if generation >= 5 else rng.randint(2, 4)
        n_children = min(n_children, max(1, budget))
        year = birth_year + rng.randint(22, 30)
        for order in range(1, n_children + 1):
            gender = "MALE" if rng.random() < 0.6 else "FEMALE"
            child = self.create({**self.child_body(gender, generation, year, order),
                                 "placement": {"type": "CHILD_OF_UNION", "unionId": union_id}})
            # Lineage follows the sons, as a gia phả does; daughters are recorded but not extended.
            if gender == "MALE" and generation < MAX_GENERATION and year < 2000:
                wife_union = self.add_spouse(child, generation, year)
                self.descend(child, generation + 1, year, wife_union, budget // n_children)
            year += rng.randint(2, 4)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--base-url", default="http://192.168.1.100:8081")
    parser.add_argument("--seed", type=int, default=20261112, help="random seed, for a reproducible tree")
    args = parser.parse_args()

    api = Api(args.base_url)
    rng = random.Random(args.seed)
    started = time.time()

    print("Accounts")
    admin, _ = sign_in(api, "admin")
    editor, editor_id = sign_in(api, "editor")
    viewer, _ = sign_in(api, "viewer")
    pending, _ = sign_in(api, "pending")

    existing = [c for c in api.request("GET", "/clans", token=admin) if c["name"] == CLAN_NAME]
    if existing:
        print(f"  '{CLAN_NAME}' already exists (code {existing[0]['clanCode']}); nothing to do.")
        return 0

    print("Clan and branches")
    clan = api.request("POST", "/clans", {
        "name": CLAN_NAME, "surname": "Nguyễn", "originPlace": "Đông Ngạc, Từ Liêm, Hà Nội",
        "description": "Dữ liệu thử nghiệm cho môi trường staging. Test data for staging."}, admin)
    clan_id, root_branch = clan["id"], clan["rootBranchId"]
    chi_giap = api.request("POST", f"/clans/{clan_id}/branches",
                           {"name": "Chi Giáp", "description": "Hậu duệ của con trưởng."}, admin)
    chi_at = api.request("POST", f"/clans/{clan_id}/branches",
                         {"name": "Chi Ất", "description": "Hậu duệ của con thứ."}, admin)

    print("People")
    tree = TreeBuilder(api, admin, clan_id, rng)

    founder = tree.create({
        "fullName": "Nguyễn Văn Thành", "familyName": "Nguyễn", "givenName": "Thành",
        "posthumousName": "Phúc Hậu", "gender": "MALE", "lifeStatus": "DECEASED",
        "birth": year_date(1820, circa=True),
        "death": {**year_date(1885), "lunarText": "ngày 12 tháng Chạp năm Ất Dậu"},
        "burialPlace": "Lăng họ Nguyễn, Đông Ngạc",
        "notesHtml": "<p>Thủy tổ, người lập ấp tại Đông Ngạc. <em>Founding ancestor (test data).</em></p>",
        "foundingAncestor": True,
        "placement": {"type": "BRANCH_ROOT", "branchId": root_branch}})
    founder_union = tree.add_spouse(founder, 1, 1820)

    # Đời 2. The two eldest sons found the branches: created as branch roots, then attached to their
    # parents' marriage so they still descend from the founder.
    sons = []
    for order, (given, branch) in enumerate([("Đức Trưởng", chi_giap), ("Đức Thứ", chi_at)], start=1):
        son = tree.create({"fullName": f"Nguyễn {given}", "familyName": "Nguyễn",
                           "givenName": given.split()[1], "gender": "MALE", "birthOrder": order,
                           **tree.life(1845 + 3 * order),
                           "placement": {"type": "BRANCH_ROOT", "branchId": branch["id"]}})
        tree.attach_to_union(founder_union, son["id"])
        sons.append((son, 1845 + 3 * order))
    third = tree.create({**tree.child_body("MALE", 2, 1854, 3),
                         "placement": {"type": "CHILD_OF_UNION", "unionId": founder_union}})
    tree.create({**tree.child_body("FEMALE", 2, 1857, 4),
                 "placement": {"type": "CHILD_OF_UNION", "unionId": founder_union}})

    for son, year in sons:
        tree.descend(son, 3, year, tree.add_spouse(son, 2, year), budget=30)

    # The third son: two marriages, and an adopted son recorded against him alone.
    first = tree.add_spouse(third, 2, 1854)
    tree.descend(third, 3, 1854, first, budget=6)
    second = tree.add_spouse(third, 2, 1860, union_type="SECONDARY")
    tree.create({**tree.child_body("MALE", 3, 1885, 1),
                 "placement": {"type": "CHILD_OF_UNION", "unionId": second}})
    tree.create({**tree.child_body("MALE", 3, 1888, 2), "nature": "ADOPTED", "parentRole": "FATHER",
                 "notesHtml": "<p>Con nuôi. <em>Adopted.</em></p>",
                 "placement": {"type": "CHILD_OF_PARENT", "personId": third["id"]}})

    print("Members and permissions")
    code = clan["clanCode"]
    req = api.request("POST", "/join-requests", {"clanCode": code, "message": "Biên tập viên chi Giáp."}, editor)
    api.request("POST", f"/clans/{clan_id}/join-requests/{req['id']}/approve",
                {"homeBranchId": chi_giap["id"]}, admin)
    api.request("POST", f"/clans/{clan_id}/permissions", {
        "granteeUserId": editor_id, "permission": "TREE_EDIT",
        "scopeType": "BRANCH", "scopeRefId": chi_giap["id"]}, admin)
    req = api.request("POST", "/join-requests", {"clanCode": code, "message": "Con cháu chi Ất."}, viewer)
    api.request("POST", f"/clans/{clan_id}/join-requests/{req['id']}/approve", {"homeBranchId": chi_at["id"]}, admin)
    api.request("POST", "/join-requests", {"clanCode": code, "message": "Xin vào họ."}, pending)

    stats = api.request("GET", f"/clans/{clan_id}/tree/stats", token=admin)
    print(f"\nDone in {time.time() - started:.0f}s, {api.calls} API calls, {tree.count} people.")
    print(f"Tree stats: {json.dumps(stats, ensure_ascii=False)}")
    print(f"Clan code: {code}")
    print(f"Sign in at {args.base_url} with any of: "
          f"{', '.join(e for e, _ in USERS.values())}  /  password {PASSWORD}")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except ApiError as err:
        print(f"\nFAILED: {err}", file=sys.stderr)
        sys.exit(1)
