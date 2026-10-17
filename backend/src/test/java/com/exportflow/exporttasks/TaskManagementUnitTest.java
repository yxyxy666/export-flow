// [规格与进度](../../../../../../../specs/004-task-management/tasks.md#测试进度与证据)
package com.exportflow.exporttasks;

import static com.exportflow.testing.BoundaryProbe.*;
import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

// [AC-086](../../../../../../../docs/product/acceptance-criteria.md#ac-086)
class TaskManagementUnitTest {
  private Object query(MultiValueMap<String, String> values) throws Throwable {
    var type = Class.forName("com.exportflow.exporttasks.api.TaskManagementApiMapper");
    var mapper = type.getDeclaredConstructor().newInstance();
    try {
      return type.getMethod("toQuery", MultiValueMap.class).invoke(mapper, values);
    } catch (InvocationTargetException failure) {
      throw failure.getCause();
    }
  }

  @Test
  void TC407_unicodeCodePointBoundaryAndTrimAreAppliedWithoutChangingLiteralSearch()
      throws Throwable {
    var input = new LinkedMultiValueMap<String, String>();
    input.add("keyword", "  " + "😀".repeat(100) + "  ");
    var query = query(input);
    assertThat(property(query, "keyword")).isEqualTo("😀".repeat(100));
    input.set("keyword", "😀".repeat(101));
    assertThatThrownBy(() -> query(input)).isInstanceOf(RuntimeException.class);
  }

  @Test
  void TC407_queryRejectsRepeatedAndUnknownFieldsBeforeParsing() throws Throwable {
    var duplicate = new LinkedMultiValueMap<String, String>();
    duplicate.add("page", "1");
    duplicate.add("page", "2");
    assertThatThrownBy(() -> query(duplicate)).isInstanceOf(RuntimeException.class);
    var unknown = new LinkedMultiValueMap<String, String>();
    unknown.add("sortBy", "progress");
    assertThatThrownBy(() -> query(unknown)).isInstanceOf(RuntimeException.class);
  }
}
