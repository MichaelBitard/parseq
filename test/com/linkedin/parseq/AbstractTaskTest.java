package com.linkedin.parseq;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.testng.annotations.Test;

import com.linkedin.parseq.function.Try;


/**
 * @author Jaroslaw Odzga (jodzga@linkedin.com)
 */
public abstract class AbstractTaskTest extends BaseEngineTest {

  public void testMap(int expectedNumberOfTasks) {
    Task<Integer> task = getSuccessTask().map(String::length);

    runAndWait("AbstractTaskTest.testMap", task);
    assertTrue(task.isDone());
    assertEquals((int) task.get(), TASK_VALUE.length());

    assertEquals(countTasks(task.getTrace()), expectedNumberOfTasks);
  }

  public void testFlatMap(int expectedNumberOfTasks) {
    Task<String> task =
        getSuccessTask().flatMap(str -> Task.callable("strlenstr", () -> String.valueOf(str.length())));

    runAndWait("AbstractTaskTest.testFlatMap", task);
    assertEquals(task.get(), String.valueOf(TASK_VALUE.length()));

    assertEquals(countTasks(task.getTrace()), expectedNumberOfTasks);
  }

  public void testAndThenConsumer(int expectedNumberOfTasks) {
    final AtomicReference<String> variable = new AtomicReference<String>();
    Task<String> task = getSuccessTask().andThen(variable::set);
    runAndWait("AbstractTaskTest.testAndThenConsumer", task);
    assertEquals(task.get(), TASK_VALUE);
    assertEquals(variable.get(), TASK_VALUE);

    assertEquals(countTasks(task.getTrace()), expectedNumberOfTasks);
  }

  public void testAndThenTask(int expectedNumberOfTasks) {
    Task<Integer> task = getSuccessTask().andThen(Task.callable("life", () -> 42));
    runAndWait("AbstractTaskTest.testAndThenTask", task);
    assertEquals((int) task.get(), 42);

    assertEquals(countTasks(task.getTrace()), expectedNumberOfTasks);
  }

  public void testRecover(int expectedNumberOfTasks) {
    Task<Integer> success = getSuccessTask().map("strlen", String::length).recover(e -> -1);
    runAndWait("AbstractTaskTest.testRecoverSuccess", success);
    assertEquals((int) success.get(), TASK_VALUE.length());
    assertEquals(countTasks(success.getTrace()), expectedNumberOfTasks);


    Task<Integer> failure = getFailureTask().map("strlen", String::length).recover(e -> -1);
    runAndWait("AbstractTaskTest.testRecoverFailure", failure);
    assertEquals((int) failure.get(), -1);
    assertEquals(countTasks(failure.getTrace()), expectedNumberOfTasks);
  }

  public void testNoRecover(int expectedNumberOfTasks) {
    Task<Integer> task = getFailureTask().map("strlen", String::length);
    try {
      runAndWait("AbstractTaskTest.testNoRecover", task);
      fail("should have failed");
    } catch (Exception ex) {
      assertEquals(ex.getCause().getMessage(), TASK_ERROR_MESSAGE);
    }
    assertEquals(countTasks(task.getTrace()), expectedNumberOfTasks);
  }

  public void testTry(int expectedNumberOfTasks) {
    Task<Try<Integer>> success = getSuccessTask().map("strlen", String::length).withTry();
    runAndWait("AbstractTaskTest.testTrySuccess", success);
    assertFalse(success.get().isFailed());
    assertEquals((int) success.get().get(), TASK_VALUE.length());
    assertEquals(countTasks(success.getTrace()), expectedNumberOfTasks);

    Task<Try<Integer>> failure = getFailureTask().map("strlen", String::length).withTry();
    runAndWait("AbstractTaskTest.testTryFailure", failure);
    assertTrue(failure.get().isFailed());
    assertEquals(failure.get().getError().getMessage(), TASK_ERROR_MESSAGE);
    assertEquals(countTasks(failure.getTrace()), expectedNumberOfTasks);
  }

  public void testRecoverWithSuccess(int expectedNumberOfTasks) {
    Task<String> success =
        getSuccessTask().recoverWith(e -> Task.callable("recover failure", () -> {
              throw new RuntimeException("recover failed!");
            }));
    runAndWait("AbstractTaskTest.testRecoverWithSuccess", success);
    assertEquals(success.get(), TASK_VALUE);
    assertEquals(countTasks(success.getTrace()), expectedNumberOfTasks);
  }

  public void testRecoverWithFailure(int expectedNumberOfTasks) {
    Task<String> failure =
        getFailureTask().recoverWith(e -> Task.callable("recover failure", () -> {
              throw new RuntimeException("recover failed!");
            }));
    try {
      runAndWait("AbstractTaskTest.testRecoverWithFailure", failure);
      fail("should have failed");
    } catch (Exception ex) {
      // fail with throwable from recovery function
      assertEquals(ex.getCause().getMessage(), "recover failed!");
    }
    assertEquals(countTasks(failure.getTrace()), expectedNumberOfTasks);
  }

  public void testRecoverWithRecoverd(int expectedNumberOfTasks) {
    Task<String> recovered =
        getFailureTask().recoverWith(e -> Task.callable("recover success", () -> "recovered"));
    runAndWait("AbstractTaskTest.testRecoverWithRecoverd", recovered);
    assertEquals(recovered.get(), "recovered");
    assertEquals(countTasks(recovered.getTrace()), expectedNumberOfTasks);
  }

  @Test
  public void testWithTimeoutSuccess() {
    Task<Integer> success =
        getSuccessTask().andThen(delayedValue(0, 30, TimeUnit.MILLISECONDS)).withTimeout(100, TimeUnit.MILLISECONDS);
    runAndWait("AbstractTaskTest.testWithTimeoutSuccess", success);
    assertEquals((int) success.get(), 0);
    assertEquals(countTasks(success.getTrace()), 5);
  }

  @Test
  public void testWithTimeoutTwiceSuccess() {
    Task<Integer> success =
        getSuccessTask().andThen(delayedValue(0, 30, TimeUnit.MILLISECONDS))
        .withTimeout(100, TimeUnit.MILLISECONDS)
        .withTimeout(5000, TimeUnit.MILLISECONDS);
    runAndWait("AbstractTaskTest.testWithTimeoutTwiceSuccess", success);
    assertEquals((int) success.get(), 0);
    assertEquals(countTasks(success.getTrace()), 7);
  }

  @Test
  public void testWithTimeoutFailure() {
    Task<Integer> failure =
        getSuccessTask().andThen(delayedValue(0, 110, TimeUnit.MILLISECONDS))
        .withTimeout(100, TimeUnit.MILLISECONDS);
    try {
      runAndWait("AbstractTaskTest.testWithTimeoutFailure", failure);
      fail("should have failed!");
    } catch (Exception ex) {
      assertSame(ex.getCause(), Exceptions.TIMEOUT_EXCEPTION);
    }
    assertEquals(countTasks(failure.getTrace()), 5);
  }

  @Test
  public void testWithTimeoutTwiceFailure() {
    Task<Integer> failure =
        getSuccessTask().andThen(delayedValue(0, 110, TimeUnit.MILLISECONDS))
        .withTimeout(5000, TimeUnit.MILLISECONDS)
        .withTimeout(100, TimeUnit.MILLISECONDS);
    try {
      runAndWait("AbstractTaskTest.testWithTimeoutTwiceFailure", failure);
      fail("should have failed!");
    } catch (Exception ex) {
      assertSame(ex.getCause(), Exceptions.TIMEOUT_EXCEPTION);
    }
    assertEquals(countTasks(failure.getTrace()), 7);
  }

  @Test
  public void testWithSideEffectPartial() {
    Task<String> fastMain = getSuccessTask();
    Task<String> slowSideEffect = delayedValue("slooow", 5100, TimeUnit.MILLISECONDS);
    Task<String> partial = fastMain.withSideEffect(s -> slowSideEffect);

    // ensure the whole task can finish before individual side effect task finishes
    runAndWait("AbstractTaskTest.testWithSideEffectPartial", partial);
    assertTrue(fastMain.isDone());
    assertTrue(partial.isDone());
    assertFalse(slowSideEffect.isDone());
  }

  public void testWithSideEffectFullCompletion(int expectedNumberOfTasks) throws Exception {
    Task<String> fastMain = getSuccessTask();
    Task<String> slowSideEffect = delayedValue("slow", 50, TimeUnit.MILLISECONDS);
    Task<String> full = fastMain.withSideEffect(s -> slowSideEffect);


    // ensure the side effect task will be run
    runAndWait("AbstractTaskTest.testWithSideEffectFullCompletion", full);
    slowSideEffect.await();
    assertTrue(full.isDone());
    assertTrue(fastMain.isDone());
    assertTrue(slowSideEffect.isDone());
    assertEquals(countTasks(full.getTrace()), expectedNumberOfTasks);
  }

  @Test
  public void testWithSideEffectCalcel() throws Exception {
    Task<String> cancelMain = delayedValue("canceled", 6000, TimeUnit.MILLISECONDS);
    Task<String> fastSideEffect = getSuccessTask();
    Task<String> cancel = cancelMain.withSideEffect(s -> fastSideEffect);

    // test cancel, side effect task should not be run
    // add 10 ms delay so that we can reliably cancel it before it's run by the engine
    run(delayedValue(0, 10, TimeUnit.MILLISECONDS).andThen(cancel));
    assertTrue(cancelMain.cancel(new Exception("canceled")));
    cancel.await();
    fastSideEffect.await(10, TimeUnit.MILLISECONDS);
    assertTrue(cancel.isDone());
    assertFalse(fastSideEffect.isDone());
    logTracingResults("AbstractTaskTest.testWithSideEffectCalcel", cancel);
  }

  public void testWithSideEffectFailure(int expectedNumberOfTasks) throws Exception {
    Task<String> failureMain = getFailureTask();
    Task<String> fastSideEffect = getSuccessTask();
    Task<String> failure = failureMain.withSideEffect(s -> fastSideEffect);

    // test failure, side effect task should not be run
    try {
      runAndWait("AbstractTaskTest.testWithSideEffectFailure", failure);
      fail("should have failed");
    } catch (Exception ex) {
      assertTrue(failure.isFailed());
      fastSideEffect.await(10, TimeUnit.MILLISECONDS);
      assertFalse(fastSideEffect.isDone());
    }
    assertEquals(countTasks(failure.getTrace()), expectedNumberOfTasks);
  }

  public void testOnFailure(int expectedNumberOfTasks) {
    final AtomicReference<Throwable> variable = new AtomicReference<Throwable>();
    Task<String> success = getSuccessTask().onFailure(variable::set);
    runAndWait("AbstractTaskTest.testOnFailureSuccess", success);
    assertNull(variable.get());
    assertEquals(countTasks(success.getTrace()), expectedNumberOfTasks);

    Task<String> failure = getFailureTask().onFailure(variable::set);
    try {
      runAndWait("AbstractTaskTest.testRecoverFailure", failure);
      fail("should have failed");
    } catch (Exception ex) {
      assertTrue(failure.isFailed());
    }
    assertEquals(variable.get().getMessage(), TASK_ERROR_MESSAGE);
    assertEquals(countTasks(failure.getTrace()), expectedNumberOfTasks);
  }

  protected static final String TASK_VALUE = "value";
  protected static final String TASK_ERROR_MESSAGE = "error";

  abstract Task<String> getSuccessTask();

  abstract Task<String> getFailureTask();
}
