package org.apache.lens.server.scheduler;

import java.util.List;

import org.apache.lens.api.query.QueryStatus;
import org.apache.lens.api.scheduler.SchedulerJobInstanceHandle;
import org.apache.lens.api.scheduler.SchedulerJobInstanceInfo;
import org.apache.lens.api.scheduler.SchedulerJobInstanceRun;
import org.apache.lens.server.api.error.InvalidStateTransitionException;
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
    SchedulerJobInstanceInfo info = schedulerDAO
        .getSchedulerJobInstanceInfo(SchedulerJobInstanceHandle.fromString(instanceHandle));
    List<SchedulerJobInstanceRun> runList = info.getInstanceRunList();
    if (runList.size() == 0) {
      log.error("No instance run for " + instanceHandle + " with query " + queryContext.getQueryHandle());
      return;
    }
    SchedulerJobInstanceRun latestRun = runList.get(runList.size() - 1);
    SchedulerJobInstanceState state = latestRun.getState();
    try {
      switch (event.getCurrentValue()) {
      case CANCELED:
        state = state.nextTransition(SchedulerJobInstanceState.EVENT.ON_KILL);
        break;
      case SUCCESSFUL:
        state = state.nextTransition(SchedulerJobInstanceState.EVENT.ON_SUCCESS);
        break;
      case FAILED:
        state = state.nextTransition(SchedulerJobInstanceState.EVENT.ON_FAILURE);
        break;
      }
      latestRun.setEndTime(System.currentTimeMillis());
      latestRun.setState(state);
      latestRun.setResultPath(queryContext.getDriverResultPath());
      schedulerDAO.updateJobInstanceRun(latestRun);
    } catch (InvalidStateTransitionException e) {
      log.error("Instance Transition Failed ", e);
    }
  }
}
