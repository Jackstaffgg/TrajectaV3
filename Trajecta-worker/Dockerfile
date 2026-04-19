# syntax=docker/dockerfile:1

FROM rust:1.88-slim AS builder
WORKDIR /app

COPY Cargo.toml ./
COPY Cargo.lock ./
COPY src ./src
RUN cargo build --release --locked

FROM debian:bookworm-slim
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /app/target/release/trajecta-worker /usr/local/bin/trajecta-worker

CMD ["trajecta-worker"]
