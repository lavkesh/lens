package org.apache.lens.api.scheduler;

import org.apache.lens.api.LensSessionHandle;
import org.apache.lens.api.query.QueryHandle;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@AllArgsConstructor
@EqualsAndHashCode
public class SchedulerJobInstanceRun {

  /**
   * @param handle : Instance handle
   * @return the handle
   */
  private SchedulerJobInstanceHandle handle;

  /**
   * @param runId : run number of the instance run. Highest run number will represent the latest run.
   * @return the runId
   */
  private int runId;

  /**
   * @param sessionHandle new session handle.
   * @return session handle for this instance run.
   */
  private LensSessionHandle sessionHandle;
  /**
   * @param startTime start time to be set for the instance run.
   * @return actual start time of this instance run .
   */
  private long startTime;

  /**
   * @param endTime end time to be set for the instance run.
   * @return actual finish time of this instance run.
   */
  private long endTime;

  /**
   * @param resultPath result path to be set.
   * @return result path of this instance run.
   */
  private String resultPath;

  /**
   * @param query query to be set
   * @return query of this instance run.
   */
  private QueryHandle queryHandle;

  /**
   * @param status status to be set.
   * @return status of this instance.
   */
  private SchedulerJobInstanceStatus state;

}
