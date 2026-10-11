# ADR-0015: Deploy to one Linux VM with Docker Compose, Caddy and GitHub Actions

- **Status:** Accepted (the host provider and its cost are recorded in an Addendum in Chunk 14)
- **Date:** 2026-10-04
- **Chunk:** planning (CI from Chunk 1, deployment in Chunk 14)
- **Supersedes:** PLAN v2's "pick a host in Chunk 13"

## Context
Production must provide:
- a public **HTTPS** endpoint for GitHub webhooks,
- PostgreSQL with pgvector,
- **Kafka** (ADR-0014),
- the Spring Boot app.

It must keep running with the owner's laptop switched off. The project is self-funded, and the owner should learn real deployment skills.

Kafka changes the picture. Free PaaS tiers rarely include Kafka, and managed-Kafka free offers tend to be time-limited credits whose terms change.

## Decision
- **Production is one Linux VM** (Ubuntu LTS, at least 2 vCPU and 4 GB RAM) running **Docker Compose** with four containers:
  - `caddy`: reverse proxy and automatic HTTPS via Let's Encrypt.
  - `app`: the Spring Boot app.
  - `postgres`: the pgvector image, on a persistent volume.
  - `kafka`: Apache Kafka in KRaft mode, a single broker, on a persistent volume.
- **Exposure:**
  - Only ports 80/443 (Caddy) are public, plus SSH with key-only login.
  - Postgres and Kafka publish **no** host ports.
  - Caddy routes **only** `/webhooks/github` and `/actuator/health`.
  - The dry-run and admin APIs (which call paid models) are reachable only through an SSH tunnel.
- **CI:** a GitHub Actions workflow `ci.yml` runs `./mvnw -B verify` (with Testcontainers) on every push and PR, starting in Chunk 1.
- **CD:** a workflow `deploy.yml` runs on a git tag `v*` (or manual dispatch). It runs the tests, builds the image (multi-stage Dockerfile on Temurin 25), and pushes it to **GHCR** tagged with the git SHA and version. It then SSHes to the VM, runs `docker compose pull` and `docker compose up -d`, and checks the health endpoint.
- **Rollback:** redeploy the previous image tag. This requires Flyway migrations to be **backward compatible** (expand/contract).
- **Backups:** a nightly `pg_dump`, kept for 7 days on the server, plus an off-server copy. A restore is rehearsed once.
- **Host:** an Oracle Cloud Always Free ARM VM if one can be obtained (it needs an arm64 image built with buildx), otherwise a low-cost x86 VPS. Check current pricing and terms at the time.

Full runbook: [docs/deployment.md](../deployment.md).

## Alternatives considered
- **PaaS** (Render, Railway, Fly.io) + managed Postgres (Neon, Supabase) + managed Kafka (Confluent Cloud, Aiven, Redpanda): less ops work, but 3–4 vendors and accounts, free tiers that change, and Kafka cost. It is a reasonable switch later.
- **Kubernetes** (k3s, EKS, GKE): overkill for four containers and too much learning at once. It is a stretch item.
- **AWS ECS + RDS + MSK:** a realistic enterprise stack, but too expensive for a portfolio project (MSK in particular).
- **Running on the laptop with a tunnel:** not a real deployment, and it fails the "laptop off" check.

## Consequences
- **Good:**
  - The same Compose model as local development.
  - Free or cheap.
  - Teaches Linux, Docker, TLS, CI/CD, backups and rollback, which are all interview material.
- **Bad / costs:**
  - The owner is the ops team: OS updates, disk, backups.
  - One VM is a single point of failure, with no high availability.

## Revisit if
The VM operations burden is too high (then move to PaaS plus managed services), or high availability becomes a requirement.

## Addendum (2026-10-04): free-hosting findings
The decision above is unchanged. This addendum records facts checked in the providers' docs on 2026-10-04 (details in [docs/deployment.md §3.1](../deployment.md)):
- **Oracle Cloud Always Free A1** (up to 2 OCPUs and 12 GB RAM, 200 GB storage) can run the full stack, **Kafka included, at $0**. The catches are signup and capacity, and the rule that Oracle reclaims a VM when CPU, network and memory are all below 20% for 7 days. The owner tries to get the VM in **Chunk 0**.
- **Free PaaS tiers can't host this design as it stands.** A sleeping instance misses GitHub's 10-second webhook window, which GitHub does not retry, and there is no free Kafka.
- **Checkpoint before Chunk 12:** if neither the Oracle VM nor a paid VPS is available, write a new ADR that supersedes ADR-0014. It would use the Postgres queue on Render free + Neon free, with the changes listed in deployment.md §3.1.

## Addendum (2026-10-11): Oracle timing
The decision above is unchanged; this changes only *when* the Oracle steps happen.
- **Account signup moves from Chunk 0 to the Chunk 12 checkpoint, and the VM is created in Chunk 14.** Reason (the owner's point): it is *VMs* that get reclaimed when idle, and there is nothing to host before Chunk 14. Signing up at the checkpoint still answers "is $0 hosting possible?" before Kafka is built.
- **The home region is chosen at signup and can't be changed,** and Always Free resources exist only in that region, so pick it carefully.
- **Immediate fallback if A1 capacity is unavailable at Chunk 14:** Contabo Cloud VPS 4 (4 vCPU / 8 GB / 100 GB SSD). As checked on 2026-10-04 it costs €5.50/month + 18% GST ≈ ₹703 on a 1-month term, with no setup fee.
- **Sizing tip:** give the A1 VM roughly what the stack needs (e.g. 2 OCPUs / 6 GB) rather than all 12 GB. About 3 GB used out of 6 GB keeps memory utilisation well above the 20% idle threshold.

## Addendum (2026-10-11): how the image is built (pre-build dry run)
This refines the "CD" bullet. The decision itself (one VM, Compose, Caddy, GHCR, SSH deploy) is unchanged.
- **CI builds the jar natively** (`./mvnw -B package` on the runner). The **Dockerfile is runtime-only**: `eclipse-temurin:25-jre` plus the copied jar, running as a non-root user with `--enable-native-access=ALL-UNNAMED`. It is not a multi-stage Maven build.
- **Multi-arch images:** `docker buildx --platform linux/amd64,linux/arm64`. The image build only copies files, so the arm64 variant for Oracle doesn't need slow compilation under QEMU emulation.
- **Image names are lowercase** (`ghcr.io/<lowercase-user>/ai-pr-reviewer`). Check the package's **visibility** after the first push (make it public, or log in on the server).
- **Oracle-specific:** open ports 80/443 in the VCN Security List **and** in the instance's iptables rules.
- Details: [docs/pitfalls.md §10](../pitfalls.md#10-deployment-chunk-14).
