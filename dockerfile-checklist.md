# Dockerfile Creation Checklist

A comprehensive checklist for writing optimized, secure, and well-configured Dockerfiles. This checklist covers three main aspects: Configuration, Performance, and Security.

---

## Configuration

Ensure your image works stably, is easily configurable, and compatible across multiple environments.

### ✓ Support multiple machine architecture (amd64, arm64)
- [ ] Use `TARGETARCH` or `BUILDPLATFORM` variables for multi-platform builds
- [ ] Avoid hardcoding platform-specific binaries
- [ ] Test on both ARM (M1/M2 Mac) and AMD64 (traditional servers)
- [ ] Example: `COPY bin/app-${TARGETARCH} /app/main`

### ✓ Set explicit CMD / ENTRYPOINT / Run application as PID 1
- [ ] Use Exec form: `["executable", "param1", "param2"]` instead of Shell form
- [ ] Avoid `/bin/sh -c` wrapper to properly receive Unix signals (SIGTERM)
- [ ] Implement init process (PID 1) for zombie process cleanup
- [ ] Consider using tini for signal handling:
  ```
  ENV TINI_VERSION v0.19.0
  ADD https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini /tini
  RUN chmod +x /tini
  ENTRYPOINT ["/tini", "--", "/docker-entrypoint.sh"]
  ```

### ✓ Add a .dockerignore file
- [ ] Exclude unnecessary files and directories from build context
- [ ] Reduce build time and image size
- [ ] Common entries:
  ```
  .git
  node_modules
  build
  *.log
  .env
  ```

### ✓ Define listen port (EXPOSE)
- [ ] Document which port the container listens on
- [ ] Helps users understand the application interface
- [ ] Example: `EXPOSE 8080`

### ✓ Define HEALTHCHECK
- [ ] Enable Docker/Kubernetes to detect if the application is still healthy
- [ ] Ensure automatic restart on failure
- [ ] Example:
  ```
  HEALTHCHECK --interval=30s --timeout=3s \
    CMD curl -f http://localhost:8080/health || exit 1
  ```

### ✓ Add labels (org.opencontainers.image.*)
- [ ] Use standard OCI labels for image metadata
- [ ] Include author information
- [ ] Document source repository
- [ ] Example:
  ```
  LABEL org.opencontainers.image.authors="Your Name"
  LABEL org.opencontainers.image.source="https://github.com/user/repo"
  ```

### ✓ Pin package + dependency versions
- [ ] Avoid automatic version upgrades that break builds
- [ ] Ensure reproducible builds
- [ ] Example: `apt-get install python3=3.9.1-r0` instead of `apt-get install python3`

### ✓ Reproducible builds: use ARG
- [ ] Use build arguments for flexible version management
- [ ] Avoid hardcoding versions in the Dockerfile
- [ ] Example:
  ```
  ARG NODE_VERSION=18
  FROM node:${NODE_VERSION}-alpine
  ```

---

## Performance

Build fast, keep images small, and maximize cache efficiency.

### ✓ Use minimal, official base image
- [ ] Prefer Alpine or Distroless images
- [ ] Avoid heavy base images like Ubuntu
- [ ] Examples:
    - Good: `FROM node:18-alpine`
    - Good: `FROM gcr.io/distroless/static-debian11`
    - Bad: `FROM ubuntu:latest`

### ✓ Use multi-stage builds
- [ ] Separate build and runtime stages
- [ ] Critical for compiled languages (Go, Java, C++)
- [ ] Significantly reduces final image size
- [ ] Example:
  ```
  # Build Stage
  FROM golang:1.19 AS builder
  WORKDIR /app
  COPY . .
  RUN go build -o main .

  # Run Stage
  FROM alpine:latest
  COPY --from=builder /app/main .
  CMD ["./main"]
  ```

### ✓ Prefer COPY over ADD
- [ ] Use COPY for simplicity and clarity
- [ ] ADD can extract tar files and fetch remote URLs (harder to control cache)
- [ ] Only use ADD when you specifically need tar extraction or remote file downloading
- [ ] Example: `COPY . /app`

### ✓ Minimize number of layers
- [ ] Combine multiple RUN commands into single commands
- [ ] Reduces intermediate layers and image size
- [ ] Bad example:
  ```
  RUN apt-get update
  RUN apt-get install -y vim
  RUN apt-get install -y curl
  ```
- [ ] Good example:
  ```
  RUN apt-get update && apt-get install -y \
      vim \
      curl \
      && rm -rf /var/lib/apt/lists/*
  ```

### ✓ Order layers for cache efficiency
- [ ] Place stable, slow-changing instructions first
- [ ] Place frequently-changing instructions last
- [ ] Typical order: Base OS → Dependencies → Source Code
- [ ] Bad example:
  ```
  COPY . .
  RUN npm install  # Cache invalidated every code change
  ```
- [ ] Good example:
  ```
  COPY package.json .
  RUN npm install  # Cache preserved if package.json unchanged
  COPY . .
  ```

---

## Security

Implement DevSecOps best practices to secure your containers.

### ✓ Use base image with sha256 hash
- [ ] Avoid mutable tags like `:latest` or `:18-alpine`
- [ ] Prevent supply chain attacks
- [ ] Pin exact image contents with SHA256
- [ ] Bad: `FROM node:lts-alpine`
- [ ] Good: `FROM node@sha256:52a6d123...`

### ✓ Remove shell, package manager, debug tools
- [ ] Use Distroless images when possible
- [ ] Remove unnecessary tools that attackers could exploit
- [ ] Avoid installing vim, curl, wget, net-tools for debugging
- [ ] Reduces attack surface

### ✓ Run static scans (hadolint, trivy)
- [ ] Integrate into CI/CD pipeline
- [ ] Block builds with HIGH or CRITICAL vulnerabilities
- [ ] Enforce Dockerfile best practices
- [ ] Scan for CVEs in base images
- [ ] Example: `trivy image my-app:latest`

### ✓ Use non-root user
- [ ] Never run container as root by default
- [ ] Mitigates damage from container breakouts
- [ ] Example:
  ```
  RUN addgroup -S appgroup && adduser -S appuser -G appgroup
  USER appuser
  ```

### ✓ Don't put secrets, credentials in Dockerfile
- [ ] Never hardcode API keys, passwords, or private keys
- [ ] Secrets in layers persist even if removed in later layers
- [ ] Bad examples:
  ```
  ENV DB_PASSWORD=secret123
  COPY id_rsa /root/.ssh/
  ```
- [ ] Good practices:
    - Use environment variables at runtime: `docker run -e DB_PASSWORD=...`
    - Use Docker BuildKit secrets: `docker build --secret id=key,src=key.txt`
    - Use external secret management systems

---

## Summary

This checklist helps ensure your Dockerfiles are:
- **Configurable**: Flexible, well-documented, and portable
- **Performant**: Fast builds, small images, efficient caching
- **Secure**: Protected against vulnerabilities and supply chain attacks

Remember: Choose items that fit your project's needs. You don't need to apply all items to every Dockerfile.