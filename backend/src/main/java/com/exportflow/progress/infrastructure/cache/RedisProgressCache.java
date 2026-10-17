// 设计：[版本缓存](../../../../../../../../../specs/004-task-management/contracts/execution-control.md)。
package com.exportflow.progress.infrastructure.cache;

import com.exportflow.config.RuntimeProperties;
import com.exportflow.exporttasks.api.TaskManagementApiMapper;
import com.exportflow.exporttasks.domain.TaskManagementModels.TaskListItem;
import com.exportflow.progress.port.ProgressCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisProgressCache implements ProgressCache {
  // Lua不把BIGINT转double：先比较十进制字符串长度，再按字典序比较。
  private static final DefaultRedisScript<Long> WRITE =
      new DefaultRedisScript<>(
          """
      local old=redis.call('GET',KEYS[1])
      if old then
        local ok,value=pcall(cjson.decode,old)
        if ok and type(value.version)=='string' then
          local v=value.version
          if string.match(v,'^%d+$') and (#v>#ARGV[1] or (#v==#ARGV[1] and v>=ARGV[1])) then return 0 end
        end
      end
      local now=tonumber(ARGV[3])
      redis.call('ZREMRANGEBYSCORE',KEYS[2],'-inf',now)
      if not old and redis.call('ZCARD',KEYS[2])>=10000 then return -1 end
      redis.call('SET',KEYS[1],ARGV[2],'EX',600)
      redis.call('ZADD',KEYS[2],now+600000,KEYS[1])
      redis.call('PEXPIRE',KEYS[2],600000)
      return 1
      """,
          Long.class);
  private final StringRedisTemplate redis;
  private final ObjectMapper json;
  private final TaskManagementApiMapper mapper;
  private final String prefix;

  public RedisProgressCache(
      StringRedisTemplate redis,
      ObjectMapper json,
      TaskManagementApiMapper mapper,
      RuntimeProperties properties) {
    this.redis = redis;
    this.json = json;
    this.mapper = mapper;
    prefix = properties.getProgressCachePrefix();
  }

  /** 缓存未命中、损坏或不可用均回源；禁止把缓存故障传回业务事务。 */
  public Optional<TaskListItem> get(UUID id, long minimumVersion) {
    try {
      var raw = redis.opsForValue().get(prefix + id);
      if (raw == null) return Optional.empty();
      var entry = json.readTree(raw);
      var version = entry.path("version").asText();
      if (!version.matches("0|[1-9][0-9]*") || Long.parseLong(version) < minimumVersion)
        return Optional.empty();
      // 领域副本用于内部读取，协议副本保持HTTP/SSE一致；二者版本必须吻合。
      var task = json.treeToValue(entry.path("domain"), TaskListItem.class);
      if (!id.equals(task.taskId()) || task.version() != Long.parseLong(version))
        return Optional.empty();
      return Optional.of(task);
    } catch (RuntimeException | java.io.IOException unavailable) {
      return Optional.empty();
    }
  }

  public void putIfNewer(TaskListItem task) {
    try {
      var body =
          json.writeValueAsString(
              Map.of(
                  "version",
                  Long.toString(task.version()),
                  "task",
                  mapper.toDto(task),
                  "domain",
                  task));
      redis.execute(
          WRITE,
          List.of(prefix + task.taskId(), prefix + "index"),
          Long.toString(task.version()),
          body,
          Long.toString(System.currentTimeMillis()));
    } catch (RuntimeException | java.io.IOException unavailable) {
      /* 权威数据库及待发通知不受缓存失败影响。 */
    }
  }
}
