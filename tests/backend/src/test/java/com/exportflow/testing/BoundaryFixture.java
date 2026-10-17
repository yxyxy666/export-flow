// 本期实施与验证：[任务记录](../../../../../../../../specs/004-task-management/tasks.md)。
// 规格与进度：[任务记录](../../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.testing;

import static org.assertj.core.api.Assertions.*;
import static com.exportflow.testing.BoundaryProbe.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.config.BeanPostProcessor;

/** 用获批公开端口装配真实边界；反射仅用于正式类型尚未生成的测试先行阶段。 */
public abstract class BoundaryFixture extends ProductionBackendFixture {
  protected static final String OUTBOX="com.exportflow.dispatch.port.CommandOutboxRepository";
  protected static final String PUBLISHER="com.exportflow.dispatch.port.ExportCommandPublisher";
  protected static final String EXECUTION="com.exportflow.worker.port.TaskExecutionRepository";
  protected static final String GENERATOR="com.exportflow.worker.port.ExportFileGenerator";
  protected static final String STORAGE="com.exportflow.exporttasks.port.ExportFileStorage";
  protected static final String ARTIFACT="com.exportflow.exporttasks.port.ArtifactRepository";
  protected static final String CHECKPOINT="com.exportflow.worker.port.CheckpointRepository";
  protected static final String ORDERS="com.exportflow.orders.port.OrderRepository";
  protected static final String TASKS="com.exportflow.exporttasks.port.ExportTaskRepository";
  protected static final String DISPATCH="com.exportflow.dispatch.application.OutboxDispatchService";
  protected static final String TIMEOUT="com.exportflow.dispatch.application.OutboxTimeoutService";
  protected static final String EXEC_SERVICE="com.exportflow.worker.application.TaskExecutionService";
  protected final BoundaryProbe probe=new BoundaryProbe();
  @Override protected BeanPostProcessor testFaults(){return probe;}
  protected Object claim(UUID task,String owner)throws Exception{return call(EXEC_SERVICE,"claim",task,owner);}
  protected Object lease(UUID task)throws Exception{return property(claim(task,"synthetic-owner"),"lease");}
  protected Object snapshot(UUID task)throws Exception{return ((Optional<?>)port(TASKS,"findSnapshot",task)).orElseThrow();}
  protected Object context(UUID task,Object lease)throws Exception{
    Class<?> type=method(GENERATOR,"generate").getParameterTypes()[0];
    return record(type,Map.of("snapshot",snapshot(task),"lease",lease));
  }
  protected Object guard(boolean valid)throws Exception{
    Class<?> type=method(GENERATOR,"generate").getParameterTypes()[1];
    return Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->m.getName().equals("check")?valid:null);
  }
  protected Object generate(UUID task,Object lease)throws Exception{return port(GENERATOR,"generate",context(task,lease),guard(true));}
  protected Object failure(String code)throws Exception{
    Class<?> type=method(EXECUTION,"complete").getParameterTypes()[1];
    Class<?> failure=Arrays.stream(type.getRecordComponents()).filter(c->c.getName().equals("error")).findFirst().orElseThrow().getType();
    return record(type,Map.of("kind","KNOWN_FAILURE","error",record(failure,Map.of("code",code,"message","合成测试明确失败")),"resourcesClosed",false));
  }
  protected Object unknown()throws Exception{return record(method(EXECUTION,"complete").getParameterTypes()[1],Map.of("kind","UNKNOWN","resourcesClosed",false));}
  /** 领取已真实提交，截住返回值防止本轮发布；留给调用者测试崩溃接管窗口。 */
  protected Object reserve(UUID task)throws Exception{
    AtomicReference<Object> captured=new AtomicReference<>();
    probe.on(OUTBOX,"claimDue",(m,a,real)->{Object batch=real.run();
      for(Object item:(List<?>)property(batch,"leases"))if(property(item,"taskId").equals(task))captured.set(item);
      return copy(batch,Map.of("leases",List.of()));});
    try{call(DISPATCH,"scanAvailable");}finally{probe.clear();}
    assertThat(captured.get()).as("领取事务必须真实提交").isNotNull();return captured.get();
  }
  protected Object evidence()throws Exception{return record(method(OUTBOX,"markSent").getParameterTypes()[1],
      Map.of("confirmAck",true,"correctRoute",true,"policyVerified",true));}
  protected Map<String,Object> outbox(UUID task){return db.queryForMap("SELECT * FROM command_outbox WHERE task_id=?",task.toString());}
  protected void expireExecution(UUID task){db.update("UPDATE task_execution SET lease_until=UTC_TIMESTAMP()-INTERVAL 1 SECOND,next_recovery_at=UTC_TIMESTAMP()-INTERVAL 1 SECOND WHERE task_id=?",task.toString());}
  protected void noAck(com.rabbitmq.client.Channel channel)throws Exception{
    org.mockito.Mockito.verify(channel,org.mockito.Mockito.never()).basicAck(org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyBoolean());
  }
}
