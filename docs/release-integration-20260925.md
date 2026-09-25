# FFH release integration, 25 September 2026

Integration candidate: latest `feature/calendar-archive-20260924`, including task-search and scoped-task fixes.
Legacy `main` is preserved without modification at `archive/main-before-ffh-release-20260925`.

## Decision on divergent code
The existing default branch and the new application had diverged substantially. The modern branch supersedes the historical index, manifest, cache logic and inspection workflows. Historical main-specific sources remain accessible at the archive branch (in particular the old `InspectionJob*` endpoints and previous icon assets). They are deliberately **not** silently reactivated in the current service: current device inspection, signing and task delegation use the unified device workflow. Reintroducing competing controllers could cause inconsistent inspection status or bypass the existing unified process.

Git ancestry is joined on the integration candidate; the legacy branch and old commits remain available for reference and selective migration. Do not delete that archive before functional checks are complete.

## Permissions and functional scopes
- Administration and Vorstand: all general task categories.
- Feuerwehrwart: FIRE and TRAINING.
- Gerätewart: DEVICE.
- Getränkewart: CATERING.
- Kassenwart: FINANCE.
- Multiple managed roles combine permitted areas.
- Assigned members may change the status of their own manual tasks, independently of task-creation permission.
- Specialist device, driving and attendance workflows stay authoritative.

## Deployment checks
Before merging into the default branch, run GitHub Maven compile/tests, build and start the compose stack, check menu and manual task editing on mobile, test permission denial across categories, and confirm push notification preferences and the dedicated worker profile. Back up PostgreSQL before deployment. The integrated changes create/add a manual-task category database column; legacy null category values display as GENERAL.

## Deployment
After release approval and successful tests, move the Ubuntu installation to the selected stable release branch or the integrated default branch; use `git status --porcelain`, `git fetch`, `git pull --ff-only`, `docker compose config --quiet`, and `docker compose up -d --build app worker`. Do not force-reset an installation with local changes.
