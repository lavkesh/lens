package org.apache.lens.server.scheduler;

import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.Application;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.lens.api.LensSessionHandle;
import org.apache.lens.api.scheduler.*;
import org.apache.lens.server.LensJerseyTest;
import org.apache.lens.server.LensServerConf;
import org.apache.lens.server.LensServerTestUtil;
import org.apache.lens.server.LensServices;
import org.apache.lens.server.api.LensConfConstants;
import org.apache.lens.server.api.query.QueryExecutionService;
import org.apache.lens.server.api.scheduler.SchedulerService;
import org.apache.lens.server.common.TestResourceFile;
import org.apache.lens.server.query.QueryExecutionServiceImpl;
import org.apache.lens.server.query.TestQueryService;

import org.glassfish.jersey.test.TestProperties;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class SchedulerServiceTest extends LensJerseyTest {
  SchedulerService schedulerService;
  private LensSessionHandle lensSessionId;
  private QueryExecutionServiceImpl queryService;
  private String TEST_TABLE = "test_table_scheduler";

  @BeforeClass
  public void setup() throws Exception {
    super.setUp();
    System.setProperty(LensConfConstants.CONFIG_LOCATION, "target/test-classes/");
    LensServices.get().init(LensServerConf.getHiveConf());
    LensServices.get().start();
    schedulerService = LensServices.get().getService(SchedulerService.NAME);
    queryService = LensServices.get().getService(QueryExecutionService.NAME);
    Map<String, String> sessionconf = new HashMap<>();
    sessionconf.put("test.session.key", "svalue");
    lensSessionId = queryService.openSession("testuser", "bar", sessionconf, false);
    LensServerTestUtil.dropTable(TEST_TABLE, target(), lensSessionId, defaultMT);
    LensServerTestUtil.createTable(TEST_TABLE, target(), lensSessionId, defaultMT);
    LensServerTestUtil
        .loadDataFromClasspath(TEST_TABLE, TestResourceFile.TEST_DATA2_FILE.getValue(), target(), lensSessionId,
            defaultMT);
  }

  private XTrigger getTestTrigger() {
    XTrigger trigger = new XTrigger();
    XFrequency frequency = new XFrequency();
    frequency.setCronExpression("0/30 * * * * ?");
    frequency.setTimezone("UTC");
    trigger.setFrequency(frequency);
    return trigger;
  }

  private XExecution getTestExecution() {
    XExecution execution = new XExecution();
    XJobQuery query = new XJobQuery();
    query.setQuery("select ID from " + TEST_TABLE);
    execution.setQuery(query);
    XSessionType sessionType = new XSessionType();
    sessionType.setDb("test");
    execution.setSession(sessionType);
    return execution;
  }

  private XJob getTestJob() throws DatatypeConfigurationException {
    XJob job = new XJob();
    job.setTrigger(getTestTrigger());
    job.setName("Test lens Job");
    GregorianCalendar startTime = new GregorianCalendar();
    startTime.setTimeInMillis(System.currentTimeMillis());
    XMLGregorianCalendar start = DatatypeFactory.newInstance().newXMLGregorianCalendar(startTime);

    GregorianCalendar endTime = new GregorianCalendar();
    endTime.setTimeInMillis(System.currentTimeMillis() + 120000);
    XMLGregorianCalendar end = DatatypeFactory.newInstance().newXMLGregorianCalendar(endTime);

    job.setStartTime(start);
    job.setEndTime(end);
    job.setExecution(getTestExecution());
    return job;
  }

  @Test
  public void test() throws Exception {
    XJob job = getTestJob();
    SchedulerJobHandle handle = schedulerService.submitJob(lensSessionId, job);
    schedulerService.scheduleJob(lensSessionId, handle);

    //    SchedulerJobHandle handle2 = schedulerService.submitJob(lensSessionId, job);
    //   schedulerService.scheduleJob(lensSessionId, handle2);
    Thread.sleep(180000);
  }

  @Override
  protected Application configure() {
    enable(TestProperties.LOG_TRAFFIC);
    enable(TestProperties.DUMP_ENTITY);
    return new TestQueryService.QueryServiceTestApp();
  }
}
