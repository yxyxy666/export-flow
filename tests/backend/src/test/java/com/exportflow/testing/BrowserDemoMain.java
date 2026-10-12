// 规格与进度：[手工验证](../../../../../../../../docs/validation/002-order-management/browser-checklist.md)
package com.exportflow.testing;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/** 手工验证专用入口：共享服务内独立schema/vhost和生产应用，默认随机端口。 */
public final class BrowserDemoMain extends ProductionBackendFixture {
  private int port;

  @Override
  protected boolean consumerEnabled() {
    return true;
  }

  @Override
  protected boolean schedulingEnabled() {
    return true;
  }

  @Override
  protected int serverPort() {
    return port;
  }

  public static void main(String[] args) throws Exception {
    var demo = new BrowserDemoMain();
    demo.port = args.length == 0 ? 0 : Integer.parseInt(args[0]);
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    demo.closeProduction();
                  } catch (Exception failure) {
                    System.err.println("演示测试资源回收失败");
                  }
                }));
    try {
      demo.startProduction();
      demo.useOrderVolume(110000);
      System.out.println("BROWSER_DEMO_READY base=" + demo.base + " rows=110000 root=" + demo.root);
      new CountDownLatch(1).await();
    } finally {
      demo.closeProduction();
    }
  }
}
