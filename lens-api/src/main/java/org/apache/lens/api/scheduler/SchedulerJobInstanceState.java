package org.apache.lens.api.scheduler;

import org.apache.lens.api.error.InvalidStateTransitionException;

public enum SchedulerJobInstanceState implements StateTransitioner<SchedulerJobInstanceState, SchedulerJobInstanceEvent> {
  // repeating same operation will return the same state to ensure idempotent behavior.
  WAITING {
    @Override
    public SchedulerJobInstanceState nextTransition(SchedulerJobInstanceEvent event) throws InvalidStateTransitionException {
      switch (event) {
      case ON_CREATION:
        return this;
      case ON_CONDITIONS_MET:
        return SchedulerJobInstanceState.LAUNCHED;
      case ON_TIME_OUT:
        return SchedulerJobInstanceState.TIMED_OUT;
      case ON_RUN:
        return SchedulerJobInstanceState.RUNNING;
      case ON_SUCCESS:
        return SchedulerJobInstanceState.SUCCEEDED;
      case ON_FAILURE:
        return SchedulerJobInstanceState.FAILED;
      case ON_KILL:
        return SchedulerJobInstanceState.KILLED;
      default:
        throw new InvalidStateTransitionException(
            "SchedulerJobInstanceEvent: " + event.name() + " is not a valid event for state: " + this.name());
      }
    }
  },

  LAUNCHED {
    @Override
    public SchedulerJobInstanceState nextTransition(SchedulerJobInstanceEvent event) throws InvalidStateTransitionException {
      switch (event) {
      case ON_CONDITIONS_MET:
        return this;
      case ON_RUN:
        return SchedulerJobInstanceState.RUNNING;
      case ON_SUCCESS:
        return SchedulerJobInstanceState.SUCCEEDED;
      case ON_FAILURE:
        return SchedulerJobInstanceState.FAILED;
      case ON_KILL:
        return SchedulerJobInstanceState.KILLED;
      default:
        throw new InvalidStateTransitionException(
            "SchedulerJobInstanceEvent: " + event.name() + " is not a valid event for state: " + this.name());
      }
    }
  },

  RUNNING {
    @Override
    public SchedulerJobInstanceState nextTransition(SchedulerJobInstanceEvent event) throws InvalidStateTransitionException {
      switch (event) {
      case ON_RUN:
        return this;
      case ON_SUCCESS:
        return SchedulerJobInstanceState.SUCCEEDED;
      case ON_FAILURE:
        return SchedulerJobInstanceState.FAILED;
      case ON_KILL:
        return SchedulerJobInstanceState.KILLED;
      default:
        throw new InvalidStateTransitionException(
            "SchedulerJobInstanceEvent: " + event.name() + " is not a valid event for state: " + this.name());
      }
    }
  },

  FAILED {
    @Override
    public SchedulerJobInstanceState nextTransition(SchedulerJobInstanceEvent event) throws InvalidStateTransitionException {
      switch (event) {
      case ON_FAILURE:
        return this;
      case ON_RERUN:
        return SchedulerJobInstanceState.LAUNCHED;
      default:
        throw new InvalidStateTransitionException(
            "SchedulerJobInstanceEvent: " + event.name() + " is not a valid event for state: " + this.name());
      }
    }
  },

  SUCCEEDED {
    @Override
    public SchedulerJobInstanceState nextTransition(SchedulerJobInstanceEvent event) throws InvalidStateTransitionException {
      switch (event) {
      case ON_SUCCESS:
        return this;
      case ON_RERUN:
        return SchedulerJobInstanceState.LAUNCHED;
      default:
        throw new InvalidStateTransitionException(
            "SchedulerJobInstanceEvent: " + event.name() + " is not a valid event for state: " + this.name());
      }
    }
  },

  TIMED_OUT {
    @Override
    public SchedulerJobInstanceState nextTransition(SchedulerJobInstanceEvent event) throws InvalidStateTransitionException {
      switch (event) {
      case ON_TIME_OUT:
        return this;
      case ON_RERUN:
        return SchedulerJobInstanceState.WAITING;
      default:
        throw new InvalidStateTransitionException(
            "SchedulerJobInstanceEvent: " + event.name() + " is not a valid event for state: " + this.name());
      }
    }
  },

  KILLED {
    @Override
    public SchedulerJobInstanceState nextTransition(SchedulerJobInstanceEvent event) throws InvalidStateTransitionException {
      switch (event) {
      case ON_KILL:
        return this;
      case ON_RERUN:
        return SchedulerJobInstanceState.LAUNCHED;
      default:
        throw new InvalidStateTransitionException(
            "SchedulerJobInstanceEvent: " + event.name() + " is not a valid event for state: " + this.name());
      }
    }
  }
}
