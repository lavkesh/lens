package org.apache.lens.cube.parse;

import java.util.Collection;
import java.util.List;

import org.apache.lens.cube.metadata.TimeRange;

/**
 * Created by puneet.gupta on 11/14/16.
 */
public class UnionCandidate implements Candidate {

  private Candidate unionCandidate1;
  private Candidate unionCandidate2;


  @Override
  public String toHQL() {
    return null;
  }

  @Override
  public QueryAST getQueryAst() {
    return null;
  }

  @Override
  public Collection<String> getFactColumns() {
    return null;
  }

  @Override
  public boolean isValidForTimeRange(TimeRange timeRange) {
    return false;
  }
}