package org.apache.lens.api.scheduler;

/**
 * All events(actions) which can happen on an instance of <code>SchedulerJob</code>.
 */
public enum SchedulerJobInstanceEvent {
  ON_CREATION, // an instance is first considered by the scheduler.
  ON_TIME_OUT,
  ON_CONDITIONS_MET,
  ON_RUN,
  ON_SUCCESS,
  ON_FAILURE,
  ON_RERUN,
  ON_KILL
}
