package com.grw.xiaobai.hybrid.llm.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.Data;

/**
 * 命令行执行器，支持超时控制和输出流捕获
 * 跨平台编码策略：动态检测 Windows 控制台编码，Linux/macOS 使用 UTF-8
 */
public class CommandExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandExecutor.class);

    /** 命令执行超时被强制终止时记录的退出码 */
    private static final int TIMEOUT_EXIT_CODE = -1000;
    /** grep 无匹配时退出码为 1，与 0 一样视为执行成功 */
    private static final Pattern GREP_COMMAND_PATTERN = Pattern.compile("^\\s*grep\\s+");
    /** 等待 stdout/stderr 读取线程结束的最长时间 */
    private static final long GOBBLER_JOIN_TIMEOUT_MILLIS = 1000;
    /** destroyForcibly 后等待进程退出的最长时间 */
    private static final long KILL_WAIT_TIMEOUT_MILLIS = 500;
    /** 非 debug 级别下 STDERR 日志的最大输出长度 */
    private static final int STDERR_LOG_LIMIT = 200;
    /** 延迟初始化 Windows 控制台编码，避免类加载时启动进程 */
    private static final AtomicReference<Charset> WINDOWS_CONSOLE_CHARSET = new AtomicReference<>();

    /**
     * 执行命令（Windows 通过 cmd.exe，其它平台通过 sh -c），不关心实时输出
     */
    public static CommandResult executeCommand(String command, long timeoutMillis) {
        return executeCommand(command, timeoutMillis, null);
    }

    /**
     * 执行命令，并通过 consumer 实时消费每一行标准输出
     */
    public static CommandResult executeCommand(String command, long timeoutMillis, Consumer<String> stdoutLineConsumer) {
        List<String> commandParts = isWindows()
                ? Arrays.asList("cmd.exe", "/c", command)
                : Arrays.asList("sh", "-c", command);
        try {
            return executeCommand(commandParts, timeoutMillis, stdoutLineConsumer);
        } catch (Exception e) {
            LOGGER.error("execute command {} error", command, e);
            return new CommandResult(false, -1, null, null, false);
        }
    }

    /**
     * 以参数列表方式执行命令，带超时控制，返回执行结果
     */
    public static CommandResult executeCommand(List<String> commandParts, long timeoutMillis,
                                               Consumer<String> stdoutLineConsumer) throws IOException, InterruptedException {
        String commandString = String.join(" ", commandParts);
        LOGGER.info("开始执行命令: [{}], 超时时间: {}ms", commandString, timeoutMillis);

        ProcessBuilder processBuilder = new ProcessBuilder(commandParts);
        Charset outputCharset = isWindows() ? getWindowsConsoleCharset() : StandardCharsets.UTF_8;

        // gobbler 线程写入、主线程读取，使用线程安全的 StringBuffer
        StringBuffer stdOutBuffer = new StringBuffer();
        StringBuffer stdErrBuffer = new StringBuffer();

        Process process = null;
        boolean timedOut = false;
        int exitCode = -1;
        Thread stdOutGobbler = null;
        Thread stdErrGobbler = null;

        try {
            process = processBuilder.start();
            final Process runningProcess = process;
            String threadNamePrefix = commandString.length() > 20 ? commandString.substring(0, 20) : commandString;

            stdOutGobbler = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(runningProcess.getInputStream(), outputCharset))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdOutBuffer.append(line).append(System.lineSeparator());
                        if (stdoutLineConsumer != null) {
                            stdoutLineConsumer.accept(line);
                        }
                    }
                } catch (IOException e) {
                    LOGGER.warn("读取命令 [{}] STDOUT 时出错 (可能因为进程已终止): {}", commandString, e.getMessage());
                }
            }, "stdout-gobbler-" + threadNamePrefix);

            stdErrGobbler = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(runningProcess.getErrorStream(), outputCharset))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdErrBuffer.append(line).append(System.lineSeparator());
                    }
                } catch (IOException e) {
                    LOGGER.warn("读取命令 [{}] STDERR 时出错 (可能因为进程已终止): {}", commandString, e.getMessage());
                }
            }, "stderr-gobbler-" + threadNamePrefix);

            stdOutGobbler.start();
            stdErrGobbler.start();

            if (process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                exitCode = process.exitValue();
                LOGGER.info("命令 [{}] 正常退出, 退出码: {}", commandString, exitCode);
            } else {
                timedOut = true;
                LOGGER.warn("命令 [{}] 执行超时 ({}ms), 将强制终止.", commandString, timeoutMillis);
            }
        } finally {
            if (process != null) {
                cleanupProcess(process, commandString, timedOut);
            }
            joinGobbler(stdOutGobbler, "STDOUT", commandString);
            joinGobbler(stdErrGobbler, "STDERR", commandString);
        }

        if (timedOut) {
            exitCode = TIMEOUT_EXIT_CODE;
        }
        String stdOut = stdOutBuffer.toString();
        String stdErr = stdErrBuffer.toString();
        logOutput(commandString, stdOut, stdErr);

        boolean success = isCommandSuccess(commandParts, timedOut, exitCode);
        LOGGER.info("命令 [{}] 执行完成. Success: {}, TimedOut: {}, ExitCode: {}, output:{}",
                commandString, success, timedOut, exitCode, stdOut);
        return new CommandResult(success, exitCode, stdOut, stdErr, timedOut);
    }

    private static void cleanupProcess(Process process, String commandString, boolean timedOut) {
        if (!process.isAlive()) {
            if (timedOut) {
                LOGGER.debug("命令 [{}] 在超时判断后自行结束", commandString);
            }
            return;
        }
        if (timedOut) {
            LOGGER.debug("命令 [{}] 超时后仍在运行, 执行 destroyForcibly()", commandString);
        } else {
            LOGGER.warn("命令 [{}] 在 finally 块中仍处于活动状态 (非超时情况), 执行 destroyForcibly()", commandString);
        }
        process.destroyForcibly();
        try {
            if (!process.waitFor(KILL_WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                LOGGER.warn("命令 [{}] 在 destroyForcibly 后 {}ms 仍未终止.", commandString, KILL_WAIT_TIMEOUT_MILLIS);
            }
        } catch (InterruptedException e) {
            LOGGER.warn("在等待 destroyForcibly 完成时被中断 for command [{}]", commandString);
            Thread.currentThread().interrupt();
        }
    }

    private static void joinGobbler(Thread gobbler, String streamName, String commandString) {
        if (gobbler == null || !gobbler.isAlive()) {
            return;
        }
        try {
            gobbler.join(GOBBLER_JOIN_TIMEOUT_MILLIS);
        } catch (InterruptedException e) {
            LOGGER.warn("在等待流读取线程结束时被中断 for command [{}]", commandString);
            Thread.currentThread().interrupt();
            return;
        }
        if (gobbler.isAlive()) {
            LOGGER.warn("{} gobbler for [{}] did not finish in {}ms, output might be incomplete.",
                    streamName, commandString, GOBBLER_JOIN_TIMEOUT_MILLIS);
            gobbler.interrupt();
        }
    }

    private static void logOutput(String commandString, String stdOut, String stdErr) {
        if (LOGGER.isDebugEnabled()) {
            if (!stdOut.isEmpty()) {
                LOGGER.debug("命令 [{}] STDOUT:\n{}", commandString, stdOut.trim());
            }
            if (!stdErr.isEmpty()) {
                LOGGER.debug("命令 [{}] STDERR:\n{}", commandString, stdErr.trim());
            }
        } else if (!stdErr.isEmpty()) {
            LOGGER.info("命令 [{}] STDERR (摘要):\n{}", commandString,
                    stdErr.substring(0, Math.min(stdErr.length(), STDERR_LOG_LIMIT)).trim()
                            + (stdErr.length() > STDERR_LOG_LIMIT ? "..." : ""));
        }
    }

    private static boolean isCommandSuccess(List<String> commandParts, boolean timedOut, int exitCode) {
        if (timedOut) {
            return false;
        }
        if (isGrepCommand(commandParts)) {
            return exitCode == 0 || exitCode == 1;
        }
        return exitCode == 0;
    }

    private static boolean isGrepCommand(List<String> commandParts) {
        String[] lastAtomicCommandParts = commandParts.get(commandParts.size() - 1).split("\\s*\\|\\s*");
        return GREP_COMMAND_PATTERN.matcher(lastAtomicCommandParts[lastAtomicCommandParts.length - 1]).find();
    }

    /**
     * 获取 Windows 控制台字符集（延迟初始化）
     */
    private static Charset getWindowsConsoleCharset() {
        return WINDOWS_CONSOLE_CHARSET.updateAndGet(current ->
                current != null ? current : detectWindowsConsoleCharset()
        );
    }

    /**
     * 动态检测 Windows 控制台 OEM 代码页
     * 通过执行 chcp 命令获取当前代码页编号（如 936=GBK, 437=美式, 65001=UTF-8）
     * 仅提取数字部分，不受输出语言影响
     */
    private static Charset detectWindowsConsoleCharset() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("cmd.exe", "/c", "chcp");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.US_ASCII))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            // 输出格式: "Active code page: 936" 或 "活动代码页: 936"，数字部分始终是 ASCII，可安全提取
            Matcher matcher = Pattern.compile("(\\d+)").matcher(output.toString());
            if (matcher.find()) {
                String codePageName = "cp" + matcher.group(1);
                LOGGER.info("Detected Windows console code page: {}", codePageName);
                return "cp65001".equals(codePageName) ? StandardCharsets.UTF_8 : Charset.forName(codePageName);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to detect Windows console charset, falling back to GBK: {}", e.getMessage());
        }
        return Charset.forName("GBK");
    }

    private static boolean isWindows() {
        String osName = System.getProperty("os.name").toLowerCase();
        return osName.contains("win");
    }

    /**
     * 命令执行结果
     */
    @Data
    public static class CommandResult {
        /** 按成功判定规则计算的执行是否成功 */
        private final boolean success;
        /** 进程退出码，超时被终止时为 {@link #TIMEOUT_EXIT_CODE} 语义的 -1000 */
        private final int exitCode;
        /** 标准输出全文 */
        private final String stdOutput;
        /** 标准错误全文 */
        private final String stdErrorOutput;
        /** 是否因超时被强制终止 */
        private final boolean timedOut;

        public CommandResult(boolean success, int exitCode, String stdOutput, String stdErrorOutput, boolean timedOut) {
            this.success = success;
            this.exitCode = exitCode;
            this.stdOutput = stdOutput;
            this.stdErrorOutput = stdErrorOutput;
            this.timedOut = timedOut;
        }
    }
}
