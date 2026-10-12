// 规格与进度：[任务记录](../../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.testing;

import static org.assertj.core.api.Assertions.*;

import com.rabbitmq.client.*;
import java.nio.file.*;
import java.sql.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 仅证明测试工具、独立服务及文件能力；不能当作生产适配器集成证据。 */
class TestEnvironmentTest {
  @TempDir Path directory;

  @Test
  void isolatedMysqlSupportsUtf8UtcTransactionsAndSkipLocked() throws Exception {
    try (var mysql = SharedServices.mysql()) {
      mysql.start();
      try (var first =
              DriverManager.getConnection(
                  mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
          var second =
              DriverManager.getConnection(
                  mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
        first
            .createStatement()
            .execute(
                "CREATE TABLE test_lock(id BIGINT PRIMARY KEY, value VARCHAR(20)) ENGINE=InnoDB");
        first.createStatement().execute("INSERT INTO test_lock VALUES(1,'合成😀'),(2,'test')");
        first.setTransactionIsolation(java.sql.Connection.TRANSACTION_READ_COMMITTED);
        second.setTransactionIsolation(java.sql.Connection.TRANSACTION_READ_COMMITTED);
        first.setAutoCommit(false);
        second.setAutoCommit(false);
        var locked =
            first.createStatement().executeQuery("SELECT id FROM test_lock WHERE id=1 FOR UPDATE");
        assertThat(locked.next()).isTrue();
        var skipped =
            second
                .createStatement()
                .executeQuery("SELECT id FROM test_lock ORDER BY id FOR UPDATE SKIP LOCKED");
        assertThat(skipped.next()).isTrue();
        assertThat(skipped.getLong(1)).isEqualTo(2);
        assertThat(skipped.next()).isFalse();
        first.rollback();
        second.rollback();
        var version = first.createStatement().executeQuery("SELECT VERSION(), @@session.time_zone");
        assertThat(version.next()).isTrue();
        assertThat(version.getString(1)).startsWith("8.4.");
        assertThat(version.getString(2)).isEqualTo("+00:00");
      }
    }
  }

  @Test
  void isolatedRabbitConfirmsAndRedeliversUnacknowledgedPersistentMessage() throws Exception {
    try (var rabbit = SharedServices.rabbit()) {
      rabbit.start();
      var factory = new ConnectionFactory();
      factory.setUri(rabbit.getAmqpUrl());
      factory.setVirtualHost(rabbit.getVirtualHost());
      try (var connection = factory.newConnection();
          var publisher = connection.createChannel()) {
        publisher.queueDeclare("harness-retention", true, false, false, null);
        publisher.confirmSelect();
        publisher.basicPublish(
            "",
            "harness-retention",
            true,
            MessageProperties.PERSISTENT_BASIC,
            "synthetic-command".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        publisher.waitForConfirmsOrDie(5000);
        var consumer = connection.createChannel();
        var first = consumer.basicGet("harness-retention", false);
        assertThat(first).isNotNull();
        consumer.close();
        // 通过协议响应同步关闭及重交付，不用固定sleep。
        org.awaitility.Awaitility.await()
            .atMost(java.time.Duration.ofSeconds(5))
            .untilAsserted(
                () -> {
                  var repeated = publisher.basicGet("harness-retention", false);
                  assertThat(repeated).isNotNull();
                  assertThat(repeated.getEnvelope().isRedeliver()).isTrue();
                  publisher.basicAck(repeated.getEnvelope().getDeliveryTag(), false);
                });
      }
    }
  }

  @Test
  void localFilesystemSupportsAtomicMoveAndClosedFileRead() throws Exception {
    var staging = directory.resolve("test.xlsx.part");
    var target = directory.resolve("test.xlsx");
    Files.writeString(staging, "synthetic-test-data");
    Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
    assertThat(staging).doesNotExist();
    assertThat(Files.readString(target)).isEqualTo("synthetic-test-data");
  }

  @Test
  void javaHttpClientCanReadRealLoopbackResponse() throws Exception {
    var server =
        com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/environment",
        exchange -> {
          byte[] body = "synthetic-loopback".getBytes(java.nio.charset.StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          try (var output = exchange.getResponseBody()) {
            output.write(body);
          }
        });
    server.start();
    try (var client = java.net.http.HttpClient.newHttpClient()) {
      var request =
          java.net.http.HttpRequest.newBuilder(
                  java.net.URI.create(
                      "http://127.0.0.1:" + server.getAddress().getPort() + "/environment"))
              .GET()
              .build();
      var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).isEqualTo("synthetic-loopback");
    } finally {
      server.stop(0);
    }
  }
}
