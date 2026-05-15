# Base Prometheus image
# Contains Prometheus server binaries and default runtime setup
FROM prom/prometheus:latest

# Replace default Prometheus configuration
# with custom production scraping configuration
COPY prometheus-prod.yml /etc/prometheus.yml