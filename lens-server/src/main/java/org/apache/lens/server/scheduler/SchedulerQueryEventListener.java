package org.apache.lens.server.scheduler;

import org.apache.lens.api.query.QueryStatus;
import org.apache.lens.api.scheduler.SchedulerJobInstanceHandle;
import org.apache.lens.api.scheduler.SchedulerJobInstanceInfo;
import org.apache.lens.api.scheduler.SchedulerJobInstanceStatus;
import org.apache.lens.server.api.events.AsyncEventListener;
import org.apache.lens.server.api.query.QueryContext;
import org.apache.lens.server.api.query.QueryEnded;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SchedulerQueryEventListener extends AsyncEventListener<QueryEnded> {
  private static final String JOB_INSTANCE_ID_KEY = "job_instance_key";
  private static final int CORE_POOL_SIZE = 10;
  private SchedulerDAO schedulerDAO;

  public SchedulerQueryEventListener(SchedulerDAO schedulerDAO) {
    super(CORE_POOL_SIZE);
    this.schedulerDAO = schedulerDAO;
  }

  @Override
  public void process(QueryEnded event) {
    if (event.getCurrentValue() == QueryStatus.Status.CLOSED) {
      return;
    }
    QueryContext queryContext = event.getQueryContext();
    if (queryContext == null) {
      log.warn("Could not find the context for {} for event:{}.", event.getQueryHandle(), event.getCurrentValue());
      return;
    }
    String instanceHandle = queryContext.getConf().get(JOB_INSTANCE_ID_KEY);
    if (instanceHandle == null) {
      // Nothing to do
      return;
    }
    SchedulerJobInstanceStatus state;
    switch (event.getCurrentValue()) {
    case CANCELED:
      state = SchedulerJobInstanceStatus.KILLED;
      break;
    case SUCCESSFUL:
      state = SchedulerJobInstanceStatus.SUCCEEDED;
      break;
    case FAILED:
      state = SchedulerJobInstanceStatus.FAILED;
      break;
    default:
      // It should not come here.
      state = SchedulerJobInstanceStatus.FAILED;
    }
    SchedulerJobInstanceInfo info = schedulerDAO
        .getSchedulerJobInstanceInfo(SchedulerJobInstanceHandle.fromString(instanceHandle));
    info.setEndTime(System.currentTimeMillis());
    info.setState(state);
    info.setResultPath(queryContext.getDriverResultPath());
    schedulerDAO.updateJobInstance(info);
  }
}
