// 规格与进度：[任务记录](../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.dispatch;

import static org.assertj.core.api.Assertions.*;

import com.exportflow.testing.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.config.BeanPostProcessor;

// [AC-053](../../../../../../../docs/product/acceptance-criteria.md#ac-053)
// [AC-056](../../../../../../../docs/product/acceptance-criteria.md#ac-056)
class PublisherCorrelationTest extends BoundaryFixture {
  private final AtomicReference<String> scenario = new AtomicReference<>();
  private final AtomicInteger callbacks = new AtomicInteger();

  @Override
  protected BeanPostProcessor testFaults() {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(Object bean, String name) {
        if (bean instanceof RabbitTemplate template) {
          var spy = org.mockito.Mockito.spy(template);
          org.mockito.Mockito.doAnswer(
                  invocation -> {
                    if (scenario.get() == null) return invocation.callRealMethod();
                    Message message = invocation.getArgument(2);
                    CorrelationData correlation = invocation.getArgument(3);
                    callbacks.incrementAndGet();
                    if (scenario.get().equals("disconnect"))
                      throw new org.springframework.amqp.AmqpConnectException(
                          new java.io.IOException("synthetic connection lost"));
                    assertThat(message.getMessageProperties().getDeliveryMode())
                        .isEqualTo(MessageDeliveryMode.PERSISTENT);
                    if (scenario.get().equals("return-after-ack"))
                      correlation
                          .getFuture()
                          .complete(
                              new CorrelationData.Confirm(
                                  true, "synthetic ack-before-return-observation"));
                    if (scenario.get().startsWith("return"))
                      correlation.setReturned(
                          new ReturnedMessage(
                              message,
                              312,
                              "NO_ROUTE",
                              "export-flow.commands",
                              "export.requested.v1"));
                    if (!scenario.get().equals("timeout"))
                      correlation
                          .getFuture()
                          .complete(
                              new CorrelationData.Confirm(
                                  !scenario.get().equals("nack"), "synthetic controlled confirm"));
                    return null;
                  })
              .when(spy)
              .send(
                  org.mockito.ArgumentMatchers.anyString(),
                  org.mockito.ArgumentMatchers.anyString(),
                  org.mockito.ArgumentMatchers.any(Message.class),
                  org.mockito.ArgumentMatchers.any(CorrelationData.class));
          return spy;
        }
        return probe.postProcessAfterInitialization(bean, name);
      }
    };
  }

  @ParameterizedTest
  @ValueSource(strings = {"return", "return-after-ack", "nack", "timeout", "disconnect"})
  void TC220_confirmAndReturnUseSameSpringCorrelationAndNeverMarkSentOnIncompleteProof(String mode)
      throws Exception {
    var task = create();
    var lease = reserve(task);
    scenario.set(mode);
    Class<?> envelopeType = BoundaryProbe.method(PUBLISHER, "publish").getParameterTypes()[0];
    var envelope =
        BoundaryProbe.record(
            envelopeType,
            Map.of(
                "eventId",
                BoundaryProbe.property(lease, "eventId"),
                "taskId",
                task,
                "type",
                "EXPORT_REQUESTED",
                "schemaVersion",
                1,
                "traceId",
                "synthetic-correlation"));
    // 包含真实管理HTTP核验，使用获批总预算；50ms会在进入相关confirm探针前到期。
    Object result = port(PUBLISHER, "publish", envelope, lease, java.time.Duration.ofSeconds(5));
    assertThat(callbacks).hasValue(1);
    assertThat(BoundaryProbe.property(result, "kind").toString()).isNotEqualTo("CONFIRMED");
    assertThat(outbox(task).get("status")).isNotEqualTo("SENT");
  }
}
