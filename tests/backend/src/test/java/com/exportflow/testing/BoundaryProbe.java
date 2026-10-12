// 规格与进度：[任务记录](../../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.testing;

import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.config.BeanPostProcessor;

/** 测试端口探针：默认调用真实端口，只在显式挂钩处阻塞、注入故障或观测结果。 */
public final class BoundaryProbe implements BeanPostProcessor {
  @FunctionalInterface public interface Proceed { Object run() throws Throwable; }
  @FunctionalInterface public interface Hook { Object invoke(Method method, Object[] args, Proceed real) throws Throwable; }
  private record Key(String port, String method) {}
  private final Map<Key, Hook> hooks = new ConcurrentHashMap<>();
  private final Map<Key, AtomicInteger> calls = new ConcurrentHashMap<>();
  public void on(String port, String method, Hook hook) { hooks.put(new Key(port, method), hook); }
  public void clear() { hooks.clear(); }
  public int calls(String port, String method) { return calls.getOrDefault(new Key(port, method), new AtomicInteger()).get(); }
  @Override public Object postProcessAfterInitialization(Object bean, String name) {
    // 真实算法组件的困难故障窗口；默认仍调用原Bean，替身不实现算法。
    for(Class<?> type=bean.getClass();type!=null;type=type.getSuperclass()){
      if(Set.of("com.exportflow.worker.infrastructure.excel.ExcelWorkbookAssembler",
          "com.exportflow.worker.infrastructure.excel.ExcelFileValidator",
          "com.exportflow.worker.infrastructure.file.DataBlockCodec").contains(type.getName())){
        String className=type.getName();return org.mockito.Mockito.mock(type,invocation->{
          Method method=invocation.getMethod();Key key=new Key(className,method.getName());calls.computeIfAbsent(key,k->new AtomicInteger()).incrementAndGet();
          Proceed real=()->{try{return method.invoke(bean,invocation.getArguments());}catch(InvocationTargetException e){throw e.getCause();}};
          Hook hook=hooks.get(key);return hook==null?real.run():hook.invoke(method,invocation.getArguments(),real);
        });
      }
    }
    Set<Class<?>> interfaces = new LinkedHashSet<>();
    for (Class<?> type = bean.getClass(); type != null; type = type.getSuperclass()) collect(type, interfaces);
    if (interfaces.stream().noneMatch(i -> i.getName().startsWith("com.exportflow.") && i.getName().contains(".port."))) return bean;
    return Proxy.newProxyInstance(bean.getClass().getClassLoader(), interfaces.toArray(Class<?>[]::new), (proxy, method, args) -> {
      Key key = new Key(method.getDeclaringClass().getName(), method.getName());
      calls.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
      Proceed real = () -> { try { return method.invoke(bean, args); } catch (InvocationTargetException error) { throw error.getCause(); } };
      Hook hook = hooks.get(key); return hook == null ? real.run() : hook.invoke(method, args, real);
    });
  }
  private static void collect(Class<?> type, Set<Class<?>> out) {
    for (Class<?> i : type.getInterfaces()) if (out.add(i)) collect(i, out);
  }
  public static Object property(Object value, String name) {
    try { Method method = value.getClass().getDeclaredMethod(name); method.trySetAccessible(); return method.invoke(value); }
    catch (ReflectiveOperationException error) { throw new AssertionError("缺少获批类型属性：" + name, error); }
  }
  public static Object enumValue(Class<?> type, String value) {
    return Arrays.stream(type.getEnumConstants()).filter(v -> v.toString().equals(value)).findFirst().orElseThrow();
  }
  /** 只构造已存在的规格record，字段名必须匹配，不创建生产替代类型。 */
  public static Object record(Class<?> type, Map<String, ?> values) {
    if (!type.isRecord()) throw new AssertionError("预期规格record：" + type);
    try {
      RecordComponent[] fields = type.getRecordComponents();
      Set<String> names = new HashSet<>(); for (var f : fields) names.add(f.getName());
      if (!names.containsAll(values.keySet())) throw new AssertionError("未定义字段：" + values.keySet());
      Object[] args = new Object[fields.length]; Class<?>[] types = new Class<?>[fields.length];
      for (int i = 0; i < fields.length; i++) { types[i] = fields[i].getType(); Object v = values.get(fields[i].getName());
        if (v instanceof String s && types[i].isEnum()) v = enumValue(types[i], s);
        args[i] = v;
      }
      Constructor<?> ctor = type.getDeclaredConstructor(types); ctor.trySetAccessible(); return ctor.newInstance(args);
    } catch (InvocationTargetException error) {
      if(error.getCause() instanceof RuntimeException invalid)throw invalid;
      if(error.getCause() instanceof Error invalid)throw invalid;
      throw new AssertionError("规格类型构造失败："+type,error.getCause());
    } catch (ReflectiveOperationException error) { throw new AssertionError("规格类型构造失败：" + type, error); }
  }
  public static Object copy(Object original, Map<String, ?> changes) {
    Map<String,Object> values = new HashMap<>();
    for (var field : original.getClass().getRecordComponents()) values.put(field.getName(), property(original, field.getName()));
    values.putAll(changes); return record(original.getClass(), values);
  }
  public static Method method(String port, String name) throws ClassNotFoundException {
    return Arrays.stream(Class.forName(port).getMethods()).filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
  }
  /** 显式双向屏障，有界退出；不通过固定sleep猜测交错。 */
  public static final class Gate implements AutoCloseable {
    private final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    public void pause() throws InterruptedException { entered.countDown(); if (!release.await(30, TimeUnit.SECONDS)) throw new AssertionError("测试屏障未释放"); }
    public void await() throws InterruptedException { if (!entered.await(20, TimeUnit.SECONDS)) throw new AssertionError("生产边界未进入屏障"); }
    public void close() { release.countDown(); }
  }
}
