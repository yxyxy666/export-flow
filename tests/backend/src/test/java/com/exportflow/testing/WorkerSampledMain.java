// 规格与进度：[任务记录](../../../../../../../../specs/002-order-management/tasks.md#测试进度与证据)
package com.exportflow.testing;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
/** 只采样独立真实Worker进程，不提供任何生成、状态或数据库替身。 */
public final class WorkerSampledMain{
  private WorkerSampledMain(){}
  public static void main(String[] args)throws Exception{
    String destination=Arrays.stream(args).filter(a->a.startsWith("--test-heap-report=")).findFirst().orElseThrow().substring("--test-heap-report=".length());
    Path report=Path.of(destination),temporary=report.resolveSibling(report.getFileName()+".part");
    var peak=new AtomicLong();var samples=new AtomicLong();var json=new com.fasterxml.jackson.databind.ObjectMapper();
    var sampler=Executors.newSingleThreadScheduledExecutor(r->{var thread=new Thread(r,"test-heap-sampler");thread.setDaemon(true);return thread;});
    sampler.scheduleAtFixedRate(()->{
      try{long used=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();peak.accumulateAndGet(used,Math::max);samples.incrementAndGet();
        json.writeValue(temporary.toFile(),Map.of("pid",ProcessHandle.current().pid(),"maxHeapBytes",Runtime.getRuntime().maxMemory(),"peakSampleBytes",peak.get(),"sampleCount",samples.get(),"sampleIntervalMs",50,"java",System.getProperty("java.runtime.version")));
        Files.move(temporary,report,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
      }catch(Exception failure){throw new IllegalStateException("独立Worker采样失败",failure);}
    },0,50,TimeUnit.MILLISECONDS);
    Runtime.getRuntime().addShutdownHook(new Thread(sampler::shutdownNow,"test-sampler-stop"));
    new org.springframework.boot.builder.SpringApplicationBuilder(Class.forName("com.exportflow.ExportFlowApplication")).run(args);
  }
}
