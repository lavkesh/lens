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

import org.apache.lens.api.scheduler.SchedulerJobInstanceStatus;
import org.apache.lens.server.api.error.InvalidStateTransitionException;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * State machine for transitions on Scheduler Jobs.
 */
@EqualsAndHashCode
public class SchedulerJobInstanceState {

  private static final SchedulerJobInstanceStatus INITIAL_STATUS = SchedulerJobInstanceStatus.WAITING;
  @Getter
  @Setter
  private SchedulerJobInstanceStatus currentStatus;

  public SchedulerJobInstanceState(SchedulerJobInstanceStatus status) {
    this.currentStatus = status;
  }

  public SchedulerJobInstanceState() {
    this.currentStatus = INITIAL_STATUS;
  }

  public SchedulerJobInstanceState nextTransition(Event event) throws InvalidStateTransitionException {
    JobInstanceState currentJobInstanceState = JobInstanceState.valueOf(currentStatus.name());
    JobInstanceState newJobInstanceState = currentJobInstanceState.nextTransition(event);
    return new SchedulerJobInstanceState(SchedulerJobInstanceStatus.valueOf(newJobInstanceState.name()));
  }

  public enum JobInstanceState implements StateMachine<JobInstanceState, Event> {
    // repeating same operation will return the same state to ensure idempotent behavior.
    WAITING {
      @Override
      public JobInstanceState nextTransition(Event event) throws InvalidStateTransitionException {
        switch (event) {
        case ON_CREATION:
          return this;
        case ON_CONDITIONS_MET:
          return JobInstanceState.LAUNCHED;
        case ON_TIME_OUT:
          return JobInstanceState.TIMED_OUT;
        case ON_RUN:
          return JobInstanceState.RUNNING;
        case ON_SUCCESS:
          return JobInstanceState.SUCCEEDED;
        case ON_FAILURE:
          return JobInstanceState.FAILED;
        case ON_KILL:
          return JobInstanceState.KILLED;
        default:
          throw new InvalidStateTransitionException(
              "Event: " + event.name() + " is not a valid event for state: " + this.name());
        }
      }
    },

    LAUNCHED {
      @Override
      public JobInstanceState nextTransition(Event event) throws InvalidStateTransitionException {
        switch (event) {
        case ON_CONDITIONS_MET:
          return this;
        case ON_RUN:
          return JobInstanceState.RUNNING;
        case ON_SUCCESS:
          return JobInstanceState.SUCCEEDED;
        case ON_FAILURE:
          return JobInstanceState.FAILED;
        case ON_KILL:
          return JobInstanceState.KILLED;
        default:
          throw new InvalidStateTransitionException(
              "Event: " + event.name() + " is not a valid event for state: " + this.name());
        }
      }
    },

    RUNNING {
      @Override
      public JobInstanceState nextTransition(Event event) throws InvalidStateTransitionException {
        switch (event) {
        case ON_RUN:
          return this;
        case ON_SUCCESS:
          return JobInstanceState.SUCCEEDED;
        case ON_FAILURE:
          return JobInstanceState.FAILED;
        case ON_KILL:
          return JobInstanceState.KILLED;
        default:
          throw new InvalidStateTransitionException(
              "Event: " + event.name() + " is not a valid event for state: " + this.name());
        }
      }
    },

    FAILED {
      @Override
      public JobInstanceState nextTransition(Event event) throws InvalidStateTransitionException {
        switch (event) {
        case ON_FAILURE:
          return this;
        case ON_RERUN:
          return JobInstanceState.LAUNCHED;
        default:
          throw new InvalidStateTransitionException(
              "Event: " + event.name() + " is not a valid event for state: " + this.name());
        }
      }
    },

    SUCCEEDED {
      @Override
      public JobInstanceState nextTransition(Event event) throws InvalidStateTransitionException {
        switch (event) {
        case ON_SUCCESS:
          return this;
        case ON_RERUN:
          return JobInstanceState.LAUNCHED;
        default:
          throw new InvalidStateTransitionException(
              "Event: " + event.name() + " is not a valid event for state: " + this.name());
        }
      }
    },

    TIMED_OUT {
      @Override
      public JobInstanceState nextTransition(Event event) throws InvalidStateTransitionException {
        switch (event) {
        case ON_TIME_OUT:
          return this;
        case ON_RERUN:
          return JobInstanceState.WAITING;
        default:
          throw new InvalidStateTransitionException(
              "Event: " + event.name() + " is not a valid event for state: " + this.name());
        }
      }
    },

    KILLED {
      @Override
      public JobInstanceState nextTransition(Event event) throws InvalidStateTransitionException {
        switch (event) {
        case ON_KILL:
          return this;
        case ON_RERUN:
          return JobInstanceState.LAUNCHED;
        default:
          throw new InvalidStateTransitionException(
              "Event: " + event.name() + " is not a valid event for state: " + this.name());
        }
      }
    }
  }

  /**
   * All events(actions) which can happen on an instance of <code>SchedulerJob</code>.
   */
  public enum Event {
    ON_CREATION, // an instance is first considered by the scheduler.
    ON_TIME_OUT,
    ON_CONDITIONS_MET,
    ON_RUN,
    ON_SUCCESS,
    ON_FAILURE,
    ON_RERUN,
    ON_KILL
  }
}
