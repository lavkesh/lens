package org.apache.lens.api.scheduler;

/**
 * All events(actions) which can happen on a Scheduler Job.
 */
public enum SchedulerJobEvent {
  ON_SUBMIT,
  ON_SCHEDULE,
  ON_SUSPEND,
  ON_RESUME,
  ON_EXPIRE,
  ON_DELETE
}
