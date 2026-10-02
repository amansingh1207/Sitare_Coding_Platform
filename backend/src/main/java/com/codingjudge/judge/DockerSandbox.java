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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
public class DockerSandbox {

    private static final long COMPILE_TIMEOUT_SECONDS = 30;
    /** Base64 characters per exec call. Keeps each command well under the shell arg limit. */
    private static final int CHUNK_CHARS = 24_000;
    private static final java.util.regex.Pattern SAFE_FILE_NAME =
            java.util.regex.Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private static final int MAX_LOGGED_CHARS = 500;
    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(DockerSandbox.class);

    /** Truncates captured program output so logs stay bounded. */
    private static String truncateForLog(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MAX_LOGGED_CHARS ? text : text.substring(0, MAX_LOGGED_CHARS) + "...";
    }

    private final DockerClient dockerClient;
    private final String image;
    private final long defaultTimeoutMs;
    private final int defaultMemoryLimitMb;
    private final double defaultCpuLimit;

    /** Test-only constructor. Not used by Spring: the container of this class
     *  requires the Docker-backed constructor below to be wired. */
    public DockerSandbox() {
        this.dockerClient = null;
        this.image = "";
        this.defaultTimeoutMs = 10000;
        this.defaultMemoryLimitMb = 256;
        this.defaultCpuLimit = 1.0;
    }

    @org.springframework.beans.factory.annotation.Autowired
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
            return compileInContainer(containerId, executor);

        } catch (Exception e) {
            return CompilationResult.failure("Compilation failed: " + e.getMessage(), -1);
        } finally {
            if (containerId != null) {
                cleanupContainer(containerId);
            }
        }
    }

    /**
     * Compiles inside an already-started container that has the source copied in.
     * Compilation must happen in the same container that later runs the program,
     * otherwise the produced binaries/class files do not survive.
     */
    private CompilationResult compileInContainer(String containerId, LanguageExecutor executor) {
        try {
            ExecCreateCmdResponse exec = dockerClient.execCreateCmd(containerId)
                    .withCmd(executor.getCompileCommand(executor.getSourceFileName()).split(" "))
                    .withWorkingDir("/workspace")
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();

            StringBuilder output = new StringBuilder();
            ResultCallback.Adapter<Frame> callback = new ResultCallback.Adapter<>() {
                @Override
                public void onNext(Frame frame) {
                    output.append(new String(frame.getPayload(), StandardCharsets.UTF_8));
                }
            };

            dockerClient.execStartCmd(exec.getId())
                    .exec(callback)
                    .awaitCompletion(COMPILE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            InspectExecResponse inspect = dockerClient.inspectExecCmd(exec.getId()).exec();
            int exitCode = inspect.getExitCode();
            String outputStr = output.toString();

            if (exitCode == 0) {
                return CompilationResult.success(outputStr);
            }
            return CompilationResult.failure(outputStr, exitCode);

        } catch (Exception e) {
            LOG.warn("Sandbox compilation error", e);
            return CompilationResult.failure("Compilation failed: " + e.getMessage(), -1);
        }
    }

    public ExecutionResult execute(String sourceCode, String input,
                                   LanguageExecutor executor,
                                   long timeoutMs, int memoryLimitMb) {
        List<ExecutionResult> results = executeBatch(
                sourceCode,
                input == null ? List.of("") : List.of(input),
                executor,
                timeoutMs,
                memoryLimitMb);
        return results.isEmpty()
                ? ExecutionResult.error("Execution failed: no result produced", -1)
                : results.get(0);
    }

    /**
     * Executes the same source against many inputs inside ONE container.
     *
     * The old path created, compiled in and destroyed a container per test
     * case, so a submission with N tests paid N container lifecycles and N
     * compilations. This runs create + copy + compile exactly once and then
     * only rewrites the input file and re-runs the program per test, which is
     * what makes multi-test submissions finish in seconds instead of minutes.
     * Each program run is still a fresh process with its own time limit.
     */
    public List<ExecutionResult> executeBatch(String sourceCode, List<String> inputs,
                                             LanguageExecutor executor,
                                             long timeoutMs, int memoryLimitMb) {
        if (dockerClient == null) {
            LOG.error("Docker client is not configured; cannot execute submission code");
            List<ExecutionResult> errors = new ArrayList<>(inputs.size());
            for (int i = 0; i < inputs.size(); i++) {
                errors.add(ExecutionResult.error("Docker not available in test environment", -1));
            }
            return errors;
        }
        String containerId = null;
        try {
            containerId = createContainer();
            copySourceToContainer(containerId, sourceCode, executor);

            // Compile once in the SAME container that will run the program.
            // Interpreted languages skip this step entirely.
            if (executor.requiresCompilation()) {
                CompilationResult compileResult = compileInContainer(containerId, executor);
                if (!compileResult.success()) {
                    LOG.warn("Compilation failed: exitCode={} output={}",
                            compileResult.exitCode(), truncateForLog(compileResult.output()));
                    ExecutionResult failure = ExecutionResult.compilationError(
                            compileResult.output(), compileResult.exitCode());
                    return Collections.nCopies(inputs.size(), failure);
                }
            }

            List<ExecutionResult> results = new ArrayList<>(inputs.size());
            for (String input : inputs) {
                results.add(runInContainer(containerId, input, executor, timeoutMs));
            }
            return results;

        } catch (Exception e) {
            LOG.warn("Sandbox execution error", e);
            return Collections.nCopies(inputs.size(),
                    ExecutionResult.error("Execution failed: " + e.getMessage(), -1));
        } finally {
            if (containerId != null) {
                cleanupContainer(containerId);
            }
        }
    }

    /** Runs the already-compiled program once against a single input. */
    private ExecutionResult runInContainer(String containerId, String input,
                                          LanguageExecutor executor, long timeoutMs) {
        try {
            // Feed stdin from a file rather than piping it: exec stdin via
            // docker-java's async exec ends with "The pipe has been ended".
            writeInputFile(containerId, input);

            // Execute with input redirected from that file.
            String runCmd = executor.getExecuteCommand("Main") + " < /tmp/input.txt";
            ExecCreateCmdResponse exec = dockerClient.execCreateCmd(containerId)
                    .withCmd("sh", "-c", runCmd)
                    .withWorkingDir("/workspace")
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            ResultCallback.Adapter<Frame> callback = new ResultCallback.Adapter<>() {
                @Override
                public void onNext(Frame frame) {
                    String payload = new String(frame.getPayload(), StandardCharsets.UTF_8);
                    if (frame.getStreamType() == StreamType.STDOUT) {
                        stdout.append(payload);
                    } else if (frame.getStreamType() == StreamType.STDERR) {
                        stderr.append(payload);
                    }
                }
            };

            long startedAtNanos = System.nanoTime();
            boolean completed = dockerClient.execStartCmd(exec.getId())
                    .exec(callback)
                    .awaitCompletion(timeoutMs, TimeUnit.MILLISECONDS);
            callback.close();
            long runtimeMs = (System.nanoTime() - startedAtNanos) / 1_000_000L;

            if (!completed) {
                // Execution outran the time limit. The container is killed and
                // removed by the batch's finally block, which also reaps the process.
                LOG.warn("Sandbox execution exceeded {} ms", timeoutMs);
                return ExecutionResult.timeout();
            }

            long peakMemoryKb = readPeakMemoryKb(containerId);

            InspectExecResponse inspect = dockerClient.inspectExecCmd(exec.getId()).exec();
            int exitCode = inspect.getExitCode();

            if (exitCode != 0) {
                String err = stderr.toString();
                if (looksOutOfMemory(err)) {
                    LOG.warn("Sandbox execution ran out of memory: exitCode={}", exitCode);
                    return ExecutionResult.oomKilledWithOutput(err, exitCode);
                }
                LOG.warn("Sandbox execution failed: exitCode={} stderr={}",
                        exitCode, truncateForLog(err));
                return ExecutionResult.error(stderr.toString(), exitCode);
            }
            return ExecutionResult.success(stdout.toString(), runtimeMs, peakMemoryKb);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ExecutionResult.timeout();
        } catch (Exception e) {
            LOG.warn("Sandbox execution error", e);
            return ExecutionResult.error("Execution failed: " + e.getMessage(), -1);
        }
    }

    private String createContainer() {
        long memoryBytes = (long) defaultMemoryLimitMb * 1024L * 1024L;
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withNetworkMode("none")
                .withMemory(memoryBytes)
                // Equal memory and memory-swap disables swap for the container.
                // Docker defaults swap to 2x the memory limit, which lets a
                // submission exceed its RAM budget before being OOM killed.
                .withMemorySwap(memoryBytes)
                .withCpuCount((long) (defaultCpuLimit * 1000000000L)) // nanoCPUs
                .withPidsLimit(64L)
                .withReadonlyRootfs(true)
                // The root filesystem stays read-only; only these two paths are
                // writable. /workspace holds the submitted source and compiler
                // output, /tmp is scratch space for the program itself.
                .withTmpFs(Map.of(
                        "/workspace", "size=64m,mode=1777,exec",
                        "/tmp", "size=64m,mode=1777,exec"))
                .withCapDrop(Capability.ALL)
                .withAutoRemove(false);

        CreateContainerResponse response = dockerClient.createContainerCmd(image)
                .withHostConfig(hostConfig)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withTty(false)
                // Keep the container alive: the image's default CMD exits
                // immediately, which would make every later exec fail with
                // "container is not running".
                .withCmd("tail", "-f", "/dev/null")
                .withWorkingDir("/workspace")
                .exec();

        String containerId = response.getId();
        dockerClient.startContainerCmd(containerId).exec();
        return containerId;
    }

    /**
     * Writes the submitted source into the container's /workspace.
     *
     * Two Docker behaviours force this approach:
     *  1. The archive/copy API refuses to write into a container created with a
     *     read-only root filesystem ("container rootfs is marked read-only"), even
     *     when the target path is a tmpfs mount. Verified against Docker 29.x.
     *  2. Streaming over exec stdin through docker-java's async exec ends with
     *     "The pipe has been ended".
     *
     * So the source is base64-encoded and decoded inside the container. /workspace
     * is a writable tmpfs mount, so the rest of the filesystem stays read-only.
     * Base64 output is shell-safe (no quotes, spaces or metacharacters).
     */
    private void copySourceToContainer(String containerId, String sourceCode, LanguageExecutor executor) {
        String fileName = executor.getSourceFileName();
        // Defence in depth: the name comes from our own adapters, but validate it
        // anyway so it can never become a path-traversal vector.
        if (!SAFE_FILE_NAME.matcher(fileName).matches()) {
            throw new IllegalArgumentException("Unsafe source file name: " + fileName);
        }
        writeFileInChunks(containerId, "/workspace/" + fileName, sourceCode);
    }

    /**
     * Writes a file inside the container by streaming base64 chunks through
     * separate small exec calls.
     *
     * A single-command write fails for anything above roughly 90 KB of source:
     * base64 inflates the payload by a third and the resulting exec command
     * exceeds the shell's argument limit ("exit 255"). Since the platform
     * accepts up to 256 KB of source, the payload must be chunked.
     *
     * Base64 output is shell-safe (only A-Z a-z 0-9 + / =), and the target path
     * is built from validated inputs, so no student data is interpreted by the shell.
     */
    private void writeFileInChunks(String containerId, String targetPath, String content) {
        String encoded = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        String stagingPath = "/tmp/.staged.b64";

        try {
            int offset = 0;
            boolean first = true;
            while (offset < encoded.length()) {
                int end = Math.min(offset + CHUNK_CHARS, encoded.length());
                String chunk = encoded.substring(offset, end);
                // Truncate on the first chunk, append on the rest.
                String redirect = first ? ">" : ">>";
                runShell(containerId, "echo " + chunk + " " + redirect + " " + stagingPath);
                first = false;
                offset = end;
            }
            if (first) {
                // Empty content still needs to produce an empty file.
                runShell(containerId, ": > " + stagingPath);
            }
            runShell(containerId, "base64 -d " + stagingPath + " > " + targetPath);
            runShell(containerId, "rm -f " + stagingPath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write file into container: " + e.getMessage(), e);
        }
    }

    /** Runs a shell command inside the container, throwing if it exits non-zero. */
    private void runShell(String containerId, String command) {
        try {
            ExecCreateCmdResponse exec = dockerClient.execCreateCmd(containerId)
                    .withCmd("sh", "-c", command)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();

            dockerClient.execStartCmd(exec.getId())
                    .exec(new ResultCallback.Adapter<>())
                    .awaitCompletion(30, TimeUnit.SECONDS);

            InspectExecResponse inspect = dockerClient.inspectExecCmd(exec.getId()).exec();
            if (inspect.getExitCode() != 0) {
                throw new IllegalStateException(
                        "Command failed with exit " + inspect.getExitCode());
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Reads peak memory usage for the container from cgroup v2 (or v1 fallback).
     * Returns 0 when the value cannot be read, which the UI renders as unknown.
     */
    private long readPeakMemoryKb(String containerId) {
        try {
            ExecCreateCmdResponse exec = dockerClient.execCreateCmd(containerId)
                    .withCmd("sh", "-c",
                            "cat /sys/fs/cgroup/memory.peak 2>/dev/null "
                            + "|| cat /sys/fs/cgroup/memory/memory.max_usage_in_bytes 2>/dev/null")
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();

            StringBuilder out = new StringBuilder();
            dockerClient.execStartCmd(exec.getId())
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            if (frame.getStreamType() == StreamType.STDOUT) {
                                out.append(new String(frame.getPayload(), StandardCharsets.UTF_8));
                            }
                        }
                    })
                    .awaitCompletion(5, TimeUnit.SECONDS);

            String value = out.toString().trim();
            if (value.isEmpty()) {
                return 0;
            }
            long bytes = Long.parseLong(value);
            // cgroup reports bytes; the API stores kilobytes.
            return bytes / 1024L;
        } catch (Exception e) {
            LOG.debug("Could not read peak memory: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Detects out-of-memory from the program's own error text.
     *
     * Runtimes report OOM themselves and exit non-zero rather than being killed,
     * so exit code 137 alone misses most cases:
     *   Java    -> java.lang.OutOfMemoryError
     *   CPython -> MemoryError
     *   C++     -> std::bad_alloc / terminate called after throwing
     */
    private static boolean looksOutOfMemory(String stderr) {
        if (stderr == null) {
            return false;
        }
        return stderr.contains("OutOfMemoryError")
                || stderr.contains("MemoryError")
                || stderr.contains("bad_alloc")
                || stderr.contains("Cannot allocate memory")
                || stderr.contains("out of memory");
    }

    /** Writes the test-case input to /tmp/input.txt so it can be redirected into the program. */
    private void writeInputFile(String containerId, String input) {
        writeFileInChunks(containerId, "/tmp/input.txt", input != null ? input : "");
    }

    /**
     * Always removes the container. Kill and remove are attempted independently:
     * if the container already exited, kill throws and would otherwise prevent the
     * remove from running, leaking the container.
     */
    private void cleanupContainer(String containerId) {
        try {
            dockerClient.killContainerCmd(containerId).exec();
        } catch (Exception e) {
            LOG.debug("Kill container {} failed (may already be stopped): {}",
                    containerId, e.getMessage());
        }
        try {
            dockerClient.removeContainerCmd(containerId)
                    .withForce(true)
                    .withRemoveVolumes(true)
                    .exec();
        } catch (Exception e) {
            LOG.error("Failed to remove sandbox container {}", containerId, e);
        }
    }
}