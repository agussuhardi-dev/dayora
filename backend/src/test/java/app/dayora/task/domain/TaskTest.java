package app.dayora.task.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskTest {
  @Test void rejectsBlankTitle() { assertThrows(IllegalArgumentException.class, () -> new Task(UUID.randomUUID(), UUID.randomUUID(), " ", null, Priority.NONE, null, null, 1, null, null, null)); }
  @Test void defaultsPriorityOnCreate() { assertEquals(Priority.NONE, Task.create(UUID.randomUUID(), "x", null, null, null).priority()); }
}
