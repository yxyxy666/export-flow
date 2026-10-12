// 规格与进度：[任务记录](../../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
// 设计：[计划](../../../../../../../../specs/002-order-management/plan.md)；验证：[环境检查](TestEnvironmentTest.java)、[真实装配](ProductionBackendFixture.java)。
package com.exportflow.testing;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** 仅复用负责人提供的服务；每用例创建并回收自己的schema、账号和vhost。 */
public final class SharedServices {
  private SharedServices() {}

  public static Mysql mysql() {
    return new Mysql();
  }

  public static Rabbit rabbit() {
    return new Rabbit();
  }

  private static String id() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  private static Result docker(String container, String... command) throws Exception {
    var args = new ArrayList<>(List.of("docker", "exec", container));
    args.addAll(List.of(command));
    var process = new ProcessBuilder(args).redirectErrorStream(true).start();
    // Docker输出有界；凭据只在内存中读取，不记录命令或原文。
    var output = new ByteArrayOutputStream();
    var reader =
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    process.getInputStream().transferTo(output);
                  } catch (IOException ignored) {
                  }
                });
    if (!process.waitFor(30, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      reader.join(1000);
      throw new IOException("共享测试资源操作超时");
    }
    reader.join();
    return new Result(process.exitValue(), output.toString(StandardCharsets.UTF_8));
  }

  private static void require(Result result) throws IOException {
    if (result.getExitCode() != 0) throw new IOException("共享测试资源操作失败（原始输出不记录）");
  }

  public record Result(int getExitCode, String getStdout) {}

  public static final class Mysql implements AutoCloseable {
    private final String suffix = id();
    private final String schema = "export_flow_test_" + suffix;
    private final String username = "eft_" + suffix.substring(0, 24);
    private final String password = id();
    private final String restricted = "efr_" + suffix.substring(0, 24);
    private String rootPassword;
    private boolean owned, userOwned, restrictedOwned;

    public synchronized void start() throws Exception {
      var result = docker("project-export-flow-mysql-1", "printenv", "MYSQL_ROOT_PASSWORD");
      require(result);
      rootPassword = result.getStdout().strip();
      try (var connection = getAdminConnection();
          var sql = connection.createStatement()) {
        sql.execute(
            "CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_ci");
        owned = true;
        sql.execute("CREATE USER '" + username + "'@'%' IDENTIFIED BY '" + password + "'");
        userOwned = true;
        sql.execute("GRANT ALL ON `" + schema + "`.* TO '" + username + "'@'%'");
        // 原订单只读复用；不写入、复制或迁移负责人提供的业务表。
        sql.execute("GRANT SELECT,REFERENCES ON `exportflow`.`orders` TO '" + username + "'@'%'");
      } catch (Exception failure) {
        try {
          close();
        } catch (Exception cleanup) {
          failure.addSuppressed(cleanup);
        }
        throw failure;
      }
    }

    public String getDatabaseName() {
      return schema;
    }

    public String getUsername() {
      return username;
    }

    public String getRestrictedUsername() {
      return restricted;
    }

    public String getPassword() {
      return password;
    }

    public synchronized void createRestrictedUser(String restrictedPassword) throws SQLException {
      if (!restrictedPassword.matches("[A-Za-z0-9-]{1,64}"))
        throw new IllegalArgumentException("测试凭据格式无效");
      try (var connection = getAdminConnection();
          var sql = connection.createStatement()) {
        sql.execute(
            "CREATE USER '" + restricted + "'@'%' IDENTIFIED BY '" + restrictedPassword + "'");
        restrictedOwned = true;
        sql.execute("GRANT SELECT ON `exportflow`.`orders` TO '" + restricted + "'@'%'");
      }
    }

    public String getJdbcUrl() {
      return url(schema);
    }

    private String url(String database) {
      return "jdbc:mysql://127.0.0.1:3306/"
          + database
          + "?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8&rewriteBatchedStatements=true";
    }

    public Connection getAdminConnection() throws SQLException {
      return DriverManager.getConnection(url(owned ? schema : "mysql"), "root", rootPassword);
    }

    public String getDockerImageName() {
      return "mysql:8.4 (existing project-export-flow-mysql-1)";
    }

    public void stop() throws Exception {
      close();
    }

    @Override
    public synchronized void close() throws Exception {
      if (!owned && !userOwned && !restrictedOwned) return;
      try (var connection = DriverManager.getConnection(url("mysql"), "root", rootPassword);
          var sql = connection.createStatement()) {
        SQLException failure = null;
        try {
          if (owned) {
            sql.execute("DROP DATABASE IF EXISTS `" + schema + "`");
            owned = false;
          }
        } catch (SQLException cleanup) {
          failure = cleanup;
        }
        try {
          if (userOwned) {
            sql.execute("DROP USER IF EXISTS '" + username + "'@'%'");
            userOwned = false;
          }
        } catch (SQLException cleanup) {
          if (failure == null) failure = cleanup;
          else failure.addSuppressed(cleanup);
        }
        try {
          if (restrictedOwned) {
            sql.execute("DROP USER IF EXISTS '" + restricted + "'@'%'");
            restrictedOwned = false;
          }
        } catch (SQLException cleanup) {
          if (failure == null) failure = cleanup;
          else failure.addSuppressed(cleanup);
        }
        if (failure != null) throw failure;
      }
    }
  }

  public static final class Rabbit implements AutoCloseable {
    private final String suffix = id();
    private final String vhost = "/export-flow-test-" + suffix;
    private final String username = "eft_" + suffix;
    private final String password = id();
    private boolean vhostOwned, userOwned;
    private TcpBridge bridge;

    public synchronized void start() throws Exception {
      try {
        require(control("add_vhost", vhost));
        vhostOwned = true;
        require(control("add_user", username, password));
        userOwned = true;
        require(control("set_user_tags", username, "administrator"));
        require(control("set_permissions", "-p", vhost, username, ".*", ".*", ".*"));
        bridge = new TcpBridge();
      } catch (Exception failure) {
        try {
          close();
        } catch (Exception cleanup) {
          failure.addSuppressed(cleanup);
        }
        throw failure;
      }
    }

    private Result control(String... command) throws Exception {
      var args = new ArrayList<>(List.of("rabbitmqctl"));
      args.addAll(List.of(command));
      return docker("project-export-flow-rabbitmq-1", args.toArray(String[]::new));
    }

    /** 仅允许测试vhost策略、本次账号标签和只读能力查询。 */
    public Result execInContainer(String... args) throws Exception {
      var values = List.of(args);
      boolean policy =
          args.length > 4
              && args[0].equals("rabbitmqctl")
              && Set.of(
                      "set_policy", "clear_policy", "set_operator_policy", "clear_operator_policy")
                  .contains(args[1])
              && args[2].equals("--vhost")
              && args[3].equals(vhost);
      boolean tags =
          args.length >= 3
              && args[0].equals("rabbitmqctl")
              && args[1].equals("set_user_tags")
              && args[2].equals(username);
      boolean read =
          values.equals(
              List.of("rabbitmqctl", "eval", "application:get_env(rabbit, consumer_timeout)."));
      if (!policy && !tags && !read) throw new IllegalArgumentException("禁止修改共享broker或其他资源");
      if (!policy) return docker("project-export-flow-rabbitmq-1", args);
      // Windows原生命令行会重解释JSON双引号；策略走真实管理HTTP，避免CLI转义损坏。
      String endpoint = args[1].contains("operator") ? "operator-policies" : "policies";
      var url =
          URI.create(
              "http://127.0.0.1:15672/api/"
                  + endpoint
                  + "/"
                  + URLEncoder.encode(vhost, StandardCharsets.UTF_8)
                  + "/"
                  + URLEncoder.encode(args[4], StandardCharsets.UTF_8));
      var request =
          java.net.http.HttpRequest.newBuilder(url)
              .timeout(java.time.Duration.ofSeconds(5))
              .header(
                  "Authorization",
                  "Basic "
                      + Base64.getEncoder()
                          .encodeToString(
                              (username + ":" + password).getBytes(StandardCharsets.UTF_8)))
              .header("Content-Type", "application/json");
      if (args[1].startsWith("clear_")) request.DELETE();
      else {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var body =
            json.writeValueAsString(
                Map.of(
                    "pattern",
                    args[5],
                    "definition",
                    json.readTree(args[6]),
                    "priority",
                    0,
                    "apply-to",
                    "queues"));
        request.PUT(java.net.http.HttpRequest.BodyPublishers.ofString(body));
      }
      try (var client =
          java.net.http.HttpClient.newBuilder()
              .version(java.net.http.HttpClient.Version.HTTP_1_1)
              .build()) {
        var response =
            client.send(request.build(), java.net.http.HttpResponse.BodyHandlers.discarding());
        return new Result(
            response.statusCode() >= 200 && response.statusCode() < 300 ? 0 : response.statusCode(),
            "policy HTTP status " + response.statusCode());
      }
    }

    public String getVirtualHost() {
      return vhost;
    }

    public String getHost() {
      return "127.0.0.1";
    }

    public int getMappedPort(int port) {
      return port == 5672 ? bridge.port() : port;
    }

    public String getAmqpUrl() {
      return "amqp://" + username + ":" + password + "@127.0.0.1:" + bridge.port();
    }

    public String getDockerImageName() {
      return "rabbitmq:3.13-management-alpine (existing project-export-flow-rabbitmq-1)";
    }

    public void suspendConnections() {
      bridge.setEnabled(false);
    }

    public void resumeConnections() {
      bridge.setEnabled(true);
    }

    public void stop() throws Exception {
      close();
    }

    @Override
    public synchronized void close() throws Exception {
      Exception failure = null;
      try {
        if (bridge != null) bridge.close();
      } catch (Exception cleanup) {
        failure = cleanup;
      }
      try {
        if (vhostOwned) {
          require(control("delete_vhost", vhost));
          vhostOwned = false;
        }
      } catch (Exception cleanup) {
        if (failure == null) failure = cleanup;
        else failure.addSuppressed(cleanup);
      }
      try {
        if (userOwned) {
          require(control("delete_user", username));
          userOwned = false;
        }
      } catch (Exception cleanup) {
        if (failure == null) failure = cleanup;
        else failure.addSuppressed(cleanup);
      }
      if (failure != null) throw failure;
    }
  }

  /** 真实AMQP字节转发；故障只关闭本测试的socket，不停止共享服务。 */
  private static final class TcpBridge implements AutoCloseable {
    private final ServerSocket listener = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
    private final ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<Socket> sockets = new HashSet<>();
    private boolean enabled = true;

    TcpBridge() throws IOException {
      threads.submit(
          () -> {
            while (!listener.isClosed()) {
              try {
                var local = listener.accept();
                threads.submit(() -> connect(local));
              } catch (IOException closed) {
                break;
              }
            }
          });
    }

    int port() {
      return listener.getLocalPort();
    }

    private void connect(Socket local) {
      var remote = new Socket();
      synchronized (this) {
        if (!enabled || listener.isClosed()) {
          closeSocket(local);
          closeSocket(remote);
          return;
        }
        sockets.add(local);
        sockets.add(remote);
      }
      try {
        remote.connect(new InetSocketAddress("127.0.0.1", 5672), 3000);
        threads.submit(() -> pump(remote, local));
        pump(local, remote);
      } catch (IOException unavailable) {
        release(local, remote);
      }
    }

    private void pump(Socket from, Socket to) {
      try {
        from.getInputStream().transferTo(to.getOutputStream());
      } catch (IOException disconnected) {
      } finally {
        release(from, to);
      }
    }

    private synchronized void release(Socket first, Socket second) {
      closeSocket(first);
      closeSocket(second);
      sockets.remove(first);
      sockets.remove(second);
    }

    synchronized void setEnabled(boolean value) {
      enabled = value;
      if (!enabled) {
        sockets.forEach(TcpBridge::closeSocket);
        sockets.clear();
      }
    }

    private static void closeSocket(Socket socket) {
      try {
        socket.close();
      } catch (IOException ignored) {
      }
    }

    @Override
    public void close() throws IOException {
      setEnabled(false);
      listener.close();
      threads.shutdownNow();
    }
  }
}
