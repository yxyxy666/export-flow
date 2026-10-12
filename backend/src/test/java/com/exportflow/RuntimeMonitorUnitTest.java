// 规格：[运行观察](../../../../../../specs/002-order-management/decision-record.md)
// [AC-049](../../../../../../docs/product/acceptance-criteria.md#ac-049)
// [AC-052](../../../../../../docs/product/acceptance-criteria.md#ac-052)
package com.exportflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.exportflow.config.RuntimeHealthMapper;
import com.exportflow.config.RuntimeMonitor;
import com.exportflow.dispatch.infrastructure.messaging.RabbitManagementClient;
import com.exportflow.worker.application.OrphanCleanupService;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class RuntimeMonitorUnitTest {
  @Test
  void TC213_alertsAreRateLimitedRecoverImmediatelyAndNeverChangeFacts() throws Exception {
    var database = mock(RuntimeHealthMapper.class);
    var broker = mock(RabbitManagementClient.class);
    var cleanup = mock(OrphanCleanupService.class);
    var monitor = new RuntimeMonitor(database, broker, cleanup);
    var logger = (Logger) LoggerFactory.getLogger(RuntimeMonitor.class);
    var output = new ListAppender<ILoggingEvent>();
    output.start();
    logger.addAppender(output);
    try {
      when(database.counts()).thenReturn(new RuntimeHealthMapper.Counts(1, 1, 1));
      when(broker.snapshot())
          .thenReturn(new RabbitManagementClient.BrokerSnapshot(1001, 1, 0, true));
      monitor.sample();
      monitor.sample();
      var warnings = output.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
      assertThat(warnings)
          .hasSize(6)
          .allMatch(message -> message.startsWith("runtime alert code="));
      assertThat(warnings)
          .anyMatch(message -> message.contains("SENT_PENDING"))
          .anyMatch(message -> message.contains("UNACKED_ATTEMPT_OVERDUE"));
      when(database.counts()).thenReturn(new RuntimeHealthMapper.Counts(0, 0, 0));
      when(broker.snapshot()).thenReturn(new RabbitManagementClient.BrokerSnapshot(0, 0, 1, false));
      monitor.sample();
      assertThat(
              output.list.stream()
                  .map(ILoggingEvent::getFormattedMessage)
                  .filter(message -> message.startsWith("runtime recovered code="))
                  .toList())
          .hasSize(6);
      when(database.counts())
          .thenThrow(new IllegalStateException("synthetic-private-database-detail"));
      when(broker.snapshot()).thenThrow(new java.io.IOException("synthetic-private-broker-detail"));
      monitor.sample();
      assertThat(output.list.stream().map(ILoggingEvent::getFormattedMessage).toList())
          .noneMatch(message -> message.contains("synthetic-private"));
      verifyNoInteractions(cleanup);
    } finally {
      monitor.stop();
      logger.detachAppender(output);
      output.stop();
    }
  }
}
