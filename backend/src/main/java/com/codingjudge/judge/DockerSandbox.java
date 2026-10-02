package com.codingjudge.judge;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.model.*;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
public class DockerSandbox {

    private final DockerClient dockerClient;
    private final String image;
    private final long defaultTimeoutMs;
    private final int defaultMemoryLimitMb;
    private final double defaultCpuLimit;

    public DockerSandbox() {
        this.dockerClient = null;
        this.image = "";
        this.defaultTimeoutMs = 10000;
        this.defaultMemoryLimitMb = 256;
        this.defaultCpuLimit = 1.0;
    }

    public DockerSandbox(DockerClient dockerClient,
                         @Value("${judge.docker-image:codingjudge/sandbox:latest}") String image,
                         @Value("${judge.timeout-ms:10000}") long defaultTimeoutMs,
                         @Value("${judge.memory-limit-mb:256}") int defaultMemoryLimitMb,
                         @Value("${judge.cpu-limit:1.0}") double defaultCpuLimit) {
        this.dockerClient = dockerClient;
        this.image = image;
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.defaultMemoryLimitMb = defaultMemoryLimitMb;
        this.defaultCpuLimit = defaultCpuLimit;
    }

    public CompilationResult compile(String sourceCode, LanguageExecutor executor) {
        if (dockerClient == null) {
            return CompilationResult.failure("Docker not available in test environment", -1);
        }
        String containerId = null;
        try {
            containerId = createContainer();
            copySourceToContainer(containerId, sourceCode, executor);

            ExecCreateCmdResponse exec = dockerClient.execCreateCmd(containerId)
                    .withCmd(executor.getCompileCommand(executor.getSourceFileName()).split(" "))
                    .withWorkingDir("/workspace")
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();

            StringBuilder output = new StringBuilder();
            ResultCallback.Adapter callback = new ResultCallback.Adapter() {
                public void onNext(Frame frame) {
                    output.append(new String(frame.getPayload()));
                }
            };

            dockerClient.execStartCmd(exec.getId())
                    .exec(callback)
                    .awaitCompletion(30, TimeUnit.SECONDS);

            InspectExecResponse inspect = dockerClient.inspectExecCmd(exec.getId()).exec();
            int exitCode = inspect.getExitCode();
            String outputStr = output.toString();

            if (exitCode == 0) {
                return CompilationResult.success(outputStr);
            }
            return CompilationResult.failure(outputStr, exitCode);

        } catch (Exception e) {
            return CompilationResult.failure("Compilation failed: " + e.getMessage(), -1);
        } finally {
            if (containerId != null) {
                cleanupContainer(containerId);
            }
        }
    }

    public ExecutionResult execute(String sourceCode, String input,
                                   LanguageExecutor executor,
                                   long timeoutMs, int memoryLimitMb) {
        if (dockerClient == null) {
            return ExecutionResult.error("Docker not available in test environment", -1);
        }
        String containerId = null;
        try {
            containerId = createContainer();
            copySourceToContainer(containerId, sourceCode, executor);

            // Compile first
            CompilationResult compileResult = compile(sourceCode, executor);
            if (!compileResult.success()) {
                return ExecutionResult.compilationError(compileResult.output(), compileResult.exitCode());
            }

            // Execute
            ExecCreateCmdResponse exec = dockerClient.execCreateCmd(containerId)
                    .withCmd(executor.getExecuteCommand("Main").split(" "))
                    .withWorkingDir("/workspace")
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();

            // Write input to stdin
            PipedInputStream pipedIn = new PipedInputStream();
            PipedOutputStream pipedOut = new PipedOutputStream(pipedIn);
            new Thread(() -> {
                try {
                    pipedOut.write((input != null ? input : "").getBytes());
                    pipedOut.close();
                } catch (IOException ignored) {
                }
            }).start();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            ResultCallback.Adapter callback = new ResultCallback.Adapter() {
                public void onNext(Frame frame) {
                    String payload = new String(frame.getPayload());
                    if (frame.getStreamType() == StreamType.STDOUT) {
                        stdout.append(payload);
                    } else if (frame.getStreamType() == StreamType.STDERR) {
                        stderr.append(payload);
                    }
                }
            };

            dockerClient.execStartCmd(exec.getId())
                    .withStdIn(pipedIn)
                    .exec(callback)
                    .awaitCompletion(timeoutMs, TimeUnit.MILLISECONDS);

            InspectExecResponse inspect = dockerClient.inspectExecCmd(exec.getId()).exec();
            int exitCode = inspect.getExitCode();

            if (exitCode != 0) {
                return ExecutionResult.error(stderr.toString(), exitCode);
            }
            return ExecutionResult.success(stdout.toString(), 0, 0);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ExecutionResult.timeout();
        } catch (Exception e) {
            return ExecutionResult.error("Execution failed: " + e.getMessage(), -1);
        } finally {
            if (containerId != null) {
                cleanupContainer(containerId);
            }
        }
    }

    private String createContainer() {
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withNetworkMode("none")
                .withMemory((long) defaultMemoryLimitMb * 1024L * 1024L)
                .withCpuCount((long) (defaultCpuLimit * 1000000000L)) // nanoCPUs
                .withPidsLimit(64L)
                .withReadonlyRootfs(true)
                .withCapDrop(Capability.ALL)
                .withAutoRemove(false);

        CreateContainerResponse response = dockerClient.createContainerCmd(image)
                .withHostConfig(hostConfig)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withTty(false)
                .withWorkingDir("/workspace")
                .exec();

        String containerId = response.getId();
        dockerClient.startContainerCmd(containerId).exec();
        return containerId;
    }

    private void copySourceToContainer(String containerId, String sourceCode, LanguageExecutor executor) {
        try {
            Path tempDir = Files.createTempDirectory("sandbox-");
            Path sourceFile = tempDir.resolve(executor.getSourceFileName());
            Files.writeString(sourceFile, sourceCode);

            // Create tar archive in memory
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (TarArchiveOutputStream tarOut = new TarArchiveOutputStream(baos)) {
                TarArchiveEntry entry = new TarArchiveEntry(executor.getSourceFileName());
                entry.setSize(sourceCode.getBytes().length);
                tarOut.putArchiveEntry(entry);
                tarOut.write(sourceCode.getBytes());
                tarOut.closeArchiveEntry();
                tarOut.finish();
            }
            byte[] tarBytes = baos.toByteArray();

            dockerClient.copyArchiveToContainerCmd(containerId)
                    .withRemotePath("/workspace")
                    .withTarInputStream(new ByteArrayInputStream(tarBytes))
                    .exec();

            Files.deleteIfExists(sourceFile);
            Files.deleteIfExists(tempDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy source to container: " + e.getMessage(), e);
        }
    }

    private void cleanupContainer(String containerId) {
        try {
            dockerClient.killContainerCmd(containerId).exec();
            dockerClient.removeContainerCmd(containerId)
                    .withForce(true)
                    .withRemoveVolumes(true)
                    .exec();
        } catch (Exception ignored) {
        }
    }
}