# CI for Moment Forever Payment Service

## WHAT
This repository now has a GitHub Actions workflow at `.github/workflows/ci.yml` that runs on:

- `push` to `main`
- `pull_request` targeting `main`

It implements **CI** (build + test + package + Docker image build + optional image publishing) and keeps a strict **CI/CD boundary** by not running deployment commands.

## WHY
CI helps the team catch breakages early and produce a repeatable build artifact.

- **Testing pipeline**: confirms application code still works after each change.
- **Build pipeline**: creates a package (`jar`) and checks build reproducibility.
- **Docker publishing**: creates a versioned image tag tied to commit SHA for traceability.
- **Safety**: deployment remains a separate CD concern and is intentionally excluded.

## HOW
Workflow stages, in order:

1. Checkout source code.
2. Setup Java 17 + Maven cache.
3. Auto-select Maven command (`./mvnw` if present, otherwise `mvn`).
4. Run tests: `mvn test` (or `./mvnw test`).
5. Build package: `mvn -DskipTests package` (or wrapper equivalent).
6. Optional security scan placeholder controlled by repository variable:
   - `ENABLE_SECURITY_SCAN=true` enables the placeholder step.
7. Build Docker image tagged with immutable commit SHA:
   - `${DOCKERHUB_USERNAME}/${IMAGE_REPOSITORY}:${GITHUB_SHA}`
8. On `push` events, login to DockerHub using:
   - `DOCKERHUB_USERNAME`
   - `DOCKERHUB_TOKEN`
9. Push SHA-tagged image.
10. On `main` push, also tag and push `latest` (optional convenience tag).

No deployment command is included.

## HOW TO VERIFY
### 1) Repository configuration
Add these GitHub repository secrets:

- `DOCKERHUB_USERNAME`
- `DOCKERHUB_TOKEN`

Set workflow env value in `.github/workflows/ci.yml`:

- `IMAGE_REPOSITORY=CHANGEME_PAYMENT_IMAGE_REPOSITORY`

Optional repository variable:

- `ENABLE_SECURITY_SCAN=true` (to enable the placeholder step)

### 2) Trigger CI
- Open a PR to `main` and check Actions tab.
- Push directly to `main` and check Actions tab.

Expected behavior:

- PR run: test/build/docker build should run; Docker push should be skipped.
- Main push run: test/build/docker build should run; SHA tag must be pushed; `latest` should also be pushed.

### 3) Validate Docker tags
In DockerHub repository `CHANGEME_DOCKERHUB_USERNAME/CHANGEME_PAYMENT_IMAGE_REPOSITORY`, verify:

- A tag equal to full commit SHA exists for each successful `main` push run.
- `latest` is updated only from `main` pushes.

---

### Placeholder values to replace
- `CHANGEME_PAYMENT_IMAGE_REPOSITORY`: DockerHub repo name (example: `moment-forever-payment`).
- `CHANGEME_DOCKERHUB_USERNAME`: your DockerHub username/org where images are pushed.
