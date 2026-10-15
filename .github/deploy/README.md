# Akka deploy descriptors

Two separate descriptor files, deployed differently:

## `akka-sdd-service.yml` — the Service resource

Deployed automatically by `.github/workflows/cd.yml` on every release
(`workflow_dispatch`, with `__IMAGE__` substituted for the released image
tag), using the `DEV_AKKA_DEPLOY_TOKEN` project service token.

To apply by hand:

```shell
IMAGE="ghcr.io/sriramsundhar/akka-sdd:<version>"
sed "s|__IMAGE__|${IMAGE}|" .github/deploy/akka-sdd-service.yml \
  | akka project apply -f - --project f55c6a6a-3dae-461f-b2e8-6c67809e2c93
```

## `observability.yml` — the Observability resource

**Not** applied by CD, and not expected to be: project service tokens can
view/deploy/update services but can never have "Admin project" permission,
which project-level Observability config requires — there's no `--scope`
that grants it, and no role narrower than full `admin` includes it (`admin`
also grants backoffice functions — raw entity/event access, bypassing ACLs,
managing projections — which is more power than this pipeline needs just to
export metrics/logs). Observability config also changes far less often than
the image does, so it doesn't belong in the per-release CD loop anyway.

Apply changes by hand, with your own (admin) session, whenever this file
changes:

```shell
akka project apply -f .github/deploy/observability.yml --project f55c6a6a-3dae-461f-b2e8-6c67809e2c93
akka service restart akka-sdd-service --project f55c6a6a-3dae-461f-b2e8-6c67809e2c93
```

The restart is required — observability config changes only take effect
after the service restarts.

### Prerequisites

`observability.yml` references a secret, `grafana-cloud`, holding the
Grafana Cloud Basic Auth header. Create it once (Grafana Cloud → your stack
→ "OpenTelemetry" setup page has the Instance ID and lets you create a Cloud
Access Policy token with `metrics:write` and `logs:write` scopes):

```shell
akka secret create generic grafana-cloud \
  --literal authorization="Basic $(echo -n '<INSTANCE_ID>:<TOKEN>' | base64)"
```

`endpointBaseUrl` is the base OTLP gateway URL for your Grafana Cloud stack
(no trailing `/v1/metrics` or `/v1/logs` — Akka's OTLP HTTP exporter appends
that itself; including it in the base URL double-appends the path and
breaks the export).

### If CD should ever update Observability too

Would require switching `cd.yml` from the stored service token to
workload-identity (OIDC) auth, with an `admin` role bound to this repo's
GitHub Actions identity (`akka roles add-binding --role admin
--workload-identity-subject sriramsundhar/akka-sdd ...`) — see
[CI/CD with GitHub Actions](https://doc.akka.io/operations/integrating-cicd/github-actions.html).
Deliberately not done: it would grant the automated pipeline full project
admin (including backoffice functions), not just observability, for a
resource that rarely changes.
