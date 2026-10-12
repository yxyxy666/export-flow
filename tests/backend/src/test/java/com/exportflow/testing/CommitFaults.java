// 规格与进度：[任务记录](../../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.testing;

import java.lang.reflect.*;
import java.sql.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/** 仅注入真实JDBC提交之后的异常；不伪造事务、唯一约束或回读结果。 */
public final class CommitFaults implements BeanPostProcessor {
  private final AtomicBoolean armed = new AtomicBoolean();
  private final AtomicInteger triggered = new AtomicInteger();
  private volatile boolean blockAfterCommit;
  private volatile boolean unavailable;
  public void arm(boolean blockReconciliation) { unavailable = false; blockAfterCommit = blockReconciliation; triggered.set(0); armed.set(true); }
  public int triggeredCount() { return triggered.get(); }
  public void restore() { unavailable = false; armed.set(false); }
  @Override public Object postProcessAfterInitialization(Object bean, String name) {
    if (!(bean instanceof DataSource source)) return bean;
    return new DelegatingDataSource(source) {
      @Override public Connection getConnection() throws SQLException { check(); return wrap(super.getConnection()); }
      @Override public Connection getConnection(String user, String password) throws SQLException { check(); return wrap(super.getConnection(user, password)); }
    };
  }
  private void check() throws SQLException { if (unavailable) throw new SQLTransientConnectionException("synthetic reconciliation unavailable"); }
  private Connection wrap(Connection original) {
    var writesTask = new AtomicBoolean();
    return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, arguments) -> {
      if (method.getName().startsWith("prepare") && arguments != null && arguments.length > 0 && arguments[0] instanceof String sql
          && sql.toLowerCase(java.util.Locale.ROOT).matches("(?s).*insert\\s+into\\s+`?export_tasks.*")) writesTask.set(true);
      Object result;
      try { result = method.invoke(original, arguments); }
      catch (InvocationTargetException failure) { throw failure.getCause(); }
      if (method.getName().equals("commit") && writesTask.get() && armed.compareAndSet(true, false)) {
        triggered.incrementAndGet();
        unavailable = blockAfterCommit;
        throw new SQLRecoverableException("synthetic lost commit acknowledgement");
      }
      return result;
    });
  }
}
