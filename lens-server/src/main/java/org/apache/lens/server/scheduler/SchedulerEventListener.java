package org.apache.lens.server.scheduler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lens.api.LensConf;
import org.apache.lens.api.LensSessionHandle;
import org.apache.lens.api.query.QueryHandle;
import org.apache.lens.api.scheduler.*;
import org.apache.lens.server.api.LensConfConstants;
import org.apache.lens.server.api.error.LensException;
import org.apache.lens.server.api.events.AsyncEventListener;
import org.apache.lens.server.api.events.SchedulerAlarmEvent;
import org.apache.lens.server.api.scheduler.SchedulerService;
import org.apache.lens.server.query.QueryExecutionServiceImpl;
import org.apache.lens.server.scheduler.util.UtilityMethods;
import org.apache.lens.server.session.LensSessionImpl;

import org.apache.hive.service.cli.HiveSQLException;

import org.joda.time.DateTime;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SchedulerEventListener extends AsyncEventListener<SchedulerAlarmEvent> {
  private static final int CORE_POOL_SIZE = 10;
  private static final String JOB_INSTANCE_ID_KEY = "job_instance_key";
  private QueryExecutionServiceImpl queryService;
  private SchedulerDAO schedulerDAO;
  private SchedulerService schedulerService;

  public SchedulerEventListener(QueryExecutionServiceImpl queryService, SchedulerDAO schedulerDAO,
      SchedulerService schedulerService) {
    super(CORE_POOL_SIZE);
    this.queryService = queryService;
    this.schedulerDAO = schedulerDAO;
    this.schedulerService = schedulerService;
  }

  /**
   * @param event the event
   */
  @Override
  public void process(SchedulerAlarmEvent event) {
    DateTime scheduledTime = event.getNominalTime();
    SchedulerJobHandle jobHandle = event.getJobHandle();
    if (event.getType() == SchedulerAlarmEvent.EventType.EXPIRE) {
      try {
        schedulerService.expireJob(null, jobHandle);
      } catch (LensException e) {
        log.error("Error while expiring the job", e);
      }
      return;
    }
    /*
     * Get the job from the store.
     * Create an instance.
     * Store the instance.
     * Try to run the instance.
     * If successfully submitted change the status to running.
     * Otherwise update the status to killed.
     */
    //TODO: Get the job status and if it is not Scheduled, don't do anything.
    XJob job = schedulerDAO.getJob(jobHandle);
    String user = schedulerDAO.getUser(jobHandle);
    SchedulerJobInstanceHandle instanceHandle = UtilityMethods.generateSchedulerJobInstanceHandle();
    Map<String, String> conf = new HashMap<>();
    LensSessionHandle sessionHandle = null;
    try {
      // Open the session with the user who submitted the job.
      sessionHandle = queryService.openSession(user, "dummy", conf, false);
    } catch (LensException e) {
      log.error("Error occurred while opening a session ", e);
      return;
    }
    SchedulerJobInstanceInfo instance = null;
    // Session needs to be closed after the launch.
    try (LensSessionImpl session = queryService.getSession(sessionHandle)) {
      long scheduledTimeMillis = scheduledTime.getMillis();
      String query = job.getExecution().getQuery().getQuery();
      long currentTime = System.currentTimeMillis();
      List<MapType> jobConf = job.getExecution().getQuery().getConf();
      LensConf queryConf = new LensConf();
      for (MapType element : jobConf) {
        queryConf.addProperty(element.getKey(), element.getValue());
      }
      queryConf.addProperty(JOB_INSTANCE_ID_KEY, instanceHandle.getHandleId());
      // Current time is used for resolving date.
      queryConf.addProperty(LensConfConstants.QUERY_CURRENT_TIME, scheduledTime.getMillis());
      String queryName = job.getName();
      queryName += "-" + scheduledTime.getMillis();
      instance = new SchedulerJobInstanceInfo(instanceHandle, jobHandle, sessionHandle, currentTime, 0, "N/A",
          QueryHandle.fromString(null), SchedulerJobInstanceStatus.WAITING, scheduledTimeMillis);
      boolean success = schedulerDAO.storeJobInstance(instance) == 1;
      if (!success) {
        log.error(
            "Exception occurred while storing the instance for instance handle " + instance + " of job " + jobHandle);
        return;
      }
      //TODO: Handle waiting status
      QueryHandle handle = queryService.executeAsync(sessionHandle, query, queryConf, queryName);
      instance.setQueryHandle(handle);
      instance.setStatus(SchedulerJobInstanceStatus.LAUNCHED);
      instance.setEndTime(System.currentTimeMillis());
      schedulerDAO.updateJobInstance(instance);
    } catch (LensException e) {
      log.error(
          "Exception occurred while launching the job instance for " + jobHandle + " for nominal time " + scheduledTime
              .getMillis(), e);
      instance.setStatus(SchedulerJobInstanceStatus.FAILED);
      instance.setEndTime(System.currentTimeMillis());
      schedulerDAO.updateJobInstance(instance);
    } catch (HiveSQLException e) {
      log.error("Error occurred for " + jobHandle + " for nominal time " + scheduledTime.getMillis(), e);
      instance.setStatus(SchedulerJobInstanceStatus.FAILED);
      instance.setEndTime(System.currentTimeMillis());
      schedulerDAO.updateJobInstance(instance);
    }
  }
}
