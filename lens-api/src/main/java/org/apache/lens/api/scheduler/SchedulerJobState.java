package org.apache.lens.api.scheduler;

import org.apache.lens.api.error.InvalidStateTransitionException;

public enum SchedulerJobState implements StateTransitioner<SchedulerJobState, SchedulerJobEvent> {
  // repeating same operation will return the same state to ensure idempotent behavior.
  NEW {
    @Override
    public SchedulerJobState nextTransition(SchedulerJobEvent event) throws InvalidStateTransitionException {
      switch (event) {
      case ON_SUBMIT:
        return this;
      case ON_SCHEDULE:
        return SchedulerJobState.SCHEDULED;
      case ON_EXPIRE:
        return SchedulerJobState.EXPIRED;
      case ON_DELETE:
        return SchedulerJobState.DELETED;
      default:
        throw new InvalidStateTransitionException(
            "SchedulerJobEvent: " + event.name() + " is not a valid event for state: " + this.name());
      }
    }
  },

  SCHEDULED {
    @Override
    public SchedulerJobState nextTransition(SchedulerJobEvent event) throws InvalidStateTransitionException {
      switch (event) {
      case ON_SCHEDULE:
        return this;
      case ON_SUSPEND:
        return SchedulerJobState.SUSPENDED;
      case ON_EXPIRE:
        return SchedulerJobState.EXPIRED;
      case ON_DELETE:
        return SchedulerJobState.DELETED;
      default:
        throw new InvalidStateTransitionException(
            "SchedulerJobEvent: " + event.name() + " is not a valid event for state: " + this.name());
      }
    }
  },

  SUSPENDED {
    @Override
    public SchedulerJobState nextTransition(SchedulerJobEvent event) throws InvalidStateTransitionException {
      switch (event) {
      case ON_SUSPEND:
        return this;
      case ON_RESUME:
        return SchedulerJobState.SCHEDULED;
      case ON_EXPIRE:
        return SchedulerJobState.EXPIRED;
      case ON_DELETE:
        return SchedulerJobState.DELETED;
      default:
        throw new InvalidStateTransitionException(
            "SchedulerJobEvent: " + event.name() + " is not a valid event for state: " + this.name());
      }
    }
  },

  EXPIRED {
    @Override
    public SchedulerJobState nextTransition(SchedulerJobEvent event) throws InvalidStateTransitionException {
      switch (event) {
      case ON_EXPIRE:
        return this;
      case ON_DELETE:
        return SchedulerJobState.DELETED;
      default:
        throw new InvalidStateTransitionException(
            "SchedulerJobEvent: " + event.name() + " is not a valid event for state: " + this.name());
      }
    }
  },

  DELETED {
    @Override
    public SchedulerJobState nextTransition(SchedulerJobEvent event) throws InvalidStateTransitionException {
      switch (event) {
      case ON_DELETE:
        return this;
      default:
        throw new InvalidStateTransitionException(
            "SchedulerJobEvent: " + event.name() + " is not a valid event for state: " + this.name());
      }
    }
  }
}
