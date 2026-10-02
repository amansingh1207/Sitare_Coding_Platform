# CodingJudge sandbox image
#
# Contains ONLY the toolchains needed to compile and run student code.
# Never mount application secrets or the Docker socket into this image.
# Built and run by the judge (docs/JUDGE_DESIGN.md section 6).
FROM eclipse-temurin:17-jdk-jammy

# g++ for C++17, python3 for Python. procps for process diagnostics only.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        g++ \
        python3 \
    && rm -rf /var/lib/apt/lists/*

# Working directory used by the judge for source code and compilation.
RUN mkdir -p /workspace

# Run as an unprivileged user; the judge also applies no-new-privileges,
# read-only rootfs, dropped capabilities and a PID limit at runtime.
RUN useradd --create-home --shell /usr/sbin/nologin judge \
    && chown -R judge:judge /workspace
USER judge

WORKDIR /workspace

CMD ["/bin/sh", "-c", "echo 'sandbox image ready: java, g++, python3'"]