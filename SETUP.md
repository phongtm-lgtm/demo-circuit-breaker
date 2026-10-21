# Local setup

This repository is a clean snapshot of the ATS project and its Resilience4j reference demo.
IDE metadata, build outputs and the original Git history are not included.

## ATS

- Java 21, Maven, PostgreSQL, Docker Desktop for monitoring.
- Create PostgreSQL databases `application_sdb`, `candidate_sdb`, and `job_sdb` if using job-service.
- Set `SPRING_DATASOURCE_PASSWORD` in each service's IntelliJ Run Configuration.
- Override `SPRING_DATASOURCE_URL` and `SPRING_DATASOURCE_USERNAME` if necessary.
- Gateway additionally requires `APP_CLIENT_ID` with the actual Google OAuth client ID.
- For a direct Circuit Breaker demo, run application-service and candidate-service with the `cb-demo` profile.
- Hibernate creates the schema; then run the SQL seed scripts in `ats_parrent/docs/circuit-breaker-demo/`.
- Run monitoring: `docker compose -f ats_parrent/infras/monitoring/compose.yml up -d`.
- Prometheus: http://localhost:9090. Grafana: http://localhost:3002.
- The Grafana password in Compose is a local-demo default; change it before exposing the stack publicly.

See `ats_parrent/docs/circuit-breaker-demo/README.md` for requests and the Grafana dashboard import.
