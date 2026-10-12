// 规格与进度：[任务记录](../../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.testing;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** 子进程在真实调用前或真实提交后停住，父进程kill验证持久化窗口。 */
public final class WorkerFaultBarrierMain {
  private WorkerFaultBarrierMain() {}
  private static String option(String[] args,String key){return Arrays.stream(args).filter(a->a.startsWith(key+"=")).findFirst().orElseThrow().substring(key.length()+1);}
  public static void main(String[] args)throws Exception{
    Path marker=Path.of(option(args,"--test-barrier-root")).resolve(option(args,"--test-barrier-marker"));
    Class<?> target=Class.forName(option(args,"--test-barrier-port"));String name=option(args,"--test-barrier-method"),phase=option(args,"--test-barrier-phase");
    var stopped=new AtomicBoolean();var occurrences=new java.util.concurrent.atomic.AtomicInteger();
    int required=Arrays.stream(args).filter(a->a.startsWith("--test-barrier-occurrence=")).mapToInt(a->Integer.parseInt(a.substring(a.indexOf('=')+1))).findFirst().orElse(1);
    BeanPostProcessor barrier=new BeanPostProcessor(){
      @Override public Object postProcessAfterInitialization(Object bean,String beanName){
        if(!target.isInstance(bean))return bean;
        java.lang.reflect.InvocationHandler handler=(proxy,method,arguments)->{
          boolean watched=method.getName().equals(name);if(watched&&phase.equals("before"))pause(null);
          Object result;try{result=method.invoke(bean,arguments);}catch(InvocationTargetException failure){throw failure.getCause();}
          if(watched&&phase.equals("after"))pause(result);return result;
        };
        if(target.isInterface())return Proxy.newProxyInstance(target.getClassLoader(),new Class<?>[]{target},handler);
        return org.mockito.Mockito.mock(target,invocation->handler.invoke(null,invocation.getMethod(),invocation.getArguments()));
      }
      private void pause(Object result)throws Exception{
        if(name.equals("commitBlock")&&phase.equals("after")&&!BoundaryProbe.property(result,"kind").toString().equals("APPLIED"))return;
        if((name.equals("registerReady")||name.equals("complete"))&&phase.equals("after")&&!result.toString().equals("APPLIED"))return;
        if(occurrences.incrementAndGet()==required&&stopped.compareAndSet(false,true)){
          Files.writeString(marker,"真实边界已到达："+target.getName()+"."+name+" "+phase,StandardOpenOption.CREATE_NEW);
          new CountDownLatch(1).await();
        }
      }
    };
    new SpringApplicationBuilder(Class.forName("com.exportflow.ExportFlowApplication"))
      .initializers(c->c.getBeanFactory().addBeanPostProcessor(barrier)).run(args);
  }
}
