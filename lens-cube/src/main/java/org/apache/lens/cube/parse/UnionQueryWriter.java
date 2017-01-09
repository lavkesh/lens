/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.lens.cube.parse;

import org.antlr.runtime.CommonToken;
import org.apache.hadoop.hive.ql.lib.Node;
import org.apache.hadoop.hive.ql.parse.ASTNode;
import org.apache.hadoop.hive.ql.parse.HiveParser;
import org.apache.hadoop.util.StringUtils;
import org.apache.lens.cube.metadata.MetastoreUtil;
import org.apache.lens.cube.metadata.Storage;
import org.apache.lens.server.api.error.LensException;
import org.apache.velocity.runtime.parser.node.ASTNENode;

import java.util.*;

import static org.apache.hadoop.hive.ql.parse.HiveParser.*;
import static org.apache.lens.cube.parse.HQLParser.*;

/**
 * This  is a helper that is used for creating QueryAst for UnionCandidate
 */
public class UnionQueryWriter {

  private QueryAST queryAst;
  private Map<HQLParser.HashableASTNode, ASTNode> innerToOuterASTs = new HashMap<>();
  private AliasDecider aliasDecider = new DefaultAliasDecider();
  private Candidate cand;
  private CubeQueryContext cubeql;
  Set<StorageCandidate> storageCandidates;
  Map<String, String> innerToOuterAliasMap = new HashMap<>();

  public UnionQueryWriter(Candidate cand, CubeQueryContext cubeql) {
    this.cand = cand;
    this.cubeql = cubeql;
    storageCandidates = CandidateUtil.getStorageCandidates(cand);
  }
  public String toHQL() throws LensException{
    // Set the default value for the non queriable measures. If a measure is not
    // answerable from a StorageCandidate set it as 0.0
    for (StorageCandidate sc : storageCandidates) {
      updateSelectASTWithDefault(sc);
    }
    // Initialize the Union QueryAST from
    queryAst = DefaultQueryAST.fromCandidateStorage(storageCandidates.iterator().next(),
        storageCandidates.iterator().next().getQueryAst());
    // Get not default outer select expr asts
    processSelectAndHavingAST();
    //updateInnerSelectAST(innerSelectAST);
    processGroupByAST();
    //processHavingAST();
    processOrderByAST();
    updateAsts();
    CandidateUtil.updateFinalAlias(queryAst.getSelectAST(), cubeql);
    return CandidateUtil.createHQLQuery(queryAst.getSelectString(), getFromString(), null,
        queryAst.getGroupByString(), queryAst.getOrderByString(),
        queryAst.getHavingString() ,queryAst.getLimitValue());
  }

  private void updateAsts() {
    for (StorageCandidate sc : storageCandidates) {
      if (sc.getQueryAst().getHavingAST() != null) {
        sc.getQueryAst().setHavingAST(null);
      }
      if (sc.getQueryAst().getOrderByAST() != null) {
        sc.getQueryAst().setOrderByAST(null);
      }
      if (sc.getQueryAst().getLimitValue() != null) {
        sc.getQueryAst().setLimitValue(null);
      }
    }
    // update union candidate alias
    updateUnionCandidateAlias(queryAst.getSelectAST());
    updateUnionCandidateAlias(queryAst.getGroupByAST());
    updateUnionCandidateAlias(queryAst.getOrderByAST());
    updateUnionCandidateAlias(queryAst.getHavingAST());
  }

  private void updateUnionCandidateAlias(ASTNode node) {
    if (node == null) {
      return;
    }
    if (node.getToken().getType() == DOT) {
      ASTNode table = HQLParser.findNodeByPath(node, TOK_TABLE_OR_COL, Identifier);
      if (table.getText().equals(cubeql.getCube().toString())) {
        table.getToken().setText(cand.getAlias());
      }
    }
    for (int i = 0; i < node.getChildCount(); i++) {
      ASTNode child = (ASTNode) node.getChild(i);
      updateUnionCandidateAlias(child);
    }
  }


  private void processGroupByAST() throws LensException{
    if (queryAst.getGroupByAST() != null) {
      queryAst.setGroupByAST(processExpression(queryAst.getGroupByAST(), null, null, null));
    }
  }

  private ASTNode processHavingAST(ASTNode innerAst, AliasDecider aliasDecider, StorageCandidate sc)
      throws LensException {
    if (queryAst.getHavingAST() != null) {
      return processExpression(queryAst.getHavingAST(), innerAst, aliasDecider, sc);
    }
    return null;
  }

  private void processOrderByAST() throws LensException{
    if (queryAst.getOrderByAST() != null) {
      queryAst.setOrderByAST(processOrderbyExpression(queryAst.getOrderByAST()));
      //queryAst.setOrderByAST(null);
    }
  }

  private ASTNode processOrderbyExpression(ASTNode astNode) throws LensException{
    if (astNode == null) {
      return null;
    }
    ASTNode outerExpression = new ASTNode(astNode);
    // sample orderby AST looks the following :
    /*
    TOK_ORDERBY
   TOK_TABSORTCOLNAMEDESC
      TOK_NULLS_LAST
         .
            TOK_TABLE_OR_COL
               testcube
            cityid
   TOK_TABSORTCOLNAMEASC
      TOK_NULLS_FIRST
         .
            TOK_TABLE_OR_COL
               testcube
            stateid
   TOK_TABSORTCOLNAMEASC
      TOK_NULLS_FIRST
         .
            TOK_TABLE_OR_COL
               testcube
            zipcode
     */
    for (Node node : astNode.getChildren()) {
      ASTNode child = (ASTNode)node;
      ASTNode outerOrderby = new ASTNode(child);
      ASTNode tokNullsChild = (ASTNode) child.getChild(0);
      ASTNode outerTokNullsChild = new ASTNode(tokNullsChild);
      outerTokNullsChild.addChild(getOuterAST((ASTNode)tokNullsChild.getChild(0), null, aliasDecider, null));
      outerOrderby.addChild(outerTokNullsChild);
      outerExpression.addChild(outerOrderby);
    }
    return outerExpression;
  }

  /*
  private void updateInnerSelectAST(ASTNode innerSelectAST) throws LensException{
    for (StorageCandidate sc : storageCandidates) {
      ASTNode originalSelect = sc.getQueryAst().getSelectAST();
      ASTNode selectWithUpdatedAlias = MetastoreUtil.copyAST(innerSelectAST);
      for (int i = 0 ; i < originalSelect.getChildCount(); i++) {
        if (originalSelect.getChild(i).getChild(0).getText().equals("0.0")) {
          String selectAlias = originalSelect.getChild(i).getChild(1).getText();
          updateNodeForAlias(selectWithUpdatedAlias, innerToOuterAliasMap.get(selectAlias));
        }
      }
      sc.getQueryAst().setSelectAST(selectWithUpdatedAlias);
    }
  }
*/
  private ASTNode getDefaultNode(ASTNode aliasNode) throws LensException {
    ASTNode defaultNode = getSelectExprAST();
    defaultNode.addChild(HQLParser.parseExpr(Double.toString(0.0)));
    defaultNode.addChild(aliasNode);
    return defaultNode;
  }
  /*
  private  void updateNodeForAlias(ASTNode select, String replacedAlias) throws LensException {
    for (int i=0; i < select.getChildCount(); i++) {
      ASTNode child = (ASTNode)  select.getChild(i);
      if (child.getChildCount() > 1) {
        String alias = child.getChild(1).getText();
        if (replacedAlias.equals(alias)) {
          child.deleteChild(0);
          ASTNode aliasNode = new ASTNode(new CommonToken(Identifier, alias));
          getDefaultNode(aliasNode);
          child.setChild(0, getDefaultNode(aliasNode));
        }
      }
    }
  }
*/
  private ASTNode getSelectExprAST() {
    return new ASTNode(new CommonToken(HiveParser.TOK_SELEXPR, "TOK_SELEXPR"));
  }

  private void updateSelectASTWithDefault(StorageCandidate sc) throws LensException {
    ASTNode innerSelect = new ASTNode(new CommonToken(TOK_SELECT, "TOK_SELECT"));
    for (int i = 0; i < cubeql.getSelectPhrases().size(); i++) {
      SelectPhraseContext phrase = cubeql.getSelectPhrases().get(i);
      ASTNode aliasNode = new ASTNode(new CommonToken(Identifier, phrase.getSelectAlias()));
      // Check whether phrase is dimensions or answerable measure in StorageCandidate
      if (!phrase.hasMeasures(cubeql)
          || sc.getAnswerableMeasureIndices().contains(phrase.getPosition())) {
        ASTNode node = getSelectExprAST();
        //node.addChild(phrase.getExprAST().getChild(0));
        node.addChild(getInnerSelectExprForAlias(sc, aliasNode.getText()));
        node.addChild(aliasNode);
        innerSelect.addChild(node);
      } else {
        innerSelect.addChild(getDefaultNode(aliasNode));
      }
    }
    sc.getQueryAst().setSelectAST(innerSelect);
  }


  private ASTNode getInnerSelectExprForAlias(StorageCandidate sc, String alias) {
    ASTNode expr = new ASTNode();
    ASTNode select = sc.getQueryAst().getSelectAST();
    for (int i = 0; i < select.getChildCount(); i++) {
      String innerAlias = select.getChild(i).getChild(1).getText();
      if (innerAlias.equals(alias)) {
        expr = (ASTNode) select.getChild(i).getChild(0);
        break;
      }
    }
    return expr;
  }

  private StorageCandidate getFirstChildCandidate() {
    return storageCandidates.iterator().next();
  }

  private ASTNode geOuterProjectestSelectExpr(int index) {
    ASTNode expr = new ASTNode();
      for (StorageCandidate sc : storageCandidates) {
        ASTNode select = sc.getQueryAst().getSelectAST();
        if (!select.getChild(index).getChild(0).getText().equals("0.0")) {
          expr =  (ASTNode) select.getChild(index);
          break;
      }
    }
    return expr;
  }

  private LinkedHashSet<ASTNode> getOuterSelectExprs() {
    LinkedHashSet<ASTNode> selectExprs = new LinkedHashSet<>();
      ASTNode select = getFirstChildCandidate().getQueryAst().getSelectAST();
      for (int i = 0; i < select.getChildCount(); i++) {
        selectExprs.add(geOuterProjectestSelectExpr(i));
      }
    return selectExprs;
  }

  private void processSelectAndHavingAST() throws LensException {
    ASTNode outerSelectAst = new ASTNode(queryAst.getSelectAST());
    ASTNode outerHavingAst = new ASTNode(new CommonToken(TOK_HAVING, "TOK_HAVING"));
    for (StorageCandidate sc : storageCandidates){
      ASTNode innerSelectAST = new ASTNode(new CommonToken(TOK_SELECT, "TOK_SELECT"));
      innerToOuterASTs.clear();
      AliasDecider aliasDecider = new DefaultAliasDecider();
      ASTNode originalSelectAST = MetastoreUtil.copyAST(sc.getQueryAst().getSelectAST());
      queryAst.setSelectAST(new ASTNode(originalSelectAST.getToken()));
      processSelectExpression(sc, outerSelectAst, innerSelectAST, aliasDecider);
      outerHavingAst = processHavingAST(innerSelectAST, aliasDecider, sc);
      //this.aliasDecider = aliasDecider;
    }
    queryAst.setSelectAST(outerSelectAst);
    queryAst.setHavingAST(outerHavingAst);
  }

  private void processSelectExpression(StorageCandidate sc, ASTNode outerSelectAst,
                                       ASTNode innerSelectAST, AliasDecider aliasDecider) throws LensException {
    ASTNode selectAST = sc.getQueryAst().getSelectAST();
    if (selectAST == null) {
      return;
    }
    //ASTNode outerExpression = new ASTNode(queryAst.getSelectAST());
    // iterate over all children of the ast and get outer ast corresponding to it.
    for (int i = 0; i < selectAST.getChildCount(); i++ ) {
    //for (Node node : selectAST.getChildren()) {
      ASTNode child = (ASTNode) selectAST.getChild(i);
      ASTNode outerSelect = new ASTNode(child);
      ASTNode selectExprAST = (ASTNode)child.getChild(0);
      ASTNode outerAST = getOuterAST(selectExprAST, innerSelectAST, aliasDecider, sc);
      outerSelect.addChild(outerAST);
      // has an alias? add it
      if (child.getChildCount() > 1) {
        outerSelect.addChild(child.getChild(1));
      }
      //outerSelectAst.addChild(outerSelect);
      if (outerSelectAst.getChildCount() <= selectAST.getChildCount()) {
        if (outerSelectAst.getChild(i) == null ) {
          outerSelectAst.addChild(outerSelect);
        } else if (HQLParser.getString((ASTNode) outerSelectAst.getChild(i).getChild(0)).equals("0.0")) {
          //outerSelectAst.deleteChild(i);
          outerSelectAst.replaceChildren(i, i, outerSelect);
          //outerSelectAst.setChild(i, outerSelect);
        }
      }
    }
    sc.getQueryAst().setSelectAST(innerSelectAST);
  }

  /*

Perform a DFS on the provided AST, and Create an AST of similar structure with changes specific to the
inner query - outer query dynamics. The resultant AST is supposed to be used in outer query.

Base cases:
 1. ast is null => null
 2. ast is aggregate_function(table.column) => add aggregate_function(table.column) to inner select expressions,
          generate alias, return aggregate_function(cube.alias). Memoize the mapping
          aggregate_function(table.column) => aggregate_function(cube.alias)
          Assumption is aggregate_function is transitive i.e. f(a,b,c,d) = f(f(a,b), f(c,d)). SUM, MAX, MIN etc
          are transitive, while AVG, COUNT etc are not. For non-transitive aggregate functions, the re-written
          query will be incorrect.
 3. ast has aggregates - iterate over children and add the non aggregate nodes as is and recursively get outer ast
 for aggregate.
 4. If no aggregates, simply select its alias in outer ast.
 5. If given ast is memorized as mentioned in the above cases, return the mapping.
 */
  private ASTNode getOuterAST(ASTNode astNode, ASTNode innerSelectAST,
                              AliasDecider aliasDecider, StorageCandidate sc) throws LensException {
    if (astNode == null) {
      return null;
    }
//    if (innerToOuterASTs.containsKey(new HQLParser.HashableASTNode(astNode))
 //       && !astNode.getText().equals("0.0")) {
    //if (!astNode.getText().equals("0.0")) {
    //    return innerToOuterASTs.get(new HQLParser.HashableASTNode(astNode));
    //}
    if (isAggregateAST(astNode)) {
      return processAggregate(astNode, innerSelectAST, aliasDecider);
    } else if (hasAggregate(astNode)) {
      ASTNode outerAST = new ASTNode(astNode);
      for (Node child : astNode.getChildren()) {
        ASTNode childAST = (ASTNode) child;
        if (hasAggregate(childAST) && isExprAnswerableForStorage(childAST, sc)) {
          outerAST.addChild(getOuterAST(childAST, innerSelectAST, aliasDecider, sc));
        } else if (hasAggregate(childAST) && !isExprAnswerableForStorage(childAST, sc)) {
          childAST.replaceChildren(1, 1, getDefaultNode(null));
          //ASTNode aggNode = new ASTNode(new CommonToken(HiveParser.TOK_FUNCTION, "TOK_FUNCTION"));
          //aggNode.addChild((ASTNode) childAST.getChild(0));
          //aggNode.addChild(getDefaultNode(null));
          outerAST.addChild(getOuterAST(childAST, innerSelectAST, aliasDecider, sc));
        } else {
          outerAST.addChild(childAST);
        }
      }
      return outerAST;
    } else {
      ASTNode innerSelectASTWithoutAlias = MetastoreUtil.copyAST(astNode);
      ASTNode innerSelectExprAST = new ASTNode(new CommonToken(HiveParser.TOK_SELEXPR, "TOK_SELEXPR"));
      innerSelectExprAST.addChild(innerSelectASTWithoutAlias);
      String alias = aliasDecider.decideAlias(astNode);
      ASTNode aliasNode = new ASTNode(new CommonToken(Identifier, alias));
      innerSelectExprAST.addChild(aliasNode);
      innerSelectAST.addChild(innerSelectExprAST);
      if (astNode.getText().equals("0.0")) {
        ASTNode outerAST = new ASTNode(new CommonToken(HiveParser.TOK_SELEXPR, "TOK_SELEXPR"));
        outerAST.addChild(astNode);
        innerToOuterASTs.put(new HQLParser.HashableASTNode(innerSelectASTWithoutAlias), outerAST);
        return outerAST;
      } else {
        ASTNode outerAST = getDotAST(cubeql.getCube().getName(), alias);
        innerToOuterASTs.put(new HQLParser.HashableASTNode(innerSelectASTWithoutAlias), outerAST);
        return outerAST;
      }
    }
  }

  private  QueriedPhraseContext getQueriedPhraseForMeasure(ASTNode node) {
    for (QueriedPhraseContext qpc : cubeql.getQueriedPhrases()) {
      if (HQLParser.getString(node).equals(HQLParser.getString(qpc.getExprAST()))) {
        return qpc;
      }
    }
    return null;
  }

  private boolean isExprAnswerableForStorage(ASTNode node, StorageCandidate sc ) throws LensException{
    boolean answerable = false;
    QueriedPhraseContext qpc = getQueriedPhraseForMeasure(node);
    if (qpc.isEvaluable(cubeql, sc)) {
      answerable = true;
    }
    return answerable;
  }


  private ASTNode processAggregate(ASTNode astNode, ASTNode innerSelectAST, AliasDecider aliasDecider) {
    ASTNode innerSelectASTWithoutAlias = MetastoreUtil.copyAST(astNode);
    ASTNode innerSelectExprAST = new ASTNode(new CommonToken(HiveParser.TOK_SELEXPR, "TOK_SELEXPR"));
    innerSelectExprAST.addChild(innerSelectASTWithoutAlias);
    String alias = aliasDecider.decideAlias(astNode);
    ASTNode aliasNode = new ASTNode(new CommonToken(Identifier, alias));
    innerSelectExprAST.addChild(aliasNode);
    innerSelectAST.addChild(innerSelectExprAST);
    ASTNode dotAST = getDotAST(cubeql.getCube().getName(), alias);
    ASTNode outerAST = new ASTNode(new CommonToken(TOK_FUNCTION, "TOK_FUNCTION"));
    //TODO: take care or non-transitive aggregate functions
    outerAST.addChild(new ASTNode(new CommonToken(Identifier, astNode.getChild(0).getText())));
    outerAST.addChild(dotAST);
    innerToOuterASTs.put(new HQLParser.HashableASTNode(innerSelectASTWithoutAlias), outerAST);
    return outerAST;
  }

  private ASTNode processExpression(ASTNode astNode, ASTNode innerAst,
                                    AliasDecider aliasDecider, StorageCandidate sc) throws LensException{
    if (astNode == null) {
      return null;
    }
    ASTNode outerExpression = new ASTNode(astNode);
    // iterate over all children of the ast and get outer ast corresponding to it.
    for (Node child : astNode.getChildren()) {
      if (innerToOuterASTs.containsKey(new HQLParser.HashableASTNode((ASTNode) child))) {
        outerExpression.addChild(innerToOuterASTs.get(new HQLParser.HashableASTNode((ASTNode) child)));
      } else {
        outerExpression.addChild(getOuterAST((ASTNode) child, innerAst, aliasDecider, sc));
      }
    }
    return outerExpression;
  }

  private String getFromString() throws LensException {
    StringBuilder from = new StringBuilder();
    List<String> hqlQueries = new ArrayList<>();
    for (StorageCandidate sc : storageCandidates) {
      hqlQueries.add(" ( " + sc.toHQL() + " ) ");
    }
    return  from.append(" ( ")
        .append(StringUtils.join(" UNION ALL ", hqlQueries))
        .append(" ) as " + cand.getAlias()).toString();
  }
}
