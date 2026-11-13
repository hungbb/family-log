# Jenkins jobs

Every Gia Ký job on `http://192.168.1.100:8080` is defined in one file,
`deploy/jenkins/jobs.groovy`, and created or updated by one job, **`giaky-seed`**, which applies
it with the [Job DSL plugin](https://plugins.jenkins.io/job-dsl/).

| Job | Jenkinsfile | Trigger | What |
|-----|-------------|---------|------|
| `giaky-seed` | `deploy/jenkins/Jenkinsfile` | polls `main` every ~15 min | applies `jobs.groovy` (and so manages itself) |
| `giaky-staging` | `Jenkinsfile` | polls `main` every ~5 min | build, test, deploy staging on the Jenkins host — [README](../staging/README.md) |
| `giaky-staging-demo-data` | `deploy/staging/Jenkinsfile.demo-data` | manual | fill staging with the demo clan (`seed.py`) |
| `giaky-prod` | `deploy/prod/Jenkinsfile` | manual | build, push, deploy production on the ezgo host — [README](../prod/README.md) |

To add or change a job: edit `jobs.groovy`, merge to `main`, then wait for `giaky-seed` or press
**Build Now** on it. Do not change these jobs in the UI: the next seed run puts them back. A job
taken out of `jobs.groovy` is **disabled**, not deleted; delete it by hand once you are sure.

## One-time setup

1. **Plugin.** *Manage Jenkins → Plugins → Available*: install **Job DSL**.
2. **Credential.** `github-ssh` (*SSH username with private key*, user `git`, a key with read access
   to `hungbb/family-log`). Staging already uses it.
3. **Script security.** Job DSL scripts run as Groovy, so Jenkins wants them approved. On this
   single-admin Jenkins, where only `main` is read, the simplest is *Manage Jenkins → Security →
   Enable script security for Job DSL scripts* **off**. Leaving it on also works, but every change
   to `jobs.groovy` then waits in *Manage Jenkins → In-process Script Approval* before it applies.
4. **The seed job, by hand, once.** *+ New Item* → name `giaky-seed` → **Pipeline** → OK, then
   under *Pipeline*: **Pipeline script from SCM**, Git, repository
   `git@github.com:hungbb/family-log.git`, credentials `github-ssh`, branch `*/main`, script path
   `deploy/jenkins/Jenkinsfile`, *Lightweight checkout* ticked. Save, then **Build Now**.

   The first run creates the other three jobs and takes over `giaky-seed`'s own configuration
   (adding its poll trigger). An existing `giaky-staging` is updated in place; its build history
   stays.

Then the prerequisites each pipeline needs on its own: the staging credentials in
[staging/README.md](../staging/README.md), and the production credentials and host setup in
[prod/README.md](../prod/README.md).
