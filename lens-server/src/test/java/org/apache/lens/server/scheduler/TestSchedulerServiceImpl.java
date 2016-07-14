package org.apache.lens.server.scheduler;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;

import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.lens.api.LensConf;
import org.apache.lens.api.LensSessionHandle;
import org.apache.lens.api.query.QueryHandle;
import org.apache.lens.api.query.QueryStatus;
import org.apache.lens.api.scheduler.*;
import org.apache.lens.server.EventServiceImpl;
import org.apache.lens.server.LensServerConf;
import org.apache.lens.server.LensServices;
import org.apache.lens.server.api.LensConfConstants;
import org.apache.lens.server.api.query.QueryContext;
import org.apache.lens.server.api.query.QueryEnded;
import org.apache.lens.server.api.query.QueryExecutionService;
import org.apache.lens.server.api.scheduler.SchedulerService;
import org.apache.lens.server.query.QueryExecutionServiceImpl;

import org.apache.hadoop.conf.Configuration;

import org.powermock.api.mockito.PowerMockito;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Test(groups = "unit-test")
public class TestSchedulerServiceImpl {

  SchedulerServiceImpl scheduler;
  EventServiceImpl eventService;

  @BeforeClass
  public void setup() throws Exception {
    System.setProperty(LensConfConstants.CONFIG_LOCATION, "target/test-classes/");
    LensServices.get().init(LensServerConf.getHiveConf());
    LensServices.get().start();
    scheduler = LensServices.get().getService(SchedulerService.NAME);
    eventService = LensServices.get().getService(EventServiceImpl.NAME);
    QueryExecutionService queryExecutionService = PowerMockito.mock(QueryExecutionService.class);
    scheduler.setQueryService(queryExecutionService);
    PowerMockito.when(scheduler.getQueryService()
        .estimate(anyString(), any(LensSessionHandle.class), anyString(), any(LensConf.class))).thenReturn(null);
    PowerMockito.when(scheduler.getQueryService()
        .executeAsync(any(LensSessionHandle.class), anyString(), any(LensConf.class), anyString()))
        .thenReturn(new QueryHandle(UUID.randomUUID()));
    scheduler.getSchedulerEventListener().setQueryService(queryExecutionService);
  }

  private QueryEnded mockQueryEnded(SchedulerJobInstanceHandle instanceHandle, QueryStatus.Status status) {
    QueryContext mockContext = PowerMockito.mock(QueryContext.class);
    PowerMockito.when(mockContext.getDriverResultPath()).thenReturn("/tmp/query1/result");
    Configuration conf = new Configuration();
    // set the instance handle
    conf.set("job_instance_key", instanceHandle.getHandleIdString());
    PowerMockito.when(mockContext.getConf()).thenReturn(conf);
    // Get the queryHandle.
    PowerMockito.when(mockContext.getQueryHandle()).thenReturn(new QueryHandle(UUID.randomUUID()));
    QueryEnded queryEnded = PowerMockito.mock(QueryEnded.class);
    PowerMockito.when(queryEnded.getQueryContext()).thenReturn(mockContext);
    PowerMockito.when(queryEnded.getCurrentValue()).thenReturn(status);
    return queryEnded;
  }

  @Test
  public void testScheduler() throws Exception {
    LensSessionHandle sessionHandle = ((QueryExecutionServiceImpl) LensServices.get()
        .getService(QueryExecutionService.NAME)).openSession("test", "test", new HashMap<String, String>(), false);
    long currentTime = System.currentTimeMillis();
    XJob job = getTestJob("0/5 * * * * ?", currentTime, currentTime + 15000);
    // job instance has 5 second frequency for 15 second.
    SchedulerJobHandle jobHandle = scheduler.submitAndScheduleJob(sessionHandle, job);
    Assert.assertNotNull(jobHandle);
    Assert.assertEquals(scheduler.getSchedulerDAO().getJobState(jobHandle).getCurrentStatus(),
        SchedulerJobStatus.SCHEDULED);
    // Wait for job to finish
    Thread.sleep(30000);
    List<SchedulerJobInstanceHandle> instanceHandleList = scheduler.getSchedulerDAO().getJobInstances(jobHandle);
    Assert.assertEquals(instanceHandleList.size() >= 3, true);
    Assert.assertEquals(scheduler.getSchedulerDAO().getJobState(jobHandle).getCurrentStatus(),
        SchedulerJobStatus.EXPIRED);
    // SuccessFul query
    eventService.notifyEvent(mockQueryEnded(instanceHandleList.get(0), QueryStatus.Status.SUCCESSFUL));
    // Wait, for event to get processed
    Thread.sleep(2000);
    // Check the instance value
    SchedulerJobInstanceInfo info = scheduler.getSchedulerDAO().getSchedulerJobInstanceInfo(instanceHandleList.get(0));
    Assert.assertEquals(info.getInstanceRunList().size(), 1);
    Assert.assertEquals(info.getInstanceRunList().get(0).getResultPath(), "/tmp/query1/result");
    Assert.assertEquals(info.getInstanceRunList().get(0).getState().getCurrentStatus(),
        SchedulerJobInstanceStatus.SUCCEEDED);
  }

  @Test
  public void testSuspendResume() throws Exception {
    LensSessionHandle sessionHandle = ((QueryExecutionServiceImpl) LensServices.get()
        .getService(QueryExecutionService.NAME)).openSession("test", "test", new HashMap<String, String>(), false);
    long currentTime = System.currentTimeMillis();
    XJob job = getTestJob("0/5 * * * * ?", currentTime, currentTime + 180000);
    SchedulerJobHandle jobHandle = scheduler.submitAndScheduleJob(sessionHandle, job);
    Assert.assertNotNull(jobHandle);
    Assert.assertTrue(scheduler.suspendJob(sessionHandle, jobHandle));
    Assert.assertEquals(scheduler.getSchedulerDAO().getJobState(jobHandle).getCurrentStatus(),
        SchedulerJobStatus.SUSPENDED);
    Assert.assertTrue(scheduler.resumeJob(sessionHandle, jobHandle));
    Assert.assertEquals(scheduler.getSchedulerDAO().getJobState(jobHandle).getCurrentStatus(),
        SchedulerJobStatus.SCHEDULED);
    Thread.sleep(10000);
    Assert.assertTrue(scheduler.expireJob(sessionHandle, jobHandle));
    Assert.assertEquals(scheduler.getSchedulerDAO().getJobState(jobHandle).getCurrentStatus(),
        SchedulerJobStatus.EXPIRED);
  }

  @Test
  public void testRerunInstance() throws Exception {
    LensSessionHandle sessionHandle = ((QueryExecutionServiceImpl) LensServices.get()
        .getService(QueryExecutionService.NAME)).openSession("test", "test", new HashMap<String, String>(), false);
    long currentTime = System.currentTimeMillis();

    XJob job = getTestJob("0/30 * * * * ?", currentTime, currentTime + 180000);
    SchedulerJobHandle jobHandle = scheduler.submitAndScheduleJob(sessionHandle, job);
    // Wait for some instances.
    Thread.sleep(30000);
    List<SchedulerJobInstanceHandle> instanceHandleList = scheduler.getSchedulerDAO().getJobInstances(jobHandle);
    // Mark fail
    eventService.notifyEvent(mockQueryEnded(instanceHandleList.get(0), QueryStatus.Status.FAILED));
    Thread.sleep(1000);
    SchedulerJobInstanceInfo info = scheduler.getSchedulerDAO().getSchedulerJobInstanceInfo(instanceHandleList.get(0));
    // First run
    Assert.assertEquals(info.getInstanceRunList().size(), 1);
    Assert.assertEquals(info.getInstanceRunList().get(0).getState().getCurrentStatus(),
        SchedulerJobInstanceStatus.FAILED);

    // Rerun
    Assert.assertTrue(scheduler.rerunInstance(sessionHandle, instanceHandleList.get(0)));
    Thread.sleep(3000);
    eventService.notifyEvent(mockQueryEnded(instanceHandleList.get(0), QueryStatus.Status.SUCCESSFUL));
    Thread.sleep(1000);
    info = scheduler.getSchedulerDAO().getSchedulerJobInstanceInfo(instanceHandleList.get(0));
    // There should be 2 reruns.
    Assert.assertEquals(info.getInstanceRunList().size(), 2);
    Assert.assertEquals(info.getInstanceRunList().get(1).getResultPath(), "/tmp/query1/result");
    Assert.assertEquals(info.getInstanceRunList().get(1).getState().getCurrentStatus(),
        SchedulerJobInstanceStatus.SUCCEEDED);
  }

  private XTrigger getTestTrigger(String cron) {
    XTrigger trigger = new XTrigger();
    XFrequency frequency = new XFrequency();
    frequency.setCronExpression(cron);
    frequency.setTimezone("UTC");
    trigger.setFrequency(frequency);
    return trigger;
  }

  private XExecution getTestExecution() {
    XExecution execution = new XExecution();
    XJobQuery query = new XJobQuery();
    query.setQuery("select ID from test_table");
    execution.setQuery(query);
    XSessionType sessionType = new XSessionType();
    sessionType.setDb("test");
    execution.setSession(sessionType);
    return execution;
  }

  private XJob getTestJob(String cron, long start, long end) throws Exception {
    XJob job = new XJob();
    job.setTrigger(getTestTrigger(cron));
    job.setName("Test lens Job");
    GregorianCalendar startTime = new GregorianCalendar();
    startTime.setTimeInMillis(start);
    XMLGregorianCalendar startCal = DatatypeFactory.newInstance().newXMLGregorianCalendar(startTime);

    GregorianCalendar endTime = new GregorianCalendar();
    endTime.setTimeInMillis(end);
    XMLGregorianCalendar endCal = DatatypeFactory.newInstance().newXMLGregorianCalendar(endTime);

    job.setStartTime(startCal);
    job.setEndTime(endCal);
    job.setExecution(getTestExecution());
    return job;
  }
}
