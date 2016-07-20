/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.lens.server.api.scheduler;

import org.apache.lens.api.scheduler.SchedulerJobStatus;
import org.apache.lens.server.api.error.InvalidStateTransitionException;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * This class represents current state of a SchedulerJob and provides helper methods
 * for handling different events and lifecycle transition for a SchedulerJob.
 */
@EqualsAndHashCode
public class SchedulerJobState {
  private static final SchedulerJobStatus INITIAL_STATUS = SchedulerJobStatus.NEW;
  @Getter
  @Setter
  private SchedulerJobStatus currentStatus;

  public SchedulerJobState(SchedulerJobStatus status) {
    this.currentStatus = status;
  }

  public SchedulerJobState() {
    this.currentStatus = INITIAL_STATUS;
  }

  public SchedulerJobState nextTransition(Event event) throws InvalidStateTransitionException {
    JobState currentJobState = JobState.valueOf(currentStatus.name());
    JobState newJobState = currentJobState.nextTransition(event);
    return new SchedulerJobState(SchedulerJobStatus.valueOf(newJobState.name()));
  }

  private enum JobState implements StateMachine<JobState, Event> {
    // repeating same operation will return the same state to ensure idempotent behavior.
    NEW {
      @Override
      public JobState nextTransition(Event event) throws InvalidStateTransitionException {
        switch (event) {
        case ON_SUBMIT:
          return this;
        case ON_SCHEDULE:
          return JobState.SCHEDULED;
        case ON_EXPIRE:
          return JobState.EXPIRED;
        case ON_DELETE:
          return JobState.DELETED;
        default:
          throw new InvalidStateTransitionException(
              "Event: " + event.name() + " is not a valid event for state: " + this.name());
        }
      }
    },

    SCHEDULED {
      @Override
      public JobState nextTransition(Event event) throws InvalidStateTransitionException {
        switch (event) {
        case ON_SCHEDULE:
          return this;
        case ON_SUSPEND:
          return JobState.SUSPENDED;
        case ON_EXPIRE:
          return JobState.EXPIRED;
        case ON_DELETE:
          return JobState.DELETED;
        default:
          throw new InvalidStateTransitionException(
              "Event: " + event.name() + " is not a valid event for state: " + this.name());
        }
      }
    },

    SUSPENDED {
      @Override
      public JobState nextTransition(Event event) throws InvalidStateTransitionException {
        switch (event) {
        case ON_SUSPEND:
          return this;
        case ON_RESUME:
          return JobState.SCHEDULED;
        case ON_EXPIRE:
          return JobState.EXPIRED;
        case ON_DELETE:
          return JobState.DELETED;
        default:
          throw new InvalidStateTransitionException(
              "Event: " + event.name() + " is not a valid event for state: " + this.name());
        }
      }
    },

    EXPIRED {
      @Override
      public JobState nextTransition(Event event) throws InvalidStateTransitionException {
        switch (event) {
        case ON_EXPIRE:
          return this;
        case ON_DELETE:
          return JobState.DELETED;
        default:
          throw new InvalidStateTransitionException(
              "Event: " + event.name() + " is not a valid event for state: " + this.name());
        }
      }
    },

    DELETED {
      @Override
      public JobState nextTransition(Event event) throws InvalidStateTransitionException {
        switch (event) {
        case ON_DELETE:
          return this;
        default:
          throw new InvalidStateTransitionException(
              "Event: " + event.name() + " is not a valid event for state: " + this.name());
        }
      }
    }
  }

  /**
   * All events(actions) which can happen on a Scheduler Job.
   */
  public enum Event {
    ON_SUBMIT,
    ON_SCHEDULE,
    ON_SUSPEND,
    ON_RESUME,
    ON_EXPIRE,
    ON_DELETE
  }
}
