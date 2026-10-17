// 测试专用：[函数设计](../../../../../../../../specs/004-task-management/function-design.md)、[证据](../../../../../../../../specs/004-task-management/tasks.md)。
package com.exportflow.testing;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 直连现有Redis，所有写入都局限于本夹具登记键；不启动容器或执行FLUSH。 */
public final class ProgressRedisFixture implements AutoCloseable {
  public record RedisTestSettings(String host, int port, String username, String password, String prefix) {}
  private final Socket socket;
  private final InputStream input;
  private final OutputStream output;
  private final String prefix;
  private final Set<String> keys = new LinkedHashSet<>();

  private ProgressRedisFixture(RedisTestSettings settings) throws IOException {
    if (!settings.prefix().matches("export-flow:test:[a-f0-9-]{36}:"))
      throw new IllegalArgumentException("缓存夹具必须使用本次隔离前缀");
    prefix = settings.prefix(); socket = new Socket(settings.host(), settings.port());
    keys.add(prefix + "index");
    socket.setSoTimeout(5000); input = socket.getInputStream(); output = socket.getOutputStream();
    try {
      if (settings.password() != null && !settings.password().isBlank()) {
        if (settings.username() == null || settings.username().isBlank()) request("AUTH", settings.password());
        else request("AUTH", settings.username(), settings.password());
      }
      if (!"PONG".equals(request("PING"))) throw new IOException("Redis连接检查失败");
    } catch (IOException failure) { socket.close(); throw failure; }
  }

  public static ProgressRedisFixture connect(RedisTestSettings settings) throws IOException {
    return new ProgressRedisFixture(settings);
  }
  public void seed(UUID taskId, String json) throws IOException {
    String key = prefix + taskId; keys.add(key); request("SET", key, json);
  }
  public String get(UUID taskId) throws IOException {
    String key = prefix + taskId; keys.add(key); return (String) request("GET", key);
  }
  private synchronized Object request(String... args) throws IOException {
    output.write(("*" + args.length + "\r\n").getBytes(StandardCharsets.UTF_8));
    for (String arg : args) {
      byte[] bytes = arg.getBytes(StandardCharsets.UTF_8);
      output.write(("$" + bytes.length + "\r\n").getBytes(StandardCharsets.UTF_8));
      output.write(bytes); output.write('\r'); output.write('\n');
    }
    output.flush(); int kind = input.read(); String line = line();
    if (kind == '+') return line;
    if (kind == ':') return Long.parseLong(line);
    if (kind == '-') throw new IOException("Redis命令失败（凭据与响应内容不输出）");
    if (kind != '$') throw new IOException("Redis响应格式错误");
    int length = Integer.parseInt(line); if (length == -1) return null;
    if (length < 0 || length > 1048576) throw new IOException("Redis响应长度无效");
    byte[] bytes = input.readNBytes(length);
    if (bytes.length != length || input.read() != '\r' || input.read() != '\n') throw new EOFException();
    return new String(bytes, StandardCharsets.UTF_8);
  }
  private String line() throws IOException {
    var bytes = new ByteArrayOutputStream();
    for (int current; (current = input.read()) != '\r';) {
      if (current == -1 || bytes.size() > 1048576) throw new EOFException(); bytes.write(current);
    }
    if (input.read() != '\n') throw new IOException("Redis行结束无效");
    return bytes.toString(StandardCharsets.UTF_8);
  }
  @Override public void close() throws IOException {
    try { for (String key : keys) request("DEL", key); }
    finally { socket.close(); }
  }
}
