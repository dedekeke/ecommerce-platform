# Local Dev Profile (`personal`)

> Back to [README](../README.md).

To stop committing credentials to YAML, each service ships an
`application-personal.yml.example` (or `.properties.example`) under
`src/main/resources/`. Copy it (drop the `.example` suffix) and fill in
your local credentials.

```bash
for f in services/*/src/main/resources/application-personal.yml.example; do
  cp "$f" "${f%.example}"
done
cp services/media-service/src/main/resources/application-personal.properties.example \
   services/media-service/src/main/resources/application-personal.properties
```

Run a service with the profile activated:

```bash
SPRING_PROFILES_ACTIVE=personal mvn -pl services/product-service spring-boot:run
```

`scripts/run-all-services.sh` activates `personal` alongside `local` so
all services pick it up automatically.

The personal files (no `.example` suffix) are gitignored. The `.env`
file at the repo root is still used by `docker-compose.yml`; it remains
gitignored and is the source of credentials for container runs.
