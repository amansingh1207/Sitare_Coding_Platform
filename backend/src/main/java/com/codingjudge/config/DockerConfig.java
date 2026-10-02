package com.codingjudge.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DockerConfig {

    /**
     * Resolves the Docker endpoint for the host platform.
     * Linux uses the unix socket; Windows uses the Docker Desktop named pipe.
     * Override with the JUDGE_DOCKER_HOST environment variable when needed.
     */
    private static String resolveDockerHost(String configured) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win")) {
            return "npipe:////./pipe/docker_engine";
        }
        return "unix:///var/run/docker.sock";
    }

    @Bean
    public DockerClient dockerClient(@Value("${judge.docker-host:}") String dockerHost) {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(resolveDockerHost(dockerHost))
                .build();

        // docker-java 3.x requires an explicit HTTP transport; without it the
        // client throws "dockerCmdExecFactory was not specified" on first use.
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }
}